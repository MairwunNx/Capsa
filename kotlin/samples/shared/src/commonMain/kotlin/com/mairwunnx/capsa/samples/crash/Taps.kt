package com.mairwunnx.capsa.samples.crash

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability

data class TapsSlice(val taps: Int = 0)

sealed interface TapsIntent : Intent
data object TapClicked : TapsIntent
data object BugClicked : TapsIntent

context(_: CapabilityScope)
fun taps(): Capability<TapsSlice> = capability<TapsSlice, TapsIntent, Nothing>(name = "taps", initial = TapsSlice()) {
    onIntent<TapClicked> { state, _ -> Decision(state = state.copy(taps = state.taps + 1)) }
    // A bug on purpose. The runtime keeps the last good slice, and Capsa.failFast chooses between a crash and a line in the log.
    onIntent<BugClicked> { _, _ -> error("A bug in decide") }
}
