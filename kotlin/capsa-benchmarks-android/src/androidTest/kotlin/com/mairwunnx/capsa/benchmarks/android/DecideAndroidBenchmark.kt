package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import com.mairwunnx.capsa.benchmarks.DecideBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** [DecideBenchmark] in ART. */
@RunWith(Parameterized::class)
class DecideAndroidBenchmark(private val reactions: Int) {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun decide() = rule.measure(DecideBenchmark().also { it.reactions = reactions }, setUp = { setUp() }) { decide() }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "reactions={0}")
        fun parameters(): List<Int> = listOf(1, 10, 30)
    }
}
