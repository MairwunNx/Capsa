package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.Started
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.stream
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
            capability("ticks", 0) {
                on<Started> { state, _ ->
                    state.commit(stream(TICKS, onError = { TicksFailed }) { flow { repeat(SIGNALS) { emit(Tick) } } })
                }
                on<Tick> { state, _ -> (state + 1).commit() }
                on<TicksFailed> { state, _ -> state.commit() }
            },
        )
        builder.build(scope)
        val count = received.first { it == SIGNALS }
        scope.cancel()
        count
    }

    private data object Tick : Signal

    private data object TicksFailed : Signal

    private companion object {
        const val SIGNALS = 10_000
        val TICKS = WorkKey("ticks")
    }
}
