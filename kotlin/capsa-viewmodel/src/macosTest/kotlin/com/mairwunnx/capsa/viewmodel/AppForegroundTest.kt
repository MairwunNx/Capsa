package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.AppKit.NSApplicationDidHideNotification
import platform.AppKit.NSApplicationDidUnhideNotification
import platform.Foundation.NSNotificationCenter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Posts the real notifications of the app and checks what [appForeground] reports. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppForegroundTest {

    @Test
    fun theNotificationsOfTheAppMoveItBetweenForegroundAndBackground() = runTest {
        val seen = mutableListOf<Boolean>()
        val foreground = checkNotNull(appForeground())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { foreground.collect { seen += it } }
        testScheduler.runCurrent()

        val center = NSNotificationCenter.defaultCenter
        center.postNotificationName(NSApplicationDidHideNotification, null)
        testScheduler.runCurrent()
        center.postNotificationName(NSApplicationDidUnhideNotification, null)
        testScheduler.runCurrent()

        assertEquals(listOf(false, true), seen)
    }
}
