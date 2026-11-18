package com.mairwunnx.capsa.test

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaViolation
import com.mairwunnx.capsa.CapsaViolationException
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.WorkPolicy
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.combineState
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.mapState
import com.mairwunnx.capsa.stream
import com.mairwunnx.capsa.work
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds

/**
 * Runs the real runtime. The example: a click highlights the likes at once, and 200 ms later the screen shows a thank-you.
 * A ViewModel can’t check this without Android, here it is a plain test on virtual time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CapsaRuntimeTest {

    // --- Example: likes ---------------------------------------------------------------------------

    private data class LikesSlice(val highlighted: Int = 0, val busy: Boolean = false)
    private data class LikeClicked(val likes: Int) : Intent
    private data class HighlightShown(val likes: Int) : Signal
    private data class LikesFailed(val error: Throwable) : Signal
    private data class ShowThanks(val likes: Int) : Effect

    private val highlight = WorkKey("highlight")

    private val likes = capability("likes", LikesSlice()) {
        on<LikeClicked> { state, fact ->
            if (state.busy) {
                state.commit()
            } else {
                state.copy(highlighted = fact.likes, busy = true).commit(
                    works = listOf(
                        work(highlight, onError = ::LikesFailed, policy = WorkPolicy.KeepExisting) {
                            delay(200.milliseconds)
                            HighlightShown(fact.likes)
                        },
                    ),
                )
            }
        }
        on<HighlightShown> { _, fact -> LikesSlice().commit(effects = listOf(ShowThanks(fact.likes))) }
        on<LikesFailed> { _, _ -> LikesSlice().commit() }
    }

    @Test
    fun clickHighlightsAtOnceAndShowsThanksAfterTheDelay() = runTest {
        val harness = capsaHarness(likes)

        harness.append(LikeClicked(4))
        harness.runCurrent()

        assertEquals(LikesSlice(highlighted = 4, busy = true), harness.stateOf(likes))
        assertTrue(harness.effects.isEmpty())

        harness.advanceTimeBy(200.milliseconds)

        assertEquals(listOf<Effect>(ShowThanks(4)), harness.effects)
        assertEquals(LikesSlice(), harness.stateOf(likes))
        harness.assertNoViolations()
    }

    @Test
    fun secondClickWhileBusyDoesNotStartAnotherWork() = runTest {
        val harness = capsaHarness(likes)

        harness.append(LikeClicked(4))
        harness.append(LikeClicked(5))
        harness.advanceTimeBy(200.milliseconds)

        assertEquals(listOf<Effect>(ShowThanks(4)), harness.effects)
        assertEquals(1, harness.workEvents.count { it is WorkEvent.Started })
    }

    // --- Work policies ----------------------------------------------------------------------------

    private data class Run(val id: Int) : Intent
    private data class Finished(val id: Int) : Signal

    private fun runner(policy: WorkPolicy) = capability("runner", emptyList<Int>()) {
        on<Run> { state, fact ->
            state.commit(
                works = listOf(
                    work(WorkKey("run"), onError = ::LikesFailed, policy = policy) {
                        delay(100.milliseconds)
                        Finished(fact.id)
                    },
                ),
            )
        }
        on<Finished> { state, fact -> (state + fact.id).commit() }
    }

    @Test
    fun latestCancelsThePreviousWorkWithTheSameKey() = runTest {
        val runner = runner(WorkPolicy.Latest)
        val harness = capsaHarness(runner)

        harness.append(Run(1))
        harness.runCurrent()
        harness.append(Run(2))
        harness.advanceTimeBy(100.milliseconds)

        assertEquals(listOf(2), harness.stateOf(runner))
        assertTrue(harness.workEvents.any { it is WorkEvent.Cancelled })
    }

    @Test
    fun keepExistingIgnoresASecondStart() = runTest {
        val runner = runner(WorkPolicy.KeepExisting)
        val harness = capsaHarness(runner)

        harness.append(Run(1))
        harness.runCurrent()
        harness.append(Run(2))
        harness.advanceTimeBy(100.milliseconds)

        assertEquals(listOf(1), harness.stateOf(runner))
    }

    @Test
    fun parallelRunsBothWorks() = runTest {
        val runner = runner(WorkPolicy.Parallel)
        val harness = capsaHarness(runner)

        harness.append(Run(1))
        harness.append(Run(2))
        harness.advanceTimeBy(100.milliseconds)

        assertEquals(listOf(1, 2), harness.stateOf(runner))
    }

    @Test
    fun sameWorkKeyInTwoCapabilitiesDoesNotCollide() = runTest {
        val first = capability("first", 0) {
            on<Started> { s, _ ->
                s.commit(
                    works = listOf(
                        work(WorkKey("load"), ::LikesFailed) {
                            delay(50.milliseconds)
                            Finished(1)
                        },
                    ),
                )
            }
            on<Finished> { _, f -> f.id.commit() }
        }
        val second = capability("second", 0) {
            on<Started> { s, _ ->
                s.commit(
                    works = listOf(
                        work(WorkKey("load"), ::LikesFailed) {
                            delay(50.milliseconds)
                            Finished(2)
                        },
                    ),
                )
            }
            on<Finished> { _, f -> (f.id * 10).commit() }
        }
        val harness = capsaHarness(first, second)

        harness.advanceTimeBy(50.milliseconds)

        // Both handlers get both signals, but neither work cancelled the other.
        assertEquals(2, harness.workEvents.count { it is WorkEvent.Started })
        assertTrue(harness.workEvents.none { it is WorkEvent.Cancelled })
    }

    // --- Streams, cancellation, and errors --------------------------------------------------------

    private data class Tick(val n: Int) : Signal
    private data object StopTicks : Intent
    private data object FailNow : Intent

    private val ticks = WorkKey("ticks")

    private val ticker = capability("ticker", emptyList<Int>()) {
        on<Started> { state, _ ->
            state.commit(
                works = listOf(
                    stream(ticks, onError = ::LikesFailed) {
                        flow {
                            repeat(10) {
                                delay(10.milliseconds)
                                emit(Tick(it))
                            }
                        }
                    },
                ),
            )
        }
        on<Tick> { state, fact -> (state + fact.n).commit() }
        on<StopTicks> { state, _ -> state.commit(cancel = listOf(ticks)) }
        on<FailNow> { state, _ -> state.commit(works = listOf(work(WorkKey("boom"), ::LikesFailed) { error("boom") })) }
        on<LikesFailed> { state, _ -> (state + -1).commit() }
    }

    @Test
    fun streamStartsOnStartedAndDeliversManySignals() = runTest {
        val harness = capsaHarness(ticker)

        harness.advanceTimeBy(35.milliseconds)

        assertEquals(listOf(0, 1, 2), harness.stateOf(ticker))
    }

    @Test
    fun cancelByKeyStopsTheStream() = runTest {
        val harness = capsaHarness(ticker)

        harness.advanceTimeBy(25.milliseconds)
        harness.append(StopTicks)
        harness.advanceTimeBy(200.milliseconds)

        assertEquals(listOf(0, 1), harness.stateOf(ticker))
    }

    @Test
    fun workErrorBecomesTheTypedSignalAndCancellationDoesNot() = runTest {
        val harness = capsaHarness(ticker)

        harness.append(FailNow)
        harness.advanceTimeBy(5.milliseconds)

        assertEquals(listOf(-1), harness.stateOf(ticker))
        assertIs<LikesFailed>(harness.facts.last { it is LikesFailed })
        harness.assertNoViolations()
    }

    // --- Contract violations ----------------------------------------------------------------------

    private data object Unhandled : Intent
    private data object Boom : Intent
    private data object Loop : Signal

    @Test
    fun unhandledIntentIsReportedStartedIsNot() = runTest {
        val harness = capsaHarness(likes, strict = false)

        harness.append(Unhandled)
        harness.runCurrent()

        assertEquals(listOf<CapsaViolation>(CapsaViolation.UnhandledFact(Unhandled)), harness.violations)
    }

    @Test
    fun exceptionInDecideIsReportedAndTheRuntimeKeepsWorking() = runTest {
        val fragile = capability("fragile", 0) {
            on<Boom> { _, _ -> error("bug") }
            on<Run> { s, f -> (s + f.id).commit() }
        }
        val harness = capsaHarness(fragile, strict = false)

        harness.append(Boom)
        harness.append(Run(7))
        harness.runCurrent()

        assertIs<CapsaViolation.DecideFailed>(harness.violations.single())
        assertEquals(7, harness.stateOf(fragile))
    }

    @Test
    fun throwingOnViolationGoesToTheScopeHandlerAndTheRuntimeKeepsWorking() = runTest {
        val fragile = capability("fragile", 0) {
            on<Boom> { _, _ -> error("bug") }
            on<Run> { s, f -> (s + f.id).commit() }
        }
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val builder = CapsaBuilder()
        val state = builder.install(fragile)
        val runtime = builder.build(scope, CapsaConfig())

        runtime.append(Boom)
        runtime.append(Run(7))
        testScheduler.runCurrent()

        assertIs<CapsaViolationException>(errors.single())
        assertEquals(7, state.value)
        scope.cancel()
    }

    @Test
    fun signalCycleIsCutAtMaxSignalDepth() = runTest {
        val looping = capability("looping", 0) {
            on<Loop> { s, _ -> (s + 1).commit(signals = listOf(Loop)) }
        }
        val harness = capsaHarness(looping, maxSignalDepth = 3, strict = false)

        harness.append(Loop)
        harness.runCurrent()

        assertIs<CapsaViolation.SignalCycle>(harness.violations.single())
        assertEquals(4, harness.stateOf(looping))
    }

    // --- Effects and state ------------------------------------------------------------------------

    @Test
    fun effectsWaitInTheChannelUntilSomeoneSubscribes() = runTest {
        val builder = CapsaBuilder()
        @Suppress("IgnoredReturnValue")
        builder.install(likes)
        val runtime = builder.build(backgroundScope, CapsaConfig())

        runtime.append(LikeClicked(3))
        testScheduler.advanceTimeBy(200)
        testScheduler.runCurrent()

        // Nobody subscribed until now. The effect is not lost.
        assertEquals(ShowThanks(3), runtime.effects.first())
    }

    @Test
    fun restoredStateReplacesInitial() = runTest {
        val builder = CapsaBuilder()
        val state = builder.install(likes, restored = LikesSlice(highlighted = 2))

        assertEquals(LikesSlice(highlighted = 2), state.value)
    }

    @Test
    fun projectionsFollowSlices() = runTest {
        val builder = CapsaBuilder()
        val state = builder.install(likes)
        val runtime = builder.build(backgroundScope, CapsaConfig())

        val label = state.mapState(backgroundScope) { "likes=${it.highlighted}" }
        val both = combineState(backgroundScope, state, state) { a, b -> a.highlighted + b.highlighted }

        runtime.append(LikeClicked(3))
        testScheduler.runCurrent()

        assertEquals("likes=3", label.value)
        assertEquals(6, both.value)
    }

    // --- Pure replay without the runtime ----------------------------------------------------------

    @Test
    fun replayFoldsFactsWithoutAnyCoroutine() {
        val replay = likes.replay(LikeClicked(4), LikeClicked(5), Unhandled)

        assertEquals(LikesSlice(highlighted = 4, busy = true), replay.state)
        assertEquals(listOf(highlight), replay.works.map { it.key })
        assertEquals(listOf(Unhandled), replay.ignored)
    }

    @Test
    fun workRunsInATestWithFakeDependencies() = runTest {
        val replay = likes.replay(LikeClicked(4))

        val signal = replay.works.single().signals().first()

        assertEquals(HighlightShown(4), signal)
    }

    @Test
    fun failingWorkMapsToItsOwnErrorSignalWhenRunInATest() = runTest {
        val gate = CompletableDeferred<Unit>().apply { completeExceptionally(IllegalStateException("net")) }
        val failing = work(highlight, onError = ::LikesFailed) {
            gate.await()
            HighlightShown(1)
        }

        val signal = failing.signals().first()

        assertIs<LikesFailed>(signal)
        assertEquals("net", signal.error.message)
    }

    @Test
    fun strictHarnessFailsTheTestOnTheFirstViolation() {
        val error = assertFailsWith<CapsaViolationException> {
            runTest {
                val harness = capsaHarness(likes)
                harness.append(Unhandled)
                harness.runCurrent()
            }
        }

        assertIs<CapsaViolation.UnhandledFact>(error.violation)
    }

    @Test
    fun harnessRecordsFailedWorksAndDroppedSignals() = runTest {
        val flaky = capability("flaky", 0) {
            on<Run> { s, f ->
                s.commit(
                    work(WorkKey("run"), onError = { Finished(-1) }) {
                        delay(100.milliseconds)
                        if (f.id < 0) error("offline")
                        Finished(f.id)
                    },
                )
            }
            on<Finished> { s, f -> (s + f.id).commit() }
        }
        val harness = capsaHarness(flaky)

        harness.append(Run(-5))
        harness.runCurrent()
        harness.advanceTimeBy(100.milliseconds)
        harness.append(Run(1))
        harness.runCurrent()
        // The work of Run(1) puts Finished(1) into the queue only after Run(2).
        testScheduler.advanceTimeBy(100)
        harness.append(Run(2))
        harness.runCurrent()
        harness.advanceTimeBy(100.milliseconds)

        assertTrue(harness.workEvents.any { it is WorkEvent.Failed && it.error.message == "offline" })
        assertTrue(harness.workEvents.any { it is WorkEvent.Dropped && it.signal == Finished(1) })
        assertEquals(1, harness.stateOf(flaky))
    }
}
