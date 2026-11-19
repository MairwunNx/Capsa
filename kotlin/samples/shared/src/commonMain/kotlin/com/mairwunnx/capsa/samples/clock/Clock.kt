package com.mairwunnx.capsa.samples.clock

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.stream
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

/** How long the user has looked at the screen. */
data class ClockSlice(val seconds: Int = 0)

data object Ticked : Signal
data object ClockFailed : Signal

private val TICK = WorkKey("tick")

context(_: CapabilityScope)
fun clock(): Capability<ClockSlice> = capability("clock", ClockSlice()) {
    // whileVisible: the runtime stops the ticks while the screen is hidden and starts them again when it comes back.
    on<Started> { state, _ -> state.commit(stream(TICK, onError = { ClockFailed }, whileVisible = true) { ticks() }) }
    on<Ticked> { state, _ -> state.copy(seconds = state.seconds + 1).commit() }
    // A tick can’t fail, but every work names the signal of its failure.
    on<ClockFailed> { state, _ -> state.commit() }
}

private fun ticks(): Flow<Signal> = flow {
    while (true) {
        delay(1.seconds)
        emit(Ticked)
    }
}
