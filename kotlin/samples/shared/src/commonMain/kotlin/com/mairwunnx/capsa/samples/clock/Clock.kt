package com.mairwunnx.capsa.samples.clock

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

/** How long the user has looked at the screen. */
data class ClockSlice(val seconds: Int = 0)

sealed interface ClockSignal : Signal
data object Ticked : ClockSignal
data object ClockFailed : ClockSignal

private val TICK = WorkKey("tick")

context(_: CapabilityScope)
fun clock(): Capability<ClockSlice> = capability<ClockSlice, Nothing, ClockSignal>(name = "clock", initial = ClockSlice()) {
    val works = object {
        // whileVisible: the runtime stops the ticks while the screen is hidden and starts them again when it comes back.
        fun ticks() = stream(key = TICK, onError = { ClockFailed }, whileVisible = true) { everySecond() }
    }

    bootstrap { state -> Decision(state = state, work = works.ticks()) }
    onSignal<Ticked> { state, _ -> Decision(state = state.copy(seconds = state.seconds + 1)) }
    // A tick can’t fail, but every work names the signal of its failure.
    onSignal<ClockFailed> { state, _ -> Decision(state = state) }
}

private fun everySecond(): Flow<ClockSignal> = flow {
    while (true) {
        delay(1.seconds)
        emit(Ticked)
    }
}
