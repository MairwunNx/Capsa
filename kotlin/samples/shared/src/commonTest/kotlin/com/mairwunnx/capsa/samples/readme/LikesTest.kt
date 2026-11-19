// From the README of Capability Slice Architecture, word for word: https://github.com/MairwunNx/Capability-Slice-Architecture
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.test.capsaHarness
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LikesTest {

    @Test
    fun `a failed save rolls the like back`() = runTest {
        val likes = likes(api = { error("offline") })
        val harness = capsaHarness(likes)

        harness.append(LikeClicked)
        harness.runCurrent()

        assertEquals(LikeSlice(liked = false, likes = 0), harness.stateOf(likes))
        assertEquals(listOf(ShowLikeError), harness.effects)
    }
}
