package com.mairwunnx.capsa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Holds the state of one capability and hides its concrete type `S` from the runtime.
 *
 * Isolating the type keeps casts and structures like `Map<Key, Any>` out of the rest of the pipeline.
 */
internal class CapabilityHolder<S : Any>(
    private val capability: Capability<S>,
    restored: S?,
) {
    private val _state = MutableStateFlow(restored ?: capability.initial())
    val state: StateFlow<S> = _state.asStateFlow()

    val name: String get() = capability.name

    fun accepts(fact: Fact): Boolean = capability.handles.any { it.isInstance(fact) }

    /** Applies `fact` and writes the new state without locks. Call it only from the fact-processing coroutine. */
    fun decide(fact: Fact): Decision<S> =
        capability.decide(_state.value, fact).also { _state.value = it.state }
}

/** Gathers the capabilities of a screen and creates a shared [CapsaRuntime] for them. */
public class CapsaBuilder {

    private val holders = mutableListOf<CapabilityHolder<*>>()
    private var built = false

    /**
     * Adds `capability` to the future runtime and returns the flow of its state.
     *
     * A restored state replaces [Capability.initial]. The platform layer picks the source,
     * for example `SavedStateHandle` or a database.
     *
     * @param capability the capability to install
     * @param restored the state after process restoration, or `null`
     * @return a read-only flow of the state, for building projections
     * @throws IllegalArgumentException if the builder already holds a capability with this name
     * @throws IllegalStateException if the runtime is already built: such a capability would never receive facts
     */
    public fun <S : Any> install(capability: Capability<S>, restored: S? = null): StateFlow<S> {
        check(!built) { "install(${capability.name}) after build(): the runtime is already built and won't see the new capability" }
        require(holders.none { it.name == capability.name }) {
            "Capability '${capability.name}' is already installed"
        }
        val holder = CapabilityHolder(capability, restored)
        holders += holder
        return holder.state
    }

    /**
     * Creates the runtime, queues [Started], and starts processing facts.
     *
     * The runtime lives while `scope` is active. The end of the scope closes the queues of facts and effects.
     *
     * @param scope the coroutine scope of the runtime, for example `viewModelScope`
     * @param config the settings of the runtime
     * @return the running [CapsaRuntime]
     * @throws IllegalStateException on a second call
     */
    public fun build(scope: CoroutineScope, config: CapsaConfig = CapsaConfig()): CapsaRuntime {
        check(!built) { "build() is already called: a second runtime over the same capabilities would process facts in parallel" }
        built = true
        return CapsaRuntime(holders.toList(), scope, config)
    }
}

/**
 * Passes facts through the capabilities, applies the decisions, and starts the works.
 *
 * One coroutine processes the facts one by one, so the order stays deterministic
 * without locks. Capabilities share data only through facts.
 */
