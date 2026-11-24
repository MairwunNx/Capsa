package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.benchmarks.CombineStateBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [CombineStateBenchmark] in ART. */
@RunWith(AndroidJUnit4::class)
class CombineStateAndroidBenchmark {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun changeOneSlice() = rule.measure(CombineStateBenchmark(), setUp = { setUp() }, tearDown = { tearDown() }) { changeOneSlice() }
}
