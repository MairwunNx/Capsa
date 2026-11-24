package com.mairwunnx.capsa.samples.counter

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability

data class CounterSlice(val count: Int = 0)

sealed interface CounterIntent : Intent
data object PlusClicked : CounterIntent
data object MinusClicked : CounterIntent
data object ResetClicked : CounterIntent

context(_: CapabilityScope)
fun counter(): Capability<CounterSlice> = capability<CounterSlice, CounterIntent, Nothing>(name = "counter", initial = CounterSlice()) {
    onIntent<PlusClicked> { state, _ -> Decision(state = state.copy(count = state.count + 1)) }
    onIntent<MinusClicked> { state, _ -> Decision(state = state.copy(count = state.count - 1)) }
    onIntent<ResetClicked> { _, _ -> Decision(state = CounterSlice()) }
}
