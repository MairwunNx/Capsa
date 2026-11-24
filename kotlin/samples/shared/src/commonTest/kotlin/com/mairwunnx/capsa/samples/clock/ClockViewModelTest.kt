package com.mairwunnx.capsa.samples.clock

import com.mairwunnx.capsa.viewmodel.test.capsaViewModel
import com.mairwunnx.capsa.viewmodel.test.collectAsScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class ClockViewModelTest {

    @Test
    fun `the clock ticks while the screen collects the time`() = runTest {
        val viewModel = capsaViewModel { ClockViewModel() }

        collectAsScreen(viewModel.time)
        advanceTimeBy(3.seconds)
        runCurrent()

        assertEquals("00:03", viewModel.time.value)
    }

    @Test
    fun `without the screen the clock stands still`() = runTest {
        val viewModel = capsaViewModel { ClockViewModel() }

        advanceTimeBy(3.seconds)
        runCurrent()

        assertEquals("00:00", viewModel.time.value)
    }
}
