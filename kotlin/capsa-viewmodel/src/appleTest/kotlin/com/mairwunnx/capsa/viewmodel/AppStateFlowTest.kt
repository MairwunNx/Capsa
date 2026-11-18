package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import platform.Foundation.NSNotificationCenter
import kotlin.test.Test
import kotlin.test.assertEquals

/** Checks the notification plumbing of [appStateFlow] with notifications of its own and a fake app state. */
@OptIn(ExperimentalCoroutinesApi::class)
class AppStateFlowTest {

    private val changed = "com.mairwunnx.capsa.test.StateChanged"

    @Test
    fun theInitialStateComesFirstAndEveryNotificationReadsTheStateAgain() = runTest {
        var state: Boolean? = false
        val seen = mutableListOf<Boolean>()
        val flow = appStateFlow(changes = mapOf(changed to { _ -> state }), initial = { state })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { seen += it } }
        testScheduler.runCurrent()

        state = true
        NSNotificationCenter.defaultCenter.postNotificationName(changed, null)
        testScheduler.runCurrent()
        // The same state twice is no change.
        NSNotificationCenter.defaultCenter.postNotificationName(changed, null)
        testScheduler.runCurrent()

        assertEquals(listOf(false, true), seen)
    }

    @Test
    fun anUnknownStateEmitsNothing() = runTest {
        val seen = mutableListOf<Boolean>()
        val flow = appStateFlow(changes = mapOf(changed to { _ -> null }), initial = { null })
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect { seen += it } }
        testScheduler.runCurrent()

        NSNotificationCenter.defaultCenter.postNotificationName(changed, null)
        testScheduler.runCurrent()

        assertEquals(emptyList(), seen)
    }
}
