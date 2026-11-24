package com.mairwunnx.capsa.compose

import com.mairwunnx.capsa.CapsaVisibility
import com.mairwunnx.capsa.LoggingCapsaObserver
import kotlinx.coroutines.Dispatchers
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame

class DefaultConfigTest {

    // The test checks the default itself, so it names the dispatcher.
    @Suppress("InjectDispatcher")
    @Test
    fun aCopyKeepsTheDispatcherAndTheJournal() {
        val config = CapsaScope.defaultConfig().copy(visibility = CapsaVisibility.Host)

        assertSame(Dispatchers.Default, config.workContext)
        assertIs<LoggingCapsaObserver>(config.observer)
        assertSame(CapsaVisibility.Host, config.visibility)
    }
}
