package com.mairwunnx.capsa.samples.crash

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit

data class TapsSlice(val taps: Int = 0)

data object TapClicked : Intent
data object BugClicked : Intent

context(_: CapabilityScope)
fun taps(): Capability<TapsSlice> = capability("taps", TapsSlice()) {
    on<TapClicked> { state, _ -> state.copy(taps = state.taps + 1).commit() }
    // A bug on purpose. The runtime keeps the last good slice, and Capsa.failFast decides between a crash and a line in the log.
    on<BugClicked> { _, _ -> error("A bug in decide") }
}
