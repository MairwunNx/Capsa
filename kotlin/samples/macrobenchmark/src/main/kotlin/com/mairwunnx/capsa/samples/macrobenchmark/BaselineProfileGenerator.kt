package com.mairwunnx.capsa.samples.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through the demo app to collect the hot paths of Capsa: facts, decisions, works, streams, effects, saved state, slots.
 *
 * `./gradlew :samples:androidApp:generateBaselineProfile` runs it. tools/device_bench.sh then takes the rules of Capsa
 * out of the app profile and writes them into capsa-core, capsa-host, capsa-viewmodel, and capsa-compose.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()

        openSample("Counter")
        repeat(TAPS) { device.findObject(By.text("+1"))?.click() }
        goBack()

        openSample("Search")
        focusTextField()
        typeFast("apple")
        device.wait(Until.hasObject(By.textContains("apple")), WAIT_MS)
        goBack()

        openSample("Like")
        repeat(TAPS) { device.findObject(By.desc("Like"))?.click() }
        goBack()

        openSample("Clock")
        Thread.sleep(WAIT_MS)
        goBack()

        openSample("Profile form")
        focusTextField()
        typeFast("Ann")
        device.findObject(By.text("Save"))?.click()
        goBack()

        openSample("Dashboard")
        device.wait(Until.findObject(By.text("Refresh both").enabled(true)), WAIT_MS)?.click()
        device.wait(Until.hasObject(By.text("Refresh both").enabled(true)), WAIT_MS)
        goBack()

        openSample("Two screens")
        device.wait(Until.hasObject(By.clickable(true).hasDescendant(By.clazz("android.widget.TextView"))), WAIT_MS)
        device.findObjects(By.clickable(true)).firstOrNull { it.contentDescription != "Back" }?.click()
        device.wait(Until.findObject(By.text("Star it")), WAIT_MS)?.click()
        device.findObject(By.desc("Back"))?.click()
        goBack()
    }

    private companion object {
        const val TAPS = 3
        const val WAIT_MS = 5_000L
    }
}
