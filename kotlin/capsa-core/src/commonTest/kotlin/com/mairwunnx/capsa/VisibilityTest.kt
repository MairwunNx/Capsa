package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Checks [CapsaVisibility.Watched]: the runtime counts who collects the screen and pauses `whileVisible` works without
 * a single call from the host.
 */
class VisibilityTest {

    private data object Tick : Signal

    private data object Done : Signal

    private val ticks = WorkKey("ticks")

    /** Starts a `whileVisible` stream on [Started] and writes down when the stream starts and stops. */
    private fun ticker(log: MutableList<String>) = capability("ticker", 0) {
        on<Started> { state, _ ->
            state.commit(
                stream(ticks, onError = { Tick }, whileVisible = true) {
                    flow<Signal> { awaitCancellation() }
                        .onStart { log += "start" }
                        .onCompletion { log += "stop" }
                },
            )
        }
        on<Tick> { state, _ -> (state + 1).commit() }
    }

    private class Screen(val runtime: CapsaRuntime, val slice: StateFlow<Int>, val builder: CapsaBuilder, val scope: CoroutineScope)

    private fun TestScope.screen(
        log: MutableList<String>,
        visibility: CapsaVisibility = CapsaVisibility.Watched(),
        beforeBuild: (StateFlow<Int>) -> Unit = {},
    ): Screen {
        val scope = CoroutineScope(backgroundScope.coroutineContext + SupervisorJob(backgroundScope.coroutineContext.job))
        val builder = CapsaBuilder()
        val slice = builder.install(ticker(log))
        beforeBuild(slice)
        val runtime = builder.build(scope, CapsaConfig(visibility = visibility))
        return Screen(runtime, slice, builder, scope)
    }

    /** A collector that lives until the test cancels it, like a screen collecting its state. */
    private fun TestScope.watch(flow: Flow<*>): Job = backgroundScope.launch { flow.collect {} }

    @Test
    fun aScreenNobodyCollectsStaysHidden() = runTest {
        val log = mutableListOf<String>()
        screen(log)

        testScheduler.advanceTimeBy(10.seconds)

        assertEquals(emptyList(), log)
    }

    @Test
    fun theFirstCollectorShowsTheScreenAndTheLastOneHidesItAfterTheGrace() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        testScheduler.runCurrent()

        val ui = watch(screen.slice)
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        ui.cancelAndJoin()
        testScheduler.advanceTimeBy(999.milliseconds)
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        testScheduler.advanceTimeBy(2.milliseconds)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aRotationWithinTheGraceKeepsTheStreamRunning() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        val before = watch(screen.slice)
        testScheduler.runCurrent()

