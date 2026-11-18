package com.mairwunnx.capsa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

class WorkLifecycleTest {

    private data class Run(val id: Int) : Intent
    private data class Finished(val id: Int) : Signal
    private data object Stop : Intent
    private data object Watch : Intent
    private data object Tick : Signal
    private data object Foreign : Intent

    private val run = WorkKey("run")
    private val ticks = WorkKey("ticks")

    private val runner = capability("runner", emptyList<Int>()) {
        on<Run> { state, fact ->
            state.commit(
                work(run, onError = { Finished(-1) }) {
                    delay(100.milliseconds)
                    Finished(fact.id)
                },
            )
        }
        on<Finished> { state, fact -> (state + fact.id).commit() }
        on<Stop> { state, _ -> state.commit(cancel = listOf(run)) }
    }

    private val ticker = capability("ticker", 0) {
        on<Watch> { state, _ ->
            state.commit(
                stream(ticks, onError = { Tick }, whileVisible = true) {
                    flow {
                        while (true) {
                            delay(100.milliseconds)
                            emit(Tick)
                        }
                    }
                },
            )
        }
        on<Tick> { state, _ -> (state + 1).commit() }
        on<Foreign> { _, _ -> throw CancellationException("not ours") }
    }

    private class Harness<S : Any>(
        val runtime: CapsaRuntime,
        val state: StateFlow<S>,
        val errors: List<Throwable>,
        val scope: CoroutineScope,
    )

    private fun <S : Any> TestScope.start(capability: Capability<S>): Harness<S> {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val builder = CapsaBuilder()
        val state = builder.install(capability)
        return Harness(runtime = builder.build(scope, CapsaConfig()), state = state, errors = errors, scope = scope)
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
        val rt = start(ticker)

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
        rt.scope.cancel()
    }

    @Test
    fun whileVisibleStreamStartedWhileHiddenWaitsForTheScreen() = runTest {
        val rt = start(ticker)

        rt.runtime.onVisibilityChanged(false)
        rt.runtime.append(Watch)
        testScheduler.advanceTimeBy(500.milliseconds)
        assertEquals(0, rt.state.value)

        rt.runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()
        testScheduler.advanceTimeBy(150.milliseconds)
        assertEquals(1, rt.state.value)
        rt.scope.cancel()
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
