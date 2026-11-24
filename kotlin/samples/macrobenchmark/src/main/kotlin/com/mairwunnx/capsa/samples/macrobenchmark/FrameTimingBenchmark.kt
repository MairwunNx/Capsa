package com.mairwunnx.capsa.samples.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frames of the demo screens where Capsa works the hardest: fast typing in Search, the ticks of Clock, the refresh of Dashboard.
 *
 * The app runs with its baseline profile, the way users get it from Google Play.
 */
@RunWith(AndroidJUnit4::class)
class FrameTimingBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    /** Debounce and the cancel of the old request on every character must not drop frames. */
    @Test
    fun searchFastTyping() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openSample("Search")
            focusTextField()
        },
    ) {
        typeFast("bananapineapple")
        device.wait(Until.hasObject(By.textContains("pineapple")), LOAD_MS)
    }

    /** A stream ticks every second and the screen redraws the time. */
    @Test
    fun clockTicks() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openSample("Clock")
        },
    ) {
        Thread.sleep(CLOCK_MS)
    }

    /** Two capabilities load at once and fill one UI model. */
    @Test
    fun dashboardRefresh() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = CompilationMode.Partial(),
        startupMode = StartupMode.WARM,
        iterations = ITERATIONS,
        setupBlock = {
            startActivityAndWait()
            openSample("Dashboard")
            device.wait(Until.findObject(By.text("Refresh both").enabled(true)), LOAD_MS)
        },
    ) {
        repeat(REFRESHES) {
            device.wait(Until.findObject(By.text("Refresh both").enabled(true)), LOAD_MS)?.click()
            device.wait(Until.hasObject(By.text("Refresh both").enabled(true)), LOAD_MS)
        }
    }

    private companion object {
        const val CLOCK_MS = 5_000L
        const val REFRESHES = 3

        // The fake network of the demo answers within a few seconds, and Dashboard retries a failed request.
        const val LOAD_MS = 10_000L
    }
}
