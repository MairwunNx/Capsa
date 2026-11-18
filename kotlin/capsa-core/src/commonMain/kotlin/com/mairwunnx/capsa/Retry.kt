package com.mairwunnx.capsa

import kotlin.math.pow
import kotlin.time.Duration

/**
 * Retries a work on failure. `onError` receives the exception only after the last failed attempt.
 *
 * The delay before the retry number `n` (counting from zero) is `delay * factor^n`, but no more than `maxDelay`.
 * `factor = 1.0` gives a constant delay, `2.0` an exponential backoff.
 *
 * ```
 * work(SEND, onError = { SendFailed }, retry = Retry(times = 3, delay = 1.seconds, factor = 2.0)) { ... }
 * ```
 * Here the retries come after 1, 2, and 4 seconds. The attempt counter is shared by the whole work: a `stream`
 * subscription that emitted something and then failed keeps spending the same attempts.
 *
 * @param times how many times to retry after the first failure
 * @param delay the delay before the first retry
 * @param factor the backoff multiplier, at least `1.0`
 * @param maxDelay the upper bound of the delay
 * @param retryIf which errors to retry, for example only network ones. All of them by default
 */
public class Retry(
    public val times: Int,
    public val delay: Duration,
    public val factor: Double = 1.0,
    public val maxDelay: Duration = Duration.INFINITE,
    public val retryIf: (Throwable) -> Boolean = { true },
) {
    init {
        require(times > 0) { "times must be greater than zero: $times" }
        require(!delay.isNegative()) { "delay must not be negative: $delay" }
        require(factor >= 1.0) { "factor must not be less than 1.0: $factor" }
    }

    /** The delay before the retry number `retry`, counting from zero. */
    internal fun delayBefore(retry: Int): Duration = (delay * factor.pow(retry)).coerceAtMost(maxDelay)

    /** Shows the settings without `retryIf`: a lambda has no readable text. */
    override fun toString(): String = "Retry(times=$times, delay=$delay, factor=$factor, maxDelay=$maxDelay)"
}
