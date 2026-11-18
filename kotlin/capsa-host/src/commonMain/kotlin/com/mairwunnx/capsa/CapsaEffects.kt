package com.mairwunnx.capsa

import kotlinx.coroutines.flow.Flow

/**
 * The effects of a screen together with the error policy for their handlers.
 *
 * The screen passes this object to `CollectEffects` instead of the host itself. A host without Compose,
 * such as SwiftUI, collects [flow] and calls [fail] on its own.
 *
 * @property flow the effects that the host didn’t take. While someone collects them, the runtime counts
 * the screen as visible
 */
public class CapsaEffects @InternalCapsaApi constructor(
    public val flow: Flow<Effect>,
    private val onFail: (Throwable) -> Unit,
) {

    /**
     * Hands `error` of a screen’s effect handler to the error policy of Capsa: a crash with [Capsa.failFast] on,
     * an error in the log otherwise. The effects keep coming either way.
     *
     * @param error what the handler threw, or an error about an effect without a handler
     */
    public fun fail(error: Throwable) {
        onFail(error)
    }
}