        before.cancelAndJoin()
        testScheduler.advanceTimeBy(300.milliseconds)
        watch(screen.slice)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start"), log)
    }

    @Test
    fun theScreenStaysVisibleWhileAnyOfSeveralCollectorsStays() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        val first = watch(screen.slice)
        val second = watch(screen.slice)
        testScheduler.runCurrent()

        first.cancelAndJoin()
        testScheduler.advanceTimeBy(5.seconds)
        assertEquals(listOf("start"), log)

        second.cancelAndJoin()
        testScheduler.advanceTimeBy(5.seconds)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aCollectorBeforeTheBuildCounts() = runTest {
        val log = mutableListOf<String>()
        screen(log) { slice -> watch(slice) }

        testScheduler.runCurrent()

        assertEquals(listOf("start"), log)
    }

    @Test
    fun anEagerProjectionDoesNotShowTheScreenButItsCollectorDoes() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        val label = screen.slice.mapState(screen.scope) { "ticks: $it" }
        val both = combineState(screen.scope, screen.slice, label) { count, text -> "$count $text" }
        testScheduler.advanceTimeBy(5.seconds)
        assertEquals(emptyList(), log)
        assertEquals("ticks: 0", label.value)

        val ui = watch(both)
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        ui.cancelAndJoin()
        testScheduler.advanceTimeBy(5.seconds)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aProjectionStaysFreshWithoutCollectors() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log, CapsaVisibility.Host)
        val label = screen.slice.mapState(screen.scope) { "ticks: $it" }
        testScheduler.runCurrent()

        screen.runtime.append(Tick)
        testScheduler.runCurrent()

        assertEquals("ticks: 1", label.value)
    }

    @Test
    fun aWatchedFlowCountsLikeASlice() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        val effects = Channel<Effect>(Channel.UNLIMITED)

        val ui = watch(screen.runtime.watched(effects.receiveAsFlow()))
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        ui.cancelAndJoin()
        testScheduler.advanceTimeBy(5.seconds)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun theEffectsOfTheRuntimeDoNotCount() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)

        watch(screen.runtime.effects)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(emptyList(), log)
    }

    @Test
    fun theHostVetoHidesAWatchedScreenAtOnce() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        watch(screen.slice)
        testScheduler.runCurrent()

        screen.runtime.onVisibilityChanged(false)
        testScheduler.runCurrent()
        assertEquals(listOf("start", "stop"), log)

        screen.runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()
        assertEquals(listOf("start", "stop", "start"), log)
    }

    @Test
    fun liftingTheVetoDoesNotShowAScreenNobodyCollects() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)

        screen.runtime.onVisibilityChanged(false)
        screen.runtime.onVisibilityChanged(true)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(emptyList(), log)
    }

    @Test
    fun theVetoDuringTheGraceHidesAtOnce() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        val ui = watch(screen.slice)
        testScheduler.runCurrent()

        ui.cancelAndJoin()
        screen.runtime.onVisibilityChanged(false)
        testScheduler.runCurrent()

        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aHostScreenStartsVisibleWithoutCollectors() = runTest {
        val log = mutableListOf<String>()
        screen(log, CapsaVisibility.Host)

        testScheduler.runCurrent()

        assertEquals(listOf("start"), log)
    }

    @Test
    fun aZeroGraceHidesTheScreenWithTheLastCollector() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log, CapsaVisibility.Watched(grace = Duration.ZERO))
        val ui = watch(screen.slice)
        testScheduler.runCurrent()

        ui.cancelAndJoin()
        testScheduler.runCurrent()

        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aShortReadShowsTheScreenOnlyForTheGrace() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)
        testScheduler.runCurrent()

        screen.slice.first()
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun anEagerStateInOverASliceKeepsTheScreenVisible() = runTest {
        val log = mutableListOf<String>()
        val screen = screen(log)

        @Suppress("IgnoredReturnValue")
        screen.slice.stateIn(screen.scope, SharingStarted.Eagerly, 0)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start"), log)
    }

    @Test
    fun aStreamThatEndedBeforeTheHideDoesNotRestart() = runTest {
        val log = mutableListOf<String>()
        lateinit var runtime: CapsaRuntime
        val once = capability("once", 0) {
            on<Started> { state, _ ->
                state.commit(
                    stream(ticks, onError = { Tick }, whileVisible = true) {
                        flow<Signal> {
                            log += "start"
                            // The hide lands in the queue after the stream ends but before its WorkDone,
                            // like a host thread hiding the screen while a work on another thread finishes.
                            runtime.onVisibilityChanged(false)
                        }
                    },
                )
            }
            on<Tick> { state, _ -> state.commit() }
        }
        val builder = CapsaBuilder()

        @Suppress("IgnoredReturnValue")
        builder.install(once)
        runtime = builder.build(backgroundScope, CapsaConfig(visibility = CapsaVisibility.Host))
        testScheduler.runCurrent()

        runtime.onVisibilityChanged(true)
        testScheduler.runCurrent()

        assertEquals(listOf("start"), log)
    }

    @Test
    fun aHideKeepsAPlainWorkThatSharesAKeyWithAStream() = runTest {
        val log = mutableListOf<String>()
        val mixed = capability("mixed", 0) {
            on<Started> { state, _ ->
                val plain = work(ticks, onError = { Done }, policy = WorkPolicy.Parallel) {
                    delay(2.seconds)
                    Done
                }
                val ticker = stream(ticks, onError = { Tick }, policy = WorkPolicy.Parallel, whileVisible = true) {
                    flow<Signal> { awaitCancellation() }
                        .onStart { log += "start" }
                        .onCompletion { log += "stop" }
                }
                state.commit(works = listOf(plain, ticker))
            }
            on<Done> { state, _ -> (state + 1).commit() }
            on<Tick> { state, _ -> state.commit() }
        }
        val builder = CapsaBuilder()
        val slice = builder.install(mixed)
        val runtime = builder.build(backgroundScope, CapsaConfig(visibility = CapsaVisibility.Host))
        testScheduler.runCurrent()

        runtime.onVisibilityChanged(false)
        testScheduler.runCurrent()
        runtime.onVisibilityChanged(true)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start", "stop", "start"), log)
        assertEquals(1, slice.value)
    }

    @Test
    fun aNegativeGraceIsRejected() {
        assertFailsWith<IllegalArgumentException> { CapsaVisibility.Watched(grace = (-1).seconds) }
    }
}
