package com.mairwunnx.capsa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WorkLifecycleTest {

    private sealed interface RunnerIntent : Intent
    private data class Run(val id: Int) : RunnerIntent
    private data object Stop : RunnerIntent
    private data class Finished(val id: Int) : Signal

    private sealed interface TickerIntent : Intent
    private data object Watch : TickerIntent
    private data object Foreign : TickerIntent
    private data object Tick : Signal

    private data object WatchBoth : Intent

    private val run = WorkKey("run")
    private val ticks = WorkKey("ticks")

    private val runner = capability<List<Int>, RunnerIntent, Finished>(name = "runner", initial = emptyList()) {
        onIntent<Run> { state, intent ->
            Decision(
                state = state,
                work = work(key = run, onError = { Finished(-1) }) {
                    delay(100.milliseconds)
                    Finished(intent.id)
                },
            )
        }
        onSignal<Finished> { state, finished -> Decision(state = state + finished.id) }
        onIntent<Stop> { state, _ -> Decision(state = state, cancel = run) }
    }

    private val ticker = capability<Int, TickerIntent, Tick>(name = "ticker", initial = 0) {
        onIntent<Watch> { state, _ ->
            Decision(
                state = state,
                work = stream(key = ticks, onError = { Tick }, whileVisible = true) {
                    flow {
                        while (true) {
                            delay(100.milliseconds)
                            emit(Tick)
                        }
                    }
                },
            )
        }
        onSignal<Tick> { state, _ -> Decision(state = state + 1) }
        onIntent<Foreign> { _, _ -> throw CancellationException("not ours") }
    }

    private val twinTickers = capability<Int, WatchBoth, Tick>(name = "twins", initial = 0) {
        onIntent<WatchBoth> { state, _ ->
            val ticker = stream(key = ticks, onError = { Tick }, policy = WorkPolicy.Parallel, whileVisible = true) {
                flow {
                    while (true) {
                        delay(100.milliseconds)
                        emit(Tick)
                    }
                }
            }
            Decision(state = state, works = listOf(ticker, ticker))
        }
        onSignal<Tick> { state, _ -> Decision(state = state + 1) }
    }

    private class Harness<S : Any>(
        val runtime: CapsaRuntime,
        val state: StateFlow<S>,
        val errors: List<Throwable>,
        val scope: CoroutineScope,
    )

    /**
     * Starts the runtime in its own scope, so `advanceUntilIdle` drives its works.
     *
     * `inBackground` puts the scope under `backgroundScope` for endless streams: `runTest` cancels them by itself,
     * even after a failed assertion. `advanceUntilIdle` doesn't wait for such works, so those tests use `advanceTimeBy`.
     */
    private fun <S : Any> TestScope.start(capability: Capability<S>, inBackground: Boolean = false): Harness<S> {
        val errors = mutableListOf<Throwable>()
        val handler = CoroutineExceptionHandler { _, e -> errors += e }
        val scope = if (inBackground) {
            CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job) + handler)
        } else {
            CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob() + handler)
        }
        val builder = CapsaBuilder()
        val state = builder.install(capability)
        // The tests drive visibility by hand, so nobody has to collect the slice.
        val config = CapsaConfig(visibility = CapsaVisibility.Host)
        return Harness(runtime = builder.build(scope, config), state = state, errors = errors, scope = scope)
    }

    @Test
    fun restartWithLatestDropsTheQueuedAnswerOfThePreviousRun() = runTest {
        val rt = start(runner)

        rt.runtime.append(Run(1))
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(100.milliseconds)
        // The first work puts Finished(1) into the queue only after Run(2).
        rt.runtime.append(Run(2))
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(2), rt.state.value)
        rt.scope.cancel()
    }

    @Test
    fun cancelDropsTheQueuedAnswerOfAFinishedRun() = runTest {
        val rt = start(runner)

        rt.runtime.append(Run(1))
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(100.milliseconds)
        rt.runtime.append(Stop)
        testScheduler.advanceUntilIdle()

        assertEquals(emptyList(), rt.state.value)
        assertTrue(rt.errors.isEmpty())
        rt.scope.cancel()
    }

    @Test
    fun answersBeforeTheCancelAreStillApplied() = runTest {
        val rt = start(runner)

        rt.runtime.append(Run(1))
        testScheduler.advanceUntilIdle()
        rt.runtime.append(Stop)
        testScheduler.advanceUntilIdle()

        assertEquals(listOf(1), rt.state.value)
        rt.scope.cancel()
    }

    @Test
    fun whileVisibleStreamPausesWhileHiddenAndRestartsWhenVisible() = runTest {
        val rt = start(ticker, inBackground = true)

        rt.runtime.append(Watch)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(350.milliseconds)
        assertEquals(3, rt.state.value)

        rt.runtime.onVisibilityChanged(false)
        testScheduler.advanceTimeBy(1_000.milliseconds)
        assertEquals(3, rt.state.value)

        rt.runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(250.milliseconds)
        assertEquals(5, rt.state.value)
    }

    @Test
    fun whileVisibleStreamStartedWhileHiddenWaitsForTheScreen() = runTest {
        val rt = start(ticker, inBackground = true)

        rt.runtime.onVisibilityChanged(false)
        rt.runtime.append(Watch)
        testScheduler.advanceTimeBy(500.milliseconds)
        assertEquals(0, rt.state.value)

        rt.runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(150.milliseconds)
        assertEquals(1, rt.state.value)
    }

    @Test
    fun parallelWhileVisibleStreamsWithOneKeyAllRestartWhenVisible() = runTest {
        val rt = start(twinTickers, inBackground = true)

        rt.runtime.append(WatchBoth)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(150.milliseconds)
        assertEquals(2, rt.state.value)

        rt.runtime.onVisibilityChanged(false)
        testScheduler.advanceTimeBy(1_000.milliseconds)
        assertEquals(2, rt.state.value)

        rt.runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(150.milliseconds)
        assertEquals(4, rt.state.value)
    }

    @Test
    fun foreignCancellationInDecideIsReportedAndTheRuntimeKeepsWorking() = runTest {
        val rt = start(ticker)

        rt.runtime.append(Foreign)
        rt.runtime.append(Tick)
        testScheduler.runCurrent()

        val error = assertIs<CapsaViolationException>(rt.errors.single())
        assertIs<CapsaViolation.DecideFailed>(error.violation)
        assertEquals(1, rt.state.value)
        rt.scope.cancel()
    }
}
