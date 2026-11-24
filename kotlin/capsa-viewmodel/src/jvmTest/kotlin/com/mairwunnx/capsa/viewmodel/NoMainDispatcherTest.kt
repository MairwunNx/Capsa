package com.mairwunnx.capsa.viewmodel

import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals

/** A plain JVM app has no main dispatcher until it adds one, and the view model must still work there. */
class NoMainDispatcherTest {

    private data class Counter(val value: Int = 0, val started: Boolean = false)
    private data object Increment : Intent

    private class CounterViewModel : CapsaViewModel(CapsaLogger { _, _, _ -> }) {
        val counter: StateFlow<Counter> = install(
            capability<Counter, Increment, Nothing>(name = "counter", initial = Counter()) {
                bootstrap { state -> Decision(state = state.copy(started = true)) }
                onIntent<Increment> { state, _ -> Decision(state = state.copy(value = state.value + 1)) }
            },
        )
    }

    @Test
    fun withoutAMainDispatcherTheViewModelIsCreatedAndTheFirstIntentStartsTheRuntime() = runBlocking {
        val vm = CounterViewModel()

        vm.send(Increment)

        assertEquals(Counter(value = 1, started = true), withTimeout(5_000) { vm.counter.first { it.value == 1 } })
    }
}
