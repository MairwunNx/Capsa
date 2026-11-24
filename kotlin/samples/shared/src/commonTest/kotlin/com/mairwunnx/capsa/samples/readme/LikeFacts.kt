// From the README of Capability Slice Architecture, word for word: https://github.com/MairwunNx/Capability-Slice-Architecture
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal

sealed interface LikeIntent : Intent
data object LikeClicked : LikeIntent

sealed interface LikeSignal : Signal
data class LikeSaved(val likes: Int) : LikeSignal
data object LikeFailed : LikeSignal

data object ShowLikeError : Effect
