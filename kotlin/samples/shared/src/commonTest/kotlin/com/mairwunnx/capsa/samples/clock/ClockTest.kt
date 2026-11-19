package com.mairwunnx.capsa.samples.clock

import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ClockTest {

    @Test
    fun `the clock ticks only while the screen is visible`() = runTest {
        val clock = inCapabilityScope { clock() }
        val harness = capsaHarness(clock)

        harness.advanceTimeBy(3.seconds)
        assertEquals(ClockSlice(seconds = 3), harness.stateOf(clock))

        harness.setVisible(false)
        harness.advanceTimeBy(10.seconds)
        assertEquals(ClockSlice(seconds = 3), harness.stateOf(clock))

        harness.setVisible(true)
        harness.advanceTimeBy(2.seconds)
        assertEquals(ClockSlice(seconds = 5), harness.stateOf(clock))
    }
}
