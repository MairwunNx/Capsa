package com.mairwunnx.capsa.samples.like

import com.mairwunnx.capsa.samples.backend.NetworkException
import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import com.mairwunnx.capsa.test.replay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LikeTest {

    private fun likesWith(setLiked: suspend (Boolean) -> Int) = inCapabilityScope { likes(LikePorts(setLiked), likes = 128) }

    @Test
    fun `a click shows the like at once`() {
        val replay = likesWith { 129 }.replay(LikeClicked)

        assertEquals(LikeSlice(liked = true, likes = 129), replay.state)
        assertEquals(1, replay.works.size)
    }

    @Test
    fun `the count of the server wins`() = runTest {
        val likes = likesWith { 200 }
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()

        assertEquals(LikeSlice(liked = true, likes = 200), harness.stateOf(likes))
        assertTrue(harness.effects.isEmpty())
    }

    @Test
    fun `a failing save retries with backoff and then rolls back`() = runTest {
        var calls = 0
        val likes = likesWith {
            calls++
            throw NetworkException()
        }
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()
        assertEquals(LikeSlice(liked = true, likes = 129), harness.stateOf(likes))
        assertEquals(1, calls)

        harness.advanceTimeBy(500.milliseconds)
        assertEquals(2, calls)
        harness.advanceTimeBy(1.seconds)
        assertEquals(3, calls)
        harness.advanceTimeBy(2.seconds)
        assertEquals(4, calls)

        assertEquals(LikeSlice(liked = false, likes = 128), harness.stateOf(likes))
        assertEquals(listOf(ShowLikeError), harness.effects)
    }
}
