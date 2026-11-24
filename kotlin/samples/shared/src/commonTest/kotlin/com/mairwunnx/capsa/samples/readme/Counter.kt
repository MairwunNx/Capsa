// From the README of Capsa, word for word: https://github.com/MairwunNx/Capsa
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability

data class CounterSlice(val count: Int = 0)

sealed interface CounterIntent : Intent
data object PlusClicked : CounterIntent
data object MinusClicked : CounterIntent

context(_: CapabilityScope)
fun counter() = capability<CounterSlice, CounterIntent, Nothing>(name = "counter", initial = CounterSlice()) {
    onIntent<PlusClicked> { state, _ -> Decision(state = state.copy(count = state.count + 1)) }
    onIntent<MinusClicked> { state, _ -> Decision(state = state.copy(count = state.count - 1)) }
}
