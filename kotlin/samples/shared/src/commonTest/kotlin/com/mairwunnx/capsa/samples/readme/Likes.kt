// From the README of Capability Slice Architecture, word for word: https://github.com/MairwunNx/Capability-Slice-Architecture
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability

private val SAVE = WorkKey("save")

// Only a host or a test harness provides this scope.
context(_: CapabilityScope)
fun likes(api: LikeApi) = capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) {
    val works = object {
        fun save(liked: Boolean) = work(key = SAVE, onError = { LikeFailed }) { LikeSaved(api.setLiked(liked)) }
    }

    onIntent<LikeClicked> { state, _ ->
        val liked = !state.liked
        // The heart turns red at once. The request is only described here: the runtime runs it.
        Decision(state = state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1), work = works.save(liked))
    }
    onSignal<LikeSaved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
    onSignal<LikeFailed> { state, _ ->
        Decision(state = state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1), effect = ShowLikeError)
    }
}
