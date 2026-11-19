package com.mairwunnx.capsa.samples.counter

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit

data class CounterSlice(val count: Int = 0)

data object PlusClicked : Intent
data object MinusClicked : Intent
data object ResetClicked : Intent

context(_: CapabilityScope)
fun counter(): Capability<CounterSlice> = capability("counter", CounterSlice()) {
    on<PlusClicked> { state, _ -> state.copy(count = state.count + 1).commit() }
    on<MinusClicked> { state, _ -> state.copy(count = state.count - 1).commit() }
    on<ResetClicked> { _, _ -> CounterSlice().commit() }
}
