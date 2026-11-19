package com.mairwunnx.capsa.samples.backend

import kotlinx.coroutines.delay
import kotlin.random.Random

/** What a request throws when the fake server fails. */
class NetworkException : Exception("The server didn’t answer")

/**
 * Stands in for a request: waits for a random `latency` in milliseconds, then fails with the chance `failRate` or returns `answer`.
 *
 * The demo has no backend. Every port of the samples goes through here.
 */
suspend fun <T> network(failRate: Double = 0.0, latency: LongRange = 300L..900L, answer: () -> T): T {
    delay(Random.nextLong(latency.first, latency.last + 1))
    if (Random.nextDouble() < failRate) throw NetworkException()
    return answer()
}
