package com.mairwunnx.capsa.viewmodel

import com.mairwunnx.capsa.CapsaVisibility
import com.mairwunnx.capsa.LoggingCapsaObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.time.Duration.Companion.seconds

class DefaultConfigTest {

    // Compiles the way the KDoc shows it: a subclass changes one setting in its super call, and the default stays for the rest.
    @Suppress("UnusedPrivateClass")
    private class Clock : CapsaViewModel(config = defaultConfig().copy(visibility = CapsaVisibility.Watched(grace = 2.seconds)))

    @AfterTest
    fun reset() {
        CapsaViewModel.resetWorkDispatcher()
    }

    // The test checks the default itself, so it names the dispatcher.
    @Suppress("InjectDispatcher")
    @Test
    fun theDefaultRunsWorksOnTheWorkDispatcherAndWritesTheJournal() {
        val config = CapsaViewModel.defaultConfig()

        assertSame(Dispatchers.Default, config.workContext)
        assertIs<LoggingCapsaObserver>(config.observer)
    }

    @Test
    fun aCopyKeepsTheDispatcherOfTheTest() {
        val dispatcher = StandardTestDispatcher()
        CapsaViewModel.setWorkDispatcher(dispatcher)

        val config = CapsaViewModel.defaultConfig().copy(visibility = CapsaVisibility.Host)

        assertSame(dispatcher, config.workContext)
        assertIs<LoggingCapsaObserver>(config.observer)
        assertSame(CapsaVisibility.Host, config.visibility)
    }
}
