package com.mairwunnx.capsa.samples.counter

import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import com.mairwunnx.capsa.test.replay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterTest {

    private val counter = inCapabilityScope { counter() }

    @Test
    fun `plus and minus change the count`() {
        val replay = counter.replay(PlusClicked, PlusClicked, MinusClicked)
        assertEquals(CounterSlice(count = 1), replay.state)
    }

    @Test
    fun `reset brings the count back to zero`() {
        val replay = counter.replay(PlusClicked, ResetClicked, from = CounterSlice(count = 5))
        assertEquals(CounterSlice(count = 0), replay.state)
    }

    @Test
    fun `the runtime handles the clicks in order`() = runTest {
        val harness = capsaHarness(counter)

        harness.append(PlusClicked)
        harness.append(PlusClicked)
        harness.append(MinusClicked)
        harness.runCurrent()

        assertEquals(CounterSlice(count = 1), harness.stateOf(counter))
    }
}
