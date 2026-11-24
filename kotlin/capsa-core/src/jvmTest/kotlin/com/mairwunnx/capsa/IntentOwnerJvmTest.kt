package com.mairwunnx.capsa

import kotlin.test.Test
import kotlin.test.assertFailsWith

/**
 * The common [IntentOwnerTest] checks two owners of one intent class. On the JVM the runtime compares classes,
 * so it also refuses an owner of a supertype next to an owner of its subtype.
 */
class IntentOwnerJvmTest {

    private sealed interface LikeIntent : Intent
    private data object LikeClicked : LikeIntent

    private val anyLike = capability<Int, LikeIntent, Nothing>(name = "anyLike", initial = 0) {
        onIntent<LikeIntent> { state, _ -> Decision(state = state + 1) }
    }

    private val clicks = capability<Int, LikeIntent, Nothing>(name = "clicks", initial = 0) {
        onIntent<LikeClicked> { state, _ -> Decision(state = state + 1) }
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun ownersOfASupertypeAndItsSubtypeFailTheInstallInEitherOrder() {
        for ((first, second) in listOf(anyLike to clicks, clicks to anyLike)) {
            val builder = CapsaBuilder()
            builder.install(first)

            assertFailsWith<IllegalArgumentException> { builder.install(second) }
        }
    }
}
