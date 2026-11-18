package com.mairwunnx.capsa.compose

import com.mairwunnx.capsa.CapsaEffects
import com.mairwunnx.capsa.CapsaHostCore
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal

/**
 * What a screen without a class gets from [rememberCapsa]: the state it exposed and the way back to the runtime.
 *
 * @param T what the lambda passed to [CapsaScope.expose]
 * @property state what the screen reads, for example a projection to collect
 */
public class CapsaHandle<out T> internal constructor(
    public val state: T,
    private val core: CapsaHostCore,
) {

    /** Passes a user `intent` to the runtime. */
    public fun send(intent: Intent) {
        core.send(intent)
    }

    /** Passes an outside `signal` to the runtime, such as the result of another screen. */
    public fun signal(signal: Signal) {
        core.signal(signal)
    }

    /**
     * The effects for the screen. The first access starts the runtime.
     *
     * The channel keeps effects until someone subscribes and serves a single consumer. The collector of
     * [CapsaEffects.flow] makes the screen visible, like a collector of a slice.
     */
    public val effects: CapsaEffects
        get() = core.effects
}
