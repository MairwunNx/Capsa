// From the Work as data page of the Capability Slice Architecture wiki, word for word:
// https://github.com/MairwunNx/Capability-Slice-Architecture/wiki/Work-as-data#the-honest-optimistic-like
// When this stops compiling or passing, the wiki needs the same change.
package com.mairwunnx.capsa.samples.wiki

import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work

data class Like(val liked: Boolean = false, val likes: Int = 0)
data class LikeSlice(val shown: Like = Like(), val confirmed: Like = Like())

data object LikeClicked : Intent
data class LikeSettled(val like: Like) : Signal // what the server holds now
data object LikeFailed : Signal
data object LikeUnknown : Signal
data object ShowLikeError : Effect

fun interface LikeApi {
    suspend fun setLiked(liked: Boolean): Like
}
fun interface LikeReader {
    suspend fun current(): Like
}

private val SAVE = WorkKey("save")

context(_: CapabilityScope)
fun likes(api: LikeApi, reader: LikeReader) = capability("likes", LikeSlice()) {
    on<LikeClicked> { state, _ ->
        val liked = !state.shown.liked
        val shown = Like(liked, state.shown.likes + if (liked) 1 else -1)
        state.copy(shown = shown).commit(
            work(SAVE, onError = { LikeFailed }) { LikeSettled(api.setLiked(liked)) },
        )
    }
    on<LikeSettled> { _, fact -> LikeSlice(shown = fact.like, confirmed = fact.like).commit() }
    on<LikeFailed> { state, _ ->
        // A cancelled request may have reached the server, so ask it instead of guessing.
        state.copy(shown = state.confirmed).commit(
            effects = listOf(ShowLikeError),
            works = listOf(work(SAVE, onError = { LikeUnknown }) { LikeSettled(reader.current()) }),
        )
    }
    on<LikeUnknown> { state, _ -> state.commit() }
}
