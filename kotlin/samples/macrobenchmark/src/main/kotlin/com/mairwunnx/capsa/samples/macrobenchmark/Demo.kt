package com.mairwunnx.capsa.samples.macrobenchmark

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

/** The package of the demo app in samples/androidApp. */
internal const val PACKAGE = "com.mairwunnx.capsa.samples"

private const val WAIT_MS = 5_000L

/**
 * How many times a macrobenchmark repeats its scenario.
 *
 * The instrumentation argument `androidx.benchmark.iterations` overrides it: without root the phone keeps its clocks free,
 * so tools/device_bench.sh asks for more iterations to even out the noise.
 */
internal const val ITERATIONS = 10

/** Waits for the list of samples on the home screen. */
internal fun MacrobenchmarkScope.awaitHome() {
    check(device.wait(Until.hasObject(By.text("Capsa samples")), WAIT_MS)) { "The home screen didn't show up" }
}

/** Opens the sample with `title` from the home screen, scrolling the list when the card is below the fold. */
internal fun MacrobenchmarkScope.openSample(title: String) {
    awaitHome()
    val card = device.findObject(By.text(title)) ?: run {
        device.findObject(By.scrollable(true)).scrollUntil(Direction.DOWN, Until.findObject(By.text(title)))
    }
    checkNotNull(card) { "No sample \"$title\" on the home screen" }.click()
    check(device.wait(Until.hasObject(By.desc("Back")), WAIT_MS)) { "The sample \"$title\" didn't open" }
}

/** Goes back to the home screen with the arrow in the top bar. */
internal fun MacrobenchmarkScope.goBack() {
    device.findObject(By.desc("Back")).click()
    awaitHome()
}

/** Types `text` one character at a time, as fast as `input` sends them: each character is a new query. */
internal fun MacrobenchmarkScope.typeFast(text: String) {
    text.forEach { device.executeShellCommand("input text $it") }
}

/** Taps the text field of the screen, so the next characters go into it. */
internal fun MacrobenchmarkScope.focusTextField() {
    val field = device.wait(Until.findObject(By.clazz("android.widget.EditText")), WAIT_MS)
    checkNotNull(field) { "No text field on the screen" }.click()
}
