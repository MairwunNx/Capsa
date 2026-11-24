package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.benchmarks.StreamBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [StreamBenchmark] in ART. */
@RunWith(AndroidJUnit4::class)
class StreamAndroidBenchmark {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun tenThousandSignals() = rule.measure(StreamBenchmark()) { tenThousandSignals() }
}