public class CapsaRuntime internal constructor(
    private val holders: List<CapabilityHolder<*>>,
    private val scope: CoroutineScope,
    private val config: CapsaConfig,
) {
    private val inbox = Channel<Inbound>(Channel.UNLIMITED)

    /**
     * Buffers effects while the host isn’t subscribed, for example during a screen rotation or in the background.
     *
     * Unlike a `SharedFlow` without a buffer, the channel loses no events, but it supports only one consumer.
     * Only the host subscribes.
     */
    private val outbox = Channel<Effect>(Channel.UNLIMITED)

    /** The effects for the host. Only one collector may read them. */
    public val effects: Flow<Effect> = outbox.receiveAsFlow()

    /**
     * Holds the runs of works by the pair “owner + key” until their signals are processed.
     *
     * Accessed only from the fact-processing coroutine. A run leaves the map when the loop takes its [Inbound.WorkDone].
     */
    private val jobs = mutableMapOf<Pair<String, WorkKey>, MutableList<Launch>>()

    /** Works with `whileVisible` that wait for the screen to come back. Accessed only from the fact-processing coroutine. */
    private val paused = mutableListOf<Pair<String, Work>>()

    private var visible = true

    private val history = CapsaHistory()

    init {
        inbox.trySend(Inbound.External(Started))
        // One consumer: the order is deterministic, and no locks are needed.
        scope.launch {
            for (inbound in inbox) {
                try {
                    receive(inbound)
                } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                    e.rethrowIfFatal(scope)
                    // A failure on one fact doesn’t stop the loop, otherwise the screen would stop responding.
                    scope.deliverUncaught(e)
                }
            }
        }
        scope.coroutineContext[Job]?.invokeOnCompletion {
            inbox.cancel()
            outbox.cancel()
        }
    }

    /**
     * Adds `fact` to the queue and returns at once.
     *
     * The method is thread-safe and accepts calls from any thread. After the scope of the runtime ends,
     * the fact is silently lost.
     */
    public fun append(fact: Fact) {
        inbox.trySend(Inbound.External(fact))
    }

    /**
     * Tells whether the screen is visible. A hidden screen pauses the works with `whileVisible`, a visible one restarts them.
     *
     * The host calls the method when the lifecycle changes. The method is thread-safe.
     */
    public fun onVisibilityChanged(visible: Boolean) {
        inbox.trySend(Inbound.Visibility(visible))
    }

    private fun receive(inbound: Inbound) {
        when (inbound) {
            is Inbound.External -> process(inbound.fact)
            is Inbound.FromWork -> if (inbound.launch.revoked) {
                val launch = inbound.launch
                history.add(CapsaHistory.SignalDropped(launch.owner, launch.work.key, inbound.signal))
                notify { onSignalDropped(launch.owner, launch.work.key, inbound.signal) }
            } else {
                process(inbound.signal)
            }
            is Inbound.WorkDone -> forget(inbound.launch)
            is Inbound.Visibility -> changeVisibility(inbound.visible)
        }
    }

    /** Processes `root` and the chain of signals it produces, in queue order. */
    private fun process(root: Fact) {
        val queue = ArrayDeque<Pair<Fact, Int>>()
        queue.add(root to 0)

        while (queue.isNotEmpty()) {
            val (fact, depth) = queue.removeFirst()
            handle(fact, depth, queue)
        }
    }

    /**
     * Passes `fact` to every capability that accepts it.
     *
     * Effects, cancels, and works apply at once. Signals go to the queue with a depth one greater.
     */
    private fun handle(fact: Fact, depth: Int, queue: ArrayDeque<Pair<Fact, Int>>) {
        notify { onFact(fact) }

        if (depth > config.maxSignalDepth) {
            report(CapsaViolation.SignalCycle(fact, depth))
            return
        }

        val handlers = holders.filter { it.accepts(fact) }
        history.add(CapsaHistory.FactHandled(fact, handlers))
        if (handlers.isEmpty()) {
            if (fact !is Started) report(CapsaViolation.UnhandledFact(fact))
            return
        }

        for (holder in handlers) {
            val decision = decideOrNull(holder, fact) ?: continue
            notify { onDecision(holder.name, fact, decision) }

            decision.effects.forEach { outbox.trySend(it) }
            decision.cancels.forEach { cancelWork(holder.name, it) }
            decision.works.forEach { startWork(holder.name, it) }
            decision.signals.forEach { queue.add(it to depth + 1) }
        }
    }

    private fun decideOrNull(holder: CapabilityHolder<*>, fact: Fact): Decision<*>? =
        try {
            holder.decide(fact)
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            e.rethrowIfFatal(scope)
            report(CapsaViolation.DecideFailed(holder.name, fact, e))
            null
        }

    private fun startWork(owner: String, work: Work) {
        val running = jobs[owner to work.key].orEmpty()
        val pausedSame = paused.any { it.first == owner && it.second.key == work.key }

        when (work.policy) {
            WorkPolicy.KeepExisting -> if (running.any { it.job.isActive } || pausedSame) return
            WorkPolicy.Latest -> cancelWork(owner, work.key)
            WorkPolicy.Parallel -> Unit
        }

        if (work.whileVisible && !visible) {
            paused += owner to work
            return
        }

        history.add(CapsaHistory.WorkStarted(owner, work.key))
        notify { onWorkStarted(owner, work.key) }
        val launch = Launch(owner, work)
        // LAZY: the run enters the map before the work can send a signal or finish.
        launch.job = scope.launch(config.workContext, start = CoroutineStart.LAZY) {
            try {
                work.signals(onFailure = { workFailed(owner, work.key, it) })
                    .collect { inbox.trySend(Inbound.FromWork(it, launch)) }
            } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
                e.rethrowIfFatal(this)
                report(CapsaViolation.WorkCrashed(owner, work.key, e))
            }
        }
        launch.job.invokeOnCompletion { inbox.trySend(Inbound.WorkDone(launch)) }
        jobs.getOrPut(owner to work.key) { mutableListOf() } += launch
        launch.job.start()
    }

    private fun workFailed(owner: String, key: WorkKey, error: Throwable) {
        history.add(CapsaHistory.WorkFailed(owner, key, error))
        notify { onWorkFailed(owner, key, error) }
    }

    /**
     * Passes `violation` with the history of the runtime to [CapsaConfig.onViolation].
     *
     * The default handler throws. The runtime hands the exception to the error handler of the scope
     * and goes on processing facts: the violation shows up in logs or crash reports, but the screen doesn’t freeze.
     */
    private fun report(violation: CapsaViolation) {
        try {
            config.onViolation(CapsaViolationException(violation, history.render()))
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            e.rethrowIfFatal(scope)
            scope.deliverUncaught(e)
        }
    }

    /** Calls the observer so that its failure doesn’t stop processing the fact. */
    private inline fun notify(event: CapsaObserver.() -> Unit) {
        try {
            config.observer.event()
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            e.rethrowIfFatal(scope)
            scope.deliverUncaught(e)
        }
    }

    /**
     * Revokes every run of `key`: the active ones get cancelled, and the signals already in the queue get dropped.
     *
     * Without the revocation, the answer of an old request arriving after the cancel would overwrite the state of the new one.
     */
    private fun cancelWork(owner: String, key: WorkKey) {
        paused.removeAll { it.first == owner && it.second.key == key }
        val running = jobs.remove(owner to key) ?: return
        running.forEach { it.revoked = true }
        val active = running.filter { it.job.isActive }
        if (active.isNotEmpty()) {
            history.add(CapsaHistory.WorkCancelled(owner, key))
            notify { onWorkCancelled(owner, key) }
            active.forEach { it.job.cancel() }
        }
    }

    /** Removes a finished run, so the map doesn’t grow with dynamic keys. */
    private fun forget(launch: Launch) {
        val key = launch.owner to launch.work.key
        val running = jobs[key] ?: return
        running.remove(launch)
        if (running.isEmpty()) jobs.remove(key)
    }

    private fun changeVisibility(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (visible) {
            val resumed = paused.toList()
            paused.clear()
            resumed.forEach { (owner, work) -> startWork(owner, work) }
        } else {
            jobs.values.flatten()
                .filter { it.work.whileVisible && !it.revoked }
                .forEach { launch ->
                    cancelWork(launch.owner, launch.work.key)
                    paused += launch.owner to launch.work
                }
        }
    }

    /** One run of a work. The [revoked] flag changes only on the fact-processing coroutine. */
    private class Launch(val owner: String, val work: Work) {
        lateinit var job: Job
        var revoked = false
    }

    /** Everything that enters the queue of the runtime: outside facts, signals of works, and service events. */
    private sealed interface Inbound {
        class External(val fact: Fact) : Inbound
        class FromWork(val signal: Signal, val launch: Launch) : Inbound
        class WorkDone(val launch: Launch) : Inbound
        class Visibility(val visible: Boolean) : Inbound
    }
}

/**
 * Hands `error` to the [CoroutineExceptionHandler] of the scope. Without one, the platform gets it
 * as an uncaught coroutine error: the handler of the current thread on the JVM and Android,
 * the unhandled exception hook on Kotlin/Native.
 */
internal fun CoroutineScope.deliverUncaught(error: Throwable) {
    val handler = coroutineContext[CoroutineExceptionHandler]
    if (handler != null) {
        handler.handleException(coroutineContext, error)
    } else {
        deliverUncaughtToPlatform(error)
    }
}

/**
 * Rethrows the errors that must not be swallowed: a real cancellation of `scope` and a fatal error such as running out of memory.
 *
 * A `CancellationException` while `scope` is alive is a foreign cancellation, for example from a cancelled `Deferred`.
 * The runtime treats it as an error, otherwise the fact loop would stop silently.
 * After a fatal error the process can’t go on.
 */
internal fun Throwable.rethrowIfFatal(scope: CoroutineScope) {
    if (isFatal()) throw this
    if (this is CancellationException && !scope.isActive) throw this
}
