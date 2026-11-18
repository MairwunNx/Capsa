package com.mairwunnx.capsa.compose

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.mairwunnx.capsa.Effect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class CollectEffectsTest {

    private data class Navigate(val id: Int) : Effect

    private class TestOwner : LifecycleOwner {
        override val lifecycle: LifecycleRegistry = LifecycleRegistry.createUnsafe(this)
    }

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun effectsAreDeliveredWhileStartedAndWaitInTheChannelBelowIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = TestOwner().apply { lifecycle.currentState = Lifecycle.State.STARTED }
        val channel = Channel<Effect>(Channel.UNLIMITED)
        val delivered = mutableListOf<Effect>()
        backgroundScope.launch {
            channel.receiveAsFlow().collectWhileStarted(owner) { delivered += it }
        }
        testScheduler.runCurrent()

        channel.trySend(Navigate(1))
        testScheduler.runCurrent()
        assertEquals(listOf<Effect>(Navigate(1)), delivered)

        owner.lifecycle.currentState = Lifecycle.State.CREATED
        testScheduler.runCurrent()
        channel.trySend(Navigate(2))
        testScheduler.runCurrent()
        assertEquals(listOf<Effect>(Navigate(1)), delivered)

        owner.lifecycle.currentState = Lifecycle.State.STARTED
        testScheduler.runCurrent()
        assertEquals(listOf<Effect>(Navigate(1), Navigate(2)), delivered)
    }
}
