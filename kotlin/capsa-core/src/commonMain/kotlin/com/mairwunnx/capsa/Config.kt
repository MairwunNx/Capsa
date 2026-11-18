package com.mairwunnx.capsa

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/** Describes a broken contract of Capsa. [CapsaConfig.onViolation] decides how to handle it. */
public sealed interface CapsaViolation {

    /** No capability accepted `fact`. The service fact [Started] is not a violation. */
    public data class UnhandledFact(public val fact: Fact) : CapsaViolation

    /** A chain of [Decision.signals] went deeper than [CapsaConfig.maxSignalDepth], usually because of a cycle between capabilities. */
    public data class SignalCycle(public val fact: Fact, public val depth: Int) : CapsaViolation

    /** [Capability.decide] threw. The runtime keeps the previous state and goes on. */
    public data class DecideFailed(public val capability: String, public val fact: Fact, public val error: Throwable) : CapsaViolation

    /** The `onError` handler threw, so the work couldn’t turn the error into a [Signal]. */
    public data class WorkCrashed(public val capability: String, public val key: WorkKey, public val error: Throwable) : CapsaViolation
}

/**
 * Carries a [CapsaViolation] and the last events of the runtime before it.
 *
 * The message reads well in a crash report without logs: what broke, where, and which facts led to it.
 * An exception from `decide` or `onError` becomes the [cause], so the report points to the line in the capability.
 * A violation without a cause gets the class of the fact as its first frame on the JVM and Android:
 * reports group by fact, and the R8 mapping file restores its name.
 *
 * @property violation the broken contract
 * @property history the events of the runtime, oldest first
 */
public class CapsaViolationException(
    public val violation: CapsaViolation,
    public val history: List<String> = emptyList(),
) : IllegalStateException(describe(violation, history), violation.cause) {

    init {
        violation.fact?.let { fact -> prependFrame(fact.platformClassName(), violation.frameName, FRAME_FILE) }
    }

    private companion object {
        const val FRAME_FILE = "Capsa"

        fun describe(violation: CapsaViolation, history: List<String>): String = buildString {
            append(
                when (violation) {
                    is CapsaViolation.UnhandledFact -> "UnhandledFact: nobody handles ${describeFact(violation.fact)}"
                    is CapsaViolation.SignalCycle ->
                        "SignalCycle: signal chain reached depth ${violation.depth} at ${describeFact(violation.fact)}"
                    is CapsaViolation.DecideFailed ->
                        "DecideFailed: ${violation.capability} failed on ${describeFact(violation.fact)}"
                    is CapsaViolation.WorkCrashed ->
                        "WorkCrashed: ${violation.capability} work \"${violation.key.name}\" failed in onError"
                },
            )
            if (history.isNotEmpty()) {
                append("\nCapsa history, newest last:")
                history.forEach { append("\n  ").append(it) }
            }
        }

        private val CapsaViolation.cause: Throwable?
            get() = when (this) {
                is CapsaViolation.DecideFailed -> error
                is CapsaViolation.WorkCrashed -> error
                is CapsaViolation.UnhandledFact, is CapsaViolation.SignalCycle -> null
            }

        /** The fact for the synthetic frame. A violation with a cause needs no frame: its stack leads to the capability. */
        private val CapsaViolation.fact: Fact?
            get() = when (this) {
                is CapsaViolation.UnhandledFact -> fact
                is CapsaViolation.SignalCycle -> fact
                is CapsaViolation.DecideFailed, is CapsaViolation.WorkCrashed -> null
            }

        private val CapsaViolation.frameName: String
            get() = if (this is CapsaViolation.SignalCycle) "signalCycle" else "unhandledFact"
    }
}

/**
 * Receives the events of the runtime for logs, debugging, and tests.
 *
 * The runtime calls the methods on the fact-processing coroutine, except [onWorkFailed]. Long work inside an observer
 * blocks the whole pipeline. An exception from an observer goes to the error handler of the scope and doesn’t stop the fact.
 */
public interface CapsaObserver {
    /** Reports the start of processing `fact`, even if no capability accepts it. */
    public fun onFact(fact: Fact) {}

    /** Reports that `capability` returned `decision` for `fact`. */
    public fun onDecision(capability: String, fact: Fact, decision: Decision<*>) {}

    /** Reports the start of `key` for `capability`. The [WorkPolicy.KeepExisting] policy can suppress the event. */
    public fun onWorkStarted(capability: String, key: WorkKey) {}

    /** Reports the cancellation of the active work `key` of `capability`. */
    public fun onWorkCancelled(capability: String, key: WorkKey) {}

    /**
     * Reports that the work `key` threw `error`, and `onError` turned it into a signal.
     *
     * The runtime calls the method on the work’s coroutine, so the implementation must be thread-safe.
     */
    public fun onWorkFailed(capability: String, key: WorkKey, error: Throwable) {}

    /**
     * Reports that the runtime dropped `signal` of the cancelled work `key`.
     *
     * The signal never reaches `decide`: the work was cancelled or restarted before the runtime took its answer.
     */
    public fun onSignalDropped(capability: String, key: WorkKey, signal: Signal) {}

    /** Ignores every event of the runtime. */
    public object None : CapsaObserver
}

/**
 * Configures how the runtime handles signals, errors, and async works.
 *
 * @param maxSignalDepth the maximum depth of a chain of signals from `Decision.signals`
 * @param onViolation the handler of a violation with the runtime history. By default it throws [CapsaViolationException].
 * The runtime passes the thrown exception to the `CoroutineExceptionHandler` of the scope and goes on processing facts
 * @param workContext the coroutine context for works. An empty context inherits the settings of the runtime scope.
 * The core doesn’t choose `Dispatchers.Main`: the dispatcher comes with the scope
 * @param observer an observer for logs and tests
 */
public class CapsaConfig(
    public val maxSignalDepth: Int = 8,
    public val onViolation: (CapsaViolationException) -> Unit = { throw it },
    public val workContext: CoroutineContext = EmptyCoroutineContext,
    public val observer: CapsaObserver = CapsaObserver.None,
)
