package com.mairwunnx.capsa.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.viewmodel.CapsaEffects
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Handles the screen’s effects while its lifecycle stays at [Lifecycle.State.STARTED] or above.
 *
 * Below `STARTED` the function stops collecting. The channel behind `CapsaViewModel.effects` keeps the effects
 * until the next active subscriber, so navigation waits for the screen to come back.
 *
 * [rememberUpdatedState] swaps in the current `handlers` without restarting the subscription on every recomposition.
 *
 * While the screen stays at `STARTED` or above, the runtime counts it as visible and keeps works with `whileVisible` running.
 *
 * A failing handler and an effect without a handler go through the error policy of the view model: a debug build
 * crashes, a release build logs the error and keeps collecting the next effects.
 *
 * @param effects the effects from `CapsaViewModel.effects`
 * @param handlers the effect handlers of the screen
 * @param lifecycleOwner the lifecycle owner that drives the subscription
 */
@Composable
public fun CollectEffects(
    effects: CapsaEffects,
    handlers: EffectHandlers,
    lifecycleOwner: LifecycleOwner = LocalLifecycleOwner.current,
) {
    val currentHandlers by rememberUpdatedState(handlers)
    LaunchedEffect(effects, lifecycleOwner) {
        effects.flow.collectWhileStarted(lifecycleOwner, onVisible = effects::setVisible) { effect ->
            try {
                currentHandlers.handle(effect)
            } catch (e: CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                effects.fail(IllegalStateException("Capsa effect $effect failed on the screen", e))
            }
        }
    }
}

/**
 * Collects effects while the lifecycle stays at [Lifecycle.State.STARTED] or above.
 *
 * `repeatOnLifecycle` restarts the collection every time the lifecycle comes back to the active state.
 *
 * The collection runs on `Dispatchers.Main.immediate`, like the view model that sends the effects. So the handler gets
 * an effect right away, without a trip through the main queue, and a drop below `STARTED` can’t cancel the collection
 * between receiving an effect and handling it.
 *
 * @param lifecycleOwner the lifecycle owner that drives the subscription
 * @param onVisible gets `true` when the lifecycle enters `STARTED` and `false` when it leaves
 * @param onEffect the synchronous effect handler
 */
public suspend fun Flow<Effect>.collectWhileStarted(
    lifecycleOwner: LifecycleOwner,
    onVisible: (Boolean) -> Unit = {},
    onEffect: (Effect) -> Unit,
) {
    lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        onVisible(true)
        try {
            withContext(Dispatchers.Main.immediate) { collect { onEffect(it) } }
        } finally {
            onVisible(false)
        }
    }
}
