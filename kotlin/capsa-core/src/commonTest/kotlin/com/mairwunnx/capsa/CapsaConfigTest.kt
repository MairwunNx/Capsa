package com.mairwunnx.capsa

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class CapsaConfigTest {

    @Test
    fun copyChangesOnlyTheGivenSettings() {
        val dispatcher = StandardTestDispatcher()
        val onViolation: (CapsaViolationException) -> Unit = {}
        val observer = object : CapsaObserver {}
        val config = CapsaConfig(maxSignalDepth = 4, onViolation = onViolation, workContext = dispatcher, observer = observer)
        val visibility = CapsaVisibility.Watched(grace = 2.seconds)

        val copy = config.copy(visibility = visibility)

        assertEquals(4, copy.maxSignalDepth)
        assertSame(onViolation, copy.onViolation)
        assertSame(dispatcher, copy.workContext)
        assertSame(observer, copy.observer)
        assertSame(visibility, copy.visibility)
    }
}
