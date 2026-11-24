package com.mairwunnx.capsa

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Checks that an intent has one owner on a screen, while a signal reaches every capability that handles it. */
class IntentOwnerTest {

    private sealed interface LikeIntent : Intent
    private data object LikeClicked : LikeIntent

    private sealed interface LikeSignal : Signal
    private data object LikeChanged : LikeSignal

    @Test
    @Suppress("IgnoredReturnValue")
    fun twoCapabilitiesHandlingOneIntentFailTheInstall() {
        val likes = capability<Boolean, LikeIntent, Nothing>(name = "likes", initial = false) {
            onIntent<LikeClicked> { state, _ -> Decision(state = !state) }
        }
        val counter = capability<Int, LikeIntent, Nothing>(name = "counter", initial = 0) {
            onIntent<LikeClicked> { state, _ -> Decision(state = state + 1) }
        }
        val builder = CapsaBuilder()
        builder.install(likes)

        val error = assertFailsWith<IllegalArgumentException> { builder.install(counter) }

        assertTrue("likes" in error.message.orEmpty())
        assertTrue("counter" in error.message.orEmpty())
    }

    @Test
    fun theOwnerSendsASignalAndEveryNeighbourReceivesIt() = runTest {
        val likes = capability<Boolean, LikeIntent, LikeSignal>(name = "likes", initial = false) {
            onIntent<LikeClicked> { state, _ -> Decision(state = !state, signal = LikeChanged) }
        }
        fun counter(name: String) = capability<Int, Nothing, Nothing>(name = name, initial = 0) {
            onSignal<LikeChanged> { state, _ -> Decision(state = state + 1) }
        }
        val builder = CapsaBuilder()
        val liked = builder.install(likes)
        val first = builder.install(counter("first"))
        val second = builder.install(counter("second"))
        val runtime = builder.build(backgroundScope, CapsaConfig(visibility = CapsaVisibility.Host))

        runtime.append(LikeClicked)
        testScheduler.runCurrent()

        assertEquals(true, liked.value)
        assertEquals(1, first.value)
        assertEquals(1, second.value)
    }
}
