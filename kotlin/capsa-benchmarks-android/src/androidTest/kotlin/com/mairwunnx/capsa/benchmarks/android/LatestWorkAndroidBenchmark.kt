package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mairwunnx.capsa.benchmarks.LatestWorkBenchmark
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** [LatestWorkBenchmark] in ART. */
@RunWith(AndroidJUnit4::class)
class LatestWorkAndroidBenchmark {

    @get:Rule
    val rule = BenchmarkRule()

    @Test
    fun typeOneCharacter() = rule.measure(LatestWorkBenchmark(), setUp = { setUp() }, tearDown = { tearDown() }) { typeOneCharacter() }
}
