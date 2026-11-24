package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import com.mairwunnx.capsa.benchmarks.EffectHandlersBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** [EffectHandlersBenchmark] in ART. */
@RunWith(Parameterized::class)
class EffectHandlersAndroidBenchmark(private val handlers: Int) {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun handle() = rule.measure(EffectHandlersBenchmark().also { it.handlers = handlers }, setUp = { setUp() }) { handle() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "handlers={0}")
        fun parameters(): List<Int> = listOf(1, 10, 30)
    }
}
