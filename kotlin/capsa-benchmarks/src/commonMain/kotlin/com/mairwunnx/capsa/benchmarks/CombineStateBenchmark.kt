package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.combineState
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A projection over three slices: one slice changes, and the projection recomputes. The unconfined dispatcher recomputes it in place. */
@State(Scope.Benchmark)
class CombineStateBenchmark {

    private lateinit var scope: CoroutineScope
    private val first = MutableStateFlow(0)
    private val second = MutableStateFlow(0)
    private val third = MutableStateFlow(0)
    private lateinit var projection: StateFlow<Int>

    @Setup
    fun setUp() {
        scope = unconfinedScope()
        projection = combineState(scope, first, second, third) { a, b, c -> a + b + c }
        first.value = 1
        check(projection.value == 1) { "The projection didn't recompute in place" }
    }

    @TearDown
    fun tearDown() {
        scope.cancel()
    }

    @Benchmark
    fun changeOneSlice(): Int {
        first.value += 1
        return projection.value
    }
}
