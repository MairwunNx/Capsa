// Checks the honest optimistic like from the Work as data page of the Capability Slice Architecture wiki:
// https://github.com/MairwunNx/Capability-Slice-Architecture/wiki/Work-as-data#the-honest-optimistic-like
package com.mairwunnx.capsa.samples.wiki

import com.mairwunnx.capsa.test.capsaHarness
import com.mairwunnx.capsa.test.inCapabilityScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class HonestLikeTest {

    @Test
    fun `a saved like is shown and confirmed`() = runTest {
        val likes = inCapabilityScope { likes(api = { liked -> Like(liked, 1) }, reader = { error("not called") }) }
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()

        assertEquals(LikeSlice(shown = Like(true, 1), confirmed = Like(true, 1)), harness.stateOf(likes))
        assertTrue(harness.effects.isEmpty())
    }

    @Test
    fun `a failed second tap asks the server and shows the like that landed`() = runTest {
        var server = Like()
        val api = LikeApi { liked ->
            if (!liked) error("offline")
            // The first request reaches the server and is cancelled on the client before it answers.
            server = Like(liked = true, likes = 1)
            delay(1.seconds)
            server
        }
        val likes = inCapabilityScope { likes(api, reader = { server }) }
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()
        harness.append(LikeClicked)
        harness.runCurrent()
        harness.advanceTimeBy(2.seconds)

        assertEquals(LikeSlice(shown = Like(true, 1), confirmed = Like(true, 1)), harness.stateOf(likes))
        assertEquals(listOf(ShowLikeError), harness.effects)
    }

    @Test
    fun `when the read fails too the last confirmed like stays`() = runTest {
        val likes = inCapabilityScope { likes(api = { error("offline") }, reader = { error("offline") }) }
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()

        assertEquals(LikeSlice(), harness.stateOf(likes))
        assertEquals(listOf(ShowLikeError), harness.effects)
    }
}
