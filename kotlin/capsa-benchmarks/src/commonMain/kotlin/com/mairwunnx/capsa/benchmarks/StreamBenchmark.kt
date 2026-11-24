package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking

/**
 * A `stream` work emits 10 000 signals, and the runtime passes each one to the capability.
 *
 * One operation builds a runtime on a single-threaded event loop, like the main thread of an app, and waits for the last signal.
 */
@State(Scope.Benchmark)
class StreamBenchmark {

    @Benchmark
    fun tenThousandSignals(): Int = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job(coroutineContext[Job]))
        val builder = CapsaBuilder()
        val received = builder.install(
            capability<Int, Nothing, TickSignal>(name = "ticks", initial = 0) {
                bootstrap { state ->
                    Decision(state = state, work = stream(key = TICKS, onError = { TicksFailed }) { flow { repeat(SIGNALS) { emit(Tick) } } })
                }
                onSignal<Tick> { state, _ -> Decision(state = state + 1) }
                onSignal<TicksFailed> { state, _ -> Decision(state = state) }
            },
        )
        builder.build(scope)
        val count = received.first { it == SIGNALS }
        scope.cancel()
        count
    }

    private sealed interface TickSignal : Signal

    private data object Tick : TickSignal

    private data object TicksFailed : TickSignal

    private companion object {
        const val SIGNALS = 10_000
        val TICKS = WorkKey("ticks")
    }
}
