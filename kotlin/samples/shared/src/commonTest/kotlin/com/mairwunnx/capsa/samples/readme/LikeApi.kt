// From the README of Capability Slice Architecture, word for word: https://github.com/MairwunNx/Capability-Slice-Architecture
// When this stops compiling or passing, the README needs the same change.
package com.mairwunnx.capsa.samples.readme

fun interface LikeApi {
    suspend fun setLiked(liked: Boolean): Int
}
