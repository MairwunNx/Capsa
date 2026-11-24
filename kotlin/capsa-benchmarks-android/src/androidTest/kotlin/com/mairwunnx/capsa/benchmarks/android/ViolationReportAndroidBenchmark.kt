package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.benchmarks.ViolationReportBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [ViolationReportBenchmark] in ART. */
@RunWith(AndroidJUnit4::class)
class ViolationReportAndroidBenchmark {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun reportUnhandledFact() = rule.measure(ViolationReportBenchmark(), setUp = { setUp() }, tearDown = { tearDown() }) { reportUnhandledFact() }
}
