package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class RetryDebounceTest {

    private sealed interface SearchSignal : Signal
    private data class Done(val attempt: Int) : SearchSignal
    private data class Failed(val message: String?) : SearchSignal
    private data class Found(val text: String) : SearchSignal

    private sealed interface SearchIntent : Intent
    private data class QueryChanged(val text: String) : SearchIntent

    private class OfflineException(message: String) : Exception(message)

    private val key = WorkKey("load")

    @Test
    fun retryWithBackoffSucceedsOnTheThirdAttempt() = runTest {
        var attempts = 0
        val retry = Retry(times = 3, delay = 100.milliseconds, factor = 2.0)
        val work = work(key, onError = { Failed(it.message) }, retry = retry) {
            attempts++
            if (attempts < 3) throw OfflineException("offline")
            Done(attempts)
        }

        val signals = work.signals().toList()

        assertEquals(listOf<Signal>(Done(3)), signals)
        // The retries waited 100 and 200 ms.
        assertEquals(300, testScheduler.currentTime)
    }

    @Test
    fun onErrorGetsTheErrorOnlyAfterTheLastAttempt() = runTest {
        var attempts = 0
        val work = work(key, onError = { Failed(it.message) }, retry = Retry(times = 2, delay = 1.seconds)) {
            attempts++
            throw OfflineException("offline")
        }

        assertEquals(listOf<Signal>(Failed("offline")), work.signals().toList())
        assertEquals(3, attempts)
    }

    @Test
    fun retryIfSkipsErrorsThatShouldNotBeRepeated() = runTest {
        var attempts = 0
        val retry = Retry(times = 3, delay = 1.seconds, retryIf = { it is OfflineException })
        val work = work(key, onError = { Failed(it.message) }, retry = retry) {
            attempts++
            error("bad request")
        }

        assertEquals(listOf<Signal>(Failed("bad request")), work.signals().toList())
        assertEquals(1, attempts)
    }

    @Test
    fun backoffIsCappedByMaxDelay() {
        val retry = Retry(times = 5, delay = 1.seconds, factor = 10.0, maxDelay = 30.seconds)

        assertEquals(listOf(1.seconds, 10.seconds, 30.seconds), (0..2).map(retry::delayBefore))
    }

    @Test
    fun workToStringShowsDebounceAndRetry() {
        val plain = work(key, onError = { Failed(it.message) }) { Done(1) }
        val tuned = work(key, onError = { Failed(it.message) }, debounce = 300.milliseconds, retry = Retry(times = 3, delay = 1.seconds, factor = 2.0)) {
            Done(1)
        }

        assertEquals("Work(load, Latest)", plain.toString())
        assertEquals("Work(load, Latest, debounce=300ms, retry=Retry(times=3, delay=1s, factor=2.0, maxDelay=Infinity))", tuned.toString())
    }

    @Test
    fun retryRejectsMeaninglessSettings() {
        assertFailsWith<IllegalArgumentException> { Retry(times = 0, delay = 1.seconds) }
        assertFailsWith<IllegalArgumentException> { Retry(times = 1, delay = 1.seconds, factor = 0.5) }
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun debounceWithLatestSendsOnlyTheLastQueryAfterAPause() = runTest {
        val queries = mutableListOf<String>()
        val search = capability<String, SearchIntent, SearchSignal>(name = "search", initial = "") {
            onIntent<QueryChanged> { _, intent ->
                Decision(
                    state = intent.text,
                    work = work(key = key, onError = { Failed(it.message) }, debounce = 300.milliseconds) {
                        queries += intent.text
                        Found(intent.text)
                    },
                )
            }
            onSignal<Found> { state, _ -> Decision(state = state) }
        }
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
        val builder = CapsaBuilder()
        builder.install(search)
        val runtime = builder.build(scope, CapsaConfig())

        runtime.append(QueryChanged("c"))
        testScheduler.advanceTimeBy(100)
        runtime.append(QueryChanged("ca"))
        testScheduler.advanceTimeBy(100)
        runtime.append(QueryChanged("cat"))
        testScheduler.advanceTimeBy(400)

        assertEquals(listOf("cat"), queries)
        scope.cancel()
    }
}
