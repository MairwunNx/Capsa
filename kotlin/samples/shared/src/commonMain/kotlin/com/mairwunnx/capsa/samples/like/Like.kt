package com.mairwunnx.capsa.samples.like

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Retry
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import kotlin.time.Duration.Companion.milliseconds

data class LikeSlice(val liked: Boolean = false, val likes: Int = 0)

sealed interface LikeIntent : Intent
data object LikeClicked : LikeIntent

sealed interface LikeSignal : Signal
data class LikeSaved(val likes: Int) : LikeSignal
data object LikeFailed : LikeSignal

data object ShowLikeError : Effect

class LikePorts(val setLiked: suspend (liked: Boolean) -> Int)

private val SAVE = WorkKey("save")

// Three more tries, after 0.5, 1, and 2 seconds. Only then the like rolls back.
private val retry = Retry(times = 3, delay = 500.milliseconds, factor = 2.0)

context(_: CapabilityScope)
fun likes(ports: LikePorts, likes: Int = 0): Capability<LikeSlice> =
    capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice(likes = likes)) {
        val works = object {
            fun save(liked: Boolean) = work(key = SAVE, onError = { LikeFailed }, retry = retry) { LikeSaved(ports.setLiked(liked)) }
        }

        onIntent<LikeClicked> { state, _ ->
            val liked = !state.liked
            // The heart turns red at once. The request is only described here: the runtime runs it.
            Decision(
                state = state.copy(liked = liked, likes = state.likes + if (liked) 1 else -1),
                work = works.save(liked),
            )
        }
        onSignal<LikeSaved> { state, saved -> Decision(state = state.copy(likes = saved.likes)) }
        onSignal<LikeFailed> { state, _ ->
            Decision(
                state = state.copy(liked = !state.liked, likes = state.likes + if (state.liked) -1 else 1),
                effect = ShowLikeError,
            )
        }
    }
