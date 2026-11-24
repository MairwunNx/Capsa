package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaRuntime
import com.mairwunnx.capsa.Fact
import com.mairwunnx.capsa.capability
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * One fact through the whole runtime: the queue, the choice of capabilities, `decide`, the history, the new state.
 *
 * Each capability handles three intents of its own, and the fact goes to the last capability.
 * The unconfined dispatcher processes the fact inside `append`, so one operation is one full pass.
 */
@State(Scope.Benchmark)
class FactPassBenchmark {

    @Param("1", "10")
    var capabilities: Int = 0

    private lateinit var scope: CoroutineScope
    private lateinit var runtime: CapsaRuntime
    private lateinit var last: StateFlow<Int>
    private lateinit var fact: Fact

    @Setup
    fun setUp() {
        scope = unconfinedScope()
        val builder = CapsaBuilder()
        val states = List(capabilities) { index ->
            builder.install(capability<Int, BenchIntent, Nothing>(name = "capability$index", initial = 0) { onIntents(from = index * REACTIONS, count = REACTIONS) })
        }
        runtime = builder.build(scope)
        last = states.last()
        fact = intents[capabilities * REACTIONS - 1]
        runtime.append(fact)
        check(last.value == 1) { "The runtime didn't process the fact inside append()" }
    }

    @TearDown
    fun tearDown() {
        scope.cancel()
    }

    @Benchmark
    fun passOneFact(): Int {
        runtime.append(fact)
        return last.value
    }

    private companion object {
        const val REACTIONS = 3
    }
}
