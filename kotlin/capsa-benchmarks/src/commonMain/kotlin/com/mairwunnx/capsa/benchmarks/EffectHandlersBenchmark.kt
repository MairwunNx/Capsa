package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.effectHandlers
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Param
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State

/** `EffectHandlers.handle`: the search for the handler and the handler itself. The effect matches the last handler. */
@State(Scope.Benchmark)
class EffectHandlersBenchmark {

    @Param("1", "10", "30")
    var handlers: Int = 0

    private val handled = IntArray(1)
    private lateinit var effectHandlers: EffectHandlers
    private lateinit var effect: Effect

    @Setup
    fun setUp() {
        effectHandlers = effectHandlers { onEffects(count = handlers, handled = handled) }
        effect = effects[handlers - 1]
    }

    @Benchmark
    fun handle(): Int {
        effectHandlers.handle(effect)
        return handled[0]
    }
}
