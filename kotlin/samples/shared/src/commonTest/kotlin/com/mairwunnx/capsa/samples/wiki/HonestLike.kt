// From the Work as data page of the Capability Slice Architecture wiki, word for word:
// https://github.com/MairwunNx/Capability-Slice-Architecture/wiki/Work-as-data#the-honest-optimistic-like
// When this stops compiling or passing, the wiki needs the same change.
package com.mairwunnx.capsa.samples.wiki

import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.capability

data class Like(val liked: Boolean = false, val likes: Int = 0)
data class LikeSlice(val shown: Like = Like(), val confirmed: Like = Like())

sealed interface LikeIntent : Intent
data object LikeClicked : LikeIntent

sealed interface LikeSignal : Signal
data class LikeSettled(val like: Like) : LikeSignal // what the server holds now
data object LikeFailed : LikeSignal
data object LikeUnknown : LikeSignal

data object ShowLikeError : Effect

fun interface LikeApi {
    suspend fun setLiked(liked: Boolean): Like
}
fun interface LikeReader {
    suspend fun current(): Like
}

private val SAVE = WorkKey("save")

context(_: CapabilityScope)
fun likes(api: LikeApi, reader: LikeReader) = capability<LikeSlice, LikeIntent, LikeSignal>(name = "likes", initial = LikeSlice()) {
    val works = object {
        fun save(liked: Boolean) = work(key = SAVE, onError = { LikeFailed }) { LikeSettled(api.setLiked(liked)) }
        fun read() = work(key = SAVE, onError = { LikeUnknown }) { LikeSettled(reader.current()) }
    }

    onIntent<LikeClicked> { state, _ ->
        val liked = !state.shown.liked
        val shown = Like(liked, state.shown.likes + if (liked) 1 else -1)
        Decision(state = state.copy(shown = shown), work = works.save(liked))
    }
    onSignal<LikeSettled> { _, settled -> Decision(state = LikeSlice(shown = settled.like, confirmed = settled.like)) }
    // A cancelled request may have reached the server, so ask it instead of guessing.
    onSignal<LikeFailed> { state, _ -> Decision(state = state.copy(shown = state.confirmed), effect = ShowLikeError, work = works.read()) }
    onSignal<LikeUnknown> { state, _ -> Decision(state = state) }
}
