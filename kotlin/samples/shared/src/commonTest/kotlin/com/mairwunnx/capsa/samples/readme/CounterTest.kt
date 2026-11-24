// Checks the counter from the README of Capsa: https://github.com/MairwunNx/Capsa
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class CounterTest {

    @Test
    fun `plus and minus change the count`() = runTest {
        val counter = inCapabilityScope { counter() }
        val harness = capsaHarness(counter)

        harness.append(PlusClicked)
        harness.append(PlusClicked)
        harness.append(MinusClicked)
        harness.runCurrent()

        assertEquals(CounterSlice(count = 1), harness.stateOf(counter))
    }
}
