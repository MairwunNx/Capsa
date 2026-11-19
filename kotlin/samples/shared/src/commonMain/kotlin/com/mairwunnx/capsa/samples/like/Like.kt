package com.mairwunnx.capsa.samples.like

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Retry
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work
import kotlin.time.Duration.Companion.milliseconds

data class LikeSlice(val liked: Boolean = false, val likes: Int = 0)

data object LikeClicked : Intent
data class LikeSaved(val likes: Int) : Signal
data object LikeFailed : Signal
data object ShowLikeError : Effect

class LikePorts(val setLiked: suspend (liked: Boolean) -> Int)

private val SAVE = WorkKey("save")

// Three more tries, after 0.5, 1, and 2 seconds. Only then the like rolls back.
private val retry = Retry(times = 3, delay = 500.milliseconds, factor = 2.0)

context(_: CapabilityScope)
fun likes(ports: LikePorts, likes: Int = 0): Capability<LikeSlice> = capability("likes", LikeSlice(likes = likes)) {
    on<LikeClicked> { state, _ ->
        val liked = !state.liked
        // The heart turns red at once. The request is only described here, the runtime runs it.
        state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1).commit(
            work(SAVE, onError = { LikeFailed }, retry = retry) { LikeSaved(ports.setLiked(liked)) },
        )
    }
    on<LikeSaved> { state, fact -> state.copy(likes = fact.likes).commit() }
    on<LikeFailed> { state, _ ->
        state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1).commit(ShowLikeError)
    }
}
