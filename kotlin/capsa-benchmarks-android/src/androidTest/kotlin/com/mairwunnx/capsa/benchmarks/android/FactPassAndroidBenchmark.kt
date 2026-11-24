package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import com.mairwunnx.capsa.benchmarks.FactPassBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** [FactPassBenchmark] in ART. */
@RunWith(Parameterized::class)
class FactPassAndroidBenchmark(private val capabilities: Int) {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun passOneFact() = rule.measure(FactPassBenchmark().also { it.capabilities = capabilities }, setUp = { setUp() }, tearDown = { tearDown() }) { passOneFact() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "capabilities={0}")
        fun parameters(): List<Int> = listOf(1, 10)
    }
}
