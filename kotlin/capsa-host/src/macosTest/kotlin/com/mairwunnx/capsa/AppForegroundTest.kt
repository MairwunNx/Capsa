package com.mairwunnx.capsa

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.AppKit.NSApplicationDidChangeOcclusionStateNotification
import platform.Foundation.NSNotificationCenter
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A test binary has no running app, so [appForeground] can’t read its windows. It must stay silent then,
 * and the app counts as being in the foreground. [AppStateFlowTest] checks the plumbing with a fake state.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AppForegroundTest {

    @Test
    fun withoutARunningAppTheLayerStaysSilent() = runTest {
        val seen = mutableListOf<Boolean>()
        val foreground = checkNotNull(appForeground())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { foreground.collect { seen += it } }
        testScheduler.runCurrent()

        NSNotificationCenter.defaultCenter.postNotificationName(NSApplicationDidChangeOcclusionStateNotification, null)
        testScheduler.runCurrent()

        assertEquals(emptyList(), seen)
    }
}
