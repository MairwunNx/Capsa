package com.mairwunnx.capsa.samples.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A cold start of the demo app up to the list of samples. Capsa barely shows here: the number gives the order of magnitude. */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStart() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.COLD,
        iterations = ITERATIONS,
    ) {
        pressHome()
        startActivityAndWait()
        awaitHome()
    }
}
