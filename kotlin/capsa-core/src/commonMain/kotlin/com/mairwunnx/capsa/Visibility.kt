package com.mairwunnx.capsa

import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Decides where the runtime learns whether the screen is visible. A hidden screen pauses the works with `whileVisible`.
 *
 * [CapsaConfig.visibility] holds the choice. [CapsaRuntime.onVisibilityChanged] works in both modes as the host’s veto.
 */
public sealed interface CapsaVisibility {

    /**
     * The screen is visible while someone collects a slice from [CapsaBuilder.install], a projection from [mapState]
     * or [combineState], or a flow from [CapsaRuntime.watched], such as the effects of a view model.
     *
     * Nobody has to call anything: a screen that collects its state with `collectAsStateWithLifecycle()` stops collecting
     * in the background and in the back stack, and the runtime sees it. Until the first collector the screen is hidden.
     * When the last collector leaves, the runtime waits for [grace] before it hides the screen, so a rotation,
     * where the old screen leaves and the new one comes a moment later, doesn’t restart the works.
     *
     * A collector that never stops, such as `stateIn(viewModelScope, SharingStarted.Eagerly)` over a slice, keeps the screen
     * visible for good. Inside a view model read `.value` or build projections with [mapState] and [combineState]:
     * they read the slice past the counter.
     *
     * @property grace how long the screen stays visible after the last collector leaves
     */
    public class Watched(public val grace: Duration = DEFAULT_GRACE) : CapsaVisibility {
        init {
            require(!grace.isNegative()) { "grace must not be negative, got $grace" }
        }

        override fun toString(): String = "Watched(grace=$grace)"

        private companion object {
            // Longer than the 700 ms that ProcessLifecycleOwner on Android waits out a rotation with.
            val DEFAULT_GRACE = 1.seconds
        }
    }

    /**
     * Only the host decides through [CapsaRuntime.onVisibilityChanged]. The screen starts visible.
     *
     * Fits a test harness and a host that knows the visibility better than the collectors do.
     */
    public data object Host : CapsaVisibility
}

/** Someone who counts the collectors of a screen. */
internal interface Watch {
    fun enter()
    fun leave()
}

/**
 * Counts the collectors of one runtime. A collector may come before [CapsaBuilder.build]: the count waits
 * and goes to the runtime when it starts. After that every change goes to [sink], the queue of the runtime.
 */
internal class Watchers : Watch {
    private val lock = SynchronizedObject()
    private var pending = 0
    private var sink: ((Int) -> Unit)? = null

    override fun enter() = change(+1)

    override fun leave() = change(-1)

    private fun change(delta: Int) {
        synchronized(lock) {
            val current = sink
            if (current == null) pending += delta else current(delta)
        }
    }

    /** Hands the collectors counted so far to `sink` and sends every later change there. */
    fun attach(sink: (Int) -> Unit) {
        synchronized(lock) {
            if (pending != 0) sink(pending)
            pending = 0
            this.sink = sink
        }
    }
}

/** Counts a collector in several runtimes at once, for a projection over the slices of different screens. */
private class Watches(private val all: List<Watch>) : Watch {
    override fun enter() = all.forEach { it.enter() }

    override fun leave() = all.forEach { it.leave() }
}

/**
 * A slice or a projection that counts its collectors, like `RefCountStateFlow` in Orbit MVI.
 *
 * [value] and [replayCache] read [source] directly and count nobody. Projections collect [source] past the counter.
 * The class implements [StateFlow], which kotlinx-coroutines doesn’t promise to keep stable for inheritance:
 * a new member in a future version would need a new release of Capsa.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class WatchedStateFlow<T>(val source: StateFlow<T>, val watch: Watch) : StateFlow<T> by source {
    override suspend fun collect(collector: FlowCollector<T>): Nothing {
        watch.enter()
        try {
            source.collect(collector)
        } finally {
            watch.leave()
        }
    }
}

/** A flow that counts its collectors, for the effects and other flows a host hands to the screen. */
internal class WatchedFlow<T>(private val source: Flow<T>, private val watch: Watch) : Flow<T> {
    override suspend fun collect(collector: FlowCollector<T>) {
        watch.enter()
        try {
            source.collect(collector)
        } finally {
            watch.leave()
        }
    }
}

/** The flow without the counter, so a projection doesn’t count itself as a collector. */
internal fun <T> StateFlow<T>.unwatched(): StateFlow<T> = (this as? WatchedStateFlow<T>)?.source ?: this

/** The counters behind `flows`, or `null` if none of them counts collectors. */
internal fun watchOf(vararg flows: StateFlow<*>): Watch? {
    val watches = flows.mapNotNull { (it as? WatchedStateFlow<*>)?.watch }.distinct()
    return when (watches.size) {
        0 -> null
        1 -> watches.single()
        else -> Watches(watches)
    }
}

/** Wraps the flow with `watch`, or returns it as is when there is nothing to count. */
internal fun <T> StateFlow<T>.watchedBy(watch: Watch?): StateFlow<T> = if (watch == null) this else WatchedStateFlow(this, watch)
