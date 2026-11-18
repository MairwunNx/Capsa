package com.mairwunnx.capsa.viewmodel

import com.mairwunnx.capsa.AppForeground
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.mapState
import com.mairwunnx.capsa.stream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Checks that a [CapsaViewModel] learns the visibility of its screen without any call: from the collectors of its flows
 * and from the foreground of the app. The app source is replaced, so the tests behave the same on every platform.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelVisibilityTest {

    private data object Tick : Signal
    private data object Ping : Intent

    /** Keeps a `whileVisible` stream from [Started] and writes down when it starts and stops. */
    private class ScreenViewModel(log: MutableList<String>) : CapsaViewModel(RecordingLogger()) {
        val ticks: StateFlow<Int> = install(
            capability("ticker", 0) {
                on<Started> { state, _ ->
                    state.commit(
                        stream(TICKS, onError = { Tick }, whileVisible = true) {
                            flow<Signal> { awaitCancellation() }
                                .onStart { log += "start" }
                                .onCompletion { log += "stop" }
                        },
                    )
                }
                on<Tick> { state, _ -> (state + 1).commit() }
                on<Ping> { state, _ -> state.commit() }
            },
        )

        val label: StateFlow<String> = ticks.mapState(capsaScope) { "ticks: $it" }
    }

    private val log = mutableListOf<String>()
    private var failFastBefore = Capsa.failFast

    @BeforeTest
    fun setUp() {
        failFastBefore = Capsa.failFast
        Capsa.failFast = true
        AppForeground.replace(null)
    }

    @AfterTest
    fun tearDown() {
        AppForeground.reset()
        Dispatchers.resetMain()
        CapsaViewModel.resetWorkDispatcher()
        Capsa.failFast = failFastBefore
    }

    private fun TestScope.screen(): ScreenViewModel {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        CapsaViewModel.setWorkDispatcher(StandardTestDispatcher(testScheduler))
        return ScreenViewModel(log)
    }

    private fun TestScope.watch(flow: Flow<*>): Job = backgroundScope.launch { flow.collect {} }

    @Test
    fun aViewModelWithoutUiKeepsWhileVisibleWorksPaused() = runTest {
        val vm = screen()

        vm.send(Ping)
        testScheduler.advanceTimeBy(10.seconds)

        assertEquals(emptyList(), log)
    }

    @Test
    fun aScreenThatCollectsOnlyItsStateIsVisible() = runTest {
        val vm = screen()
        testScheduler.runCurrent()

        watch(vm.label)
        testScheduler.runCurrent()

        assertEquals(listOf("start"), log)
    }

    @Test
    fun theCollectorOfTheEffectsShowsTheScreen() = runTest {
        val vm = screen()

        val ui = watch(vm.effects.flow)
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        ui.cancelAndJoin()
        testScheduler.advanceTimeBy(2.seconds)
        assertEquals(listOf("start", "stop"), log)
    }

    @Test
    fun aRotationDoesNotRestartTheWork() = runTest {
        val vm = screen()
        val before = watch(vm.ticks)
        val beforeEffects = watch(vm.effects.flow)
        testScheduler.runCurrent()

        before.cancelAndJoin()
        beforeEffects.cancelAndJoin()
        testScheduler.advanceTimeBy(400.milliseconds)
        watch(vm.ticks)
        watch(vm.effects.flow)
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start"), log)
    }

    @Test
    fun twoScreensOnOneViewModelDoNotHideEachOther() = runTest {
        val vm = screen()
        val first = watch(vm.ticks)
        watch(vm.label)
        testScheduler.runCurrent()

        first.cancelAndJoin()
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(listOf("start"), log)
    }

    @Test
    fun theAppInTheBackgroundPausesWorksWhileTheUiKeepsCollecting() = runTest {
        val foreground = MutableStateFlow(true)
        AppForeground.replace(foreground)
        val vm = screen()
        // Like collectAsState() without a lifecycle: the composition keeps collecting in the background.
        watch(vm.ticks)
        testScheduler.runCurrent()
        assertEquals(listOf("start"), log)

        foreground.value = false
        testScheduler.runCurrent()
        assertEquals(listOf("start", "stop"), log)

        foreground.value = true
        testScheduler.runCurrent()
        assertEquals(listOf("start", "stop", "start"), log)
    }

    @Test
    fun theForegroundAloneDoesNotShowAScreenNobodyCollects() = runTest {
        val foreground = MutableStateFlow(false)
        AppForeground.replace(foreground)
        screen()
        testScheduler.runCurrent()

        foreground.value = true
        testScheduler.advanceTimeBy(5.seconds)

        assertEquals(emptyList(), log)
    }

    private companion object {
        val TICKS = WorkKey("ticks")
    }
}
