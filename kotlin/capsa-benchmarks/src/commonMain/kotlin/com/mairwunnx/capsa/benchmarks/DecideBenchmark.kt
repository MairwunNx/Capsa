package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Fact
import com.mairwunnx.capsa.capability
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** `decide` of a capability built with `capability { }`: the search for the reaction and the reaction itself. The fact matches the last reaction. */
@State(Scope.Benchmark)
class DecideBenchmark {

    @Param("1", "10", "30")
    var reactions: Int = 0

    private lateinit var capability: Capability<Int>
    private lateinit var fact: Fact

    @Setup
    fun setUp() {
        capability = capability("decide", 0) { onIntents(from = 0, count = reactions) }
        fact = intents[reactions - 1]
    }

    @Benchmark
    fun decide(): Decision<Int> = capability.decide(0, fact)
}
