package com.mairwunnx.capsa.viewmodel.test

import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.viewmodel.CapsaViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class CapsaViewModelHelpersTest {

    private data object Ticked : Signal

    private class ClockViewModel : CapsaViewModel() {
        var cleared = false

        val seconds: StateFlow<Int> = install(
            capability<Int, Nothing, Ticked>(name = "clock", initial = 0) {
                bootstrap { state -> Decision(state = state, work = stream(key = TICK, onError = { Ticked }, whileVisible = true) { everySecond() }) }
                onSignal<Ticked> { state, _ -> Decision(state = state + 1) }
            },
        )

        override fun onCleared() {
            cleared = true
        }
    }

    @Test
    fun theClockTicksWhileTheScreenCollectsIt() = runTest {
        val viewModel = capsaViewModel { ClockViewModel() }

        collectAsScreen(viewModel.seconds)
        advanceTimeBy(3.seconds)
        runCurrent()

        assertEquals(3, viewModel.seconds.value)
    }

    @Test
    fun withoutACollectorTheClockStandsStill() = runTest {
        val viewModel = capsaViewModel { ClockViewModel() }

        advanceTimeBy(3.seconds)
        runCurrent()

        assertEquals(0, viewModel.seconds.value)
    }

    @Test
    fun aHiddenScreenPausesTheClockAfterTheGrace() = runTest {
        val viewModel = capsaViewModel { ClockViewModel() }
        val screen = collectAsScreen(viewModel.seconds)
        advanceTimeBy(2.seconds)
        runCurrent()

        screen.cancelAndJoin()
        advanceTimeBy(10.seconds)
        runCurrent()

        assertTrue(viewModel.seconds.value in 2..3)
    }

    @Test
    fun theViewModelIsClearedWhenTheTestEnds() {
        lateinit var viewModel: ClockViewModel
        runTest { viewModel = capsaViewModel { ClockViewModel() } }

        assertTrue(viewModel.cleared)
    }

    private companion object {
        val TICK = WorkKey("tick")

        fun everySecond() = flow {
            while (true) {
                delay(1.seconds)
                emit(Ticked)
            }
        }
    }
}
