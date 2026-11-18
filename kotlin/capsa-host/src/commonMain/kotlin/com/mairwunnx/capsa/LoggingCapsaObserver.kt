package com.mairwunnx.capsa

/**
 * Writes the runtime journal to a [CapsaLogger] at [CapsaLogger.Level.Debug]:
 *
 * ```
 * Signal ResultsLoaded(items=[...])
 *   search: state SearchSlice(load=Loaded(value=[...]))
 *   timer: state TimerSlice(left=30s, running=true)
 *   timer: cancel work "tick"
 *   timer: start work "tick"
 *   likes: work "like" failed with java.net.SocketTimeoutException: timeout
 *   search: drop signal ResultsLoaded(query=cat) from cancelled work "search"
 *
 * Intent LikeClicked
 *   likes: state LikesSlice(...)
 *   likes: send effect ShowThanks
 * ```
 *
 * An event starts with `Intent` or `Signal`. Below it, indented, comes everything it caused, as `capability: action`:
 * the new state, each effect and signal, the start and the cancellation of works. A work gets into the journal
 * when the runtime really starts or cancels it. The lines of one decision go out as one record joined by line breaks,
 * so Logcat shows them together under the same tag.
 *
 * The lines follow the order of processing, so a race can be read from the log without a debugger.
 * The observer builds a line only if the logger takes it: [CapsaLogger.Default] skips the journal in release builds.
 *
 * With `checkReadableFacts`, the observer throws on a fact without a readable `toString()`. In a release build,
 * such a fact turns into `a.b@1f2e` in a crash report. By default the check runs only while [Capsa.failFast] is on.
 *
 * @param logger the logger that receives the journal
 * @param checkReadableFacts require a `data class` or a `data object` for facts
 */
public class LoggingCapsaObserver(
    private val logger: CapsaLogger,
    private val checkReadableFacts: Boolean = Capsa.failFast,
) : CapsaObserver {

    override fun onFact(fact: Fact) {
        if (checkReadableFacts) checkReadable(fact)
        log { formatFact(fact) }
    }

    override fun onDecision(capability: String, fact: Fact, decision: Decision<*>) {
        log { formatDecision(capability, decision).joinToString(separator = "\n") }
    }

    override fun onWorkStarted(capability: String, key: WorkKey) {
        log { formatWork(capability, key, started = true) }
    }

    override fun onWorkCancelled(capability: String, key: WorkKey) {
        log { formatWork(capability, key, started = false) }
    }

    override fun onSignalDropped(capability: String, key: WorkKey, signal: Signal) {
        log { formatSignalDropped(capability, key, signal) }
    }

    override fun onWorkFailed(capability: String, key: WorkKey, error: Throwable) {
        log { formatWorkFailed(capability, key, error) }
    }

    private fun log(text: () -> String) {
        logger.log(CapsaLogger.Level.Debug, null, text)
    }
}

internal fun formatFact(fact: Fact): String {
    val kind = when (fact) {
        is Intent -> "Intent"
        is Signal -> "Signal"
        else -> "Fact"
    }
    return "$kind $fact"
}

internal fun formatDecision(capability: String, decision: Decision<*>): List<String> = buildList {
    add("  $capability: state ${decision.state}")
    decision.effects.forEach { add("  $capability: send effect $it") }
    decision.signals.forEach { add("  $capability: emit signal $it") }
}

internal fun formatWork(capability: String, key: WorkKey, started: Boolean): String =
    "  $capability: ${if (started) "start" else "cancel"} work \"${key.name}\""

internal fun formatSignalDropped(capability: String, key: WorkKey, signal: Signal): String =
    "  $capability: drop signal $signal from cancelled work \"${key.name}\""

internal fun formatWorkFailed(capability: String, key: WorkKey, error: Throwable): String =
    "  $capability: work \"${key.name}\" failed with $error"

/** The default `toString()` prints the class and a hash: in a release build that’s an unreadable `a.b@1f2e`. */
internal fun checkReadable(fact: Fact) {
    check(!fact.hasDefaultToString()) {
        "Fact ${fact.toString().substringBeforeLast('@')} has no readable toString(). Make it a data class or a data object."
    }
}
