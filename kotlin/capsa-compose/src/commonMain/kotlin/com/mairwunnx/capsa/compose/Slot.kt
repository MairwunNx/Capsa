package com.mairwunnx.capsa.compose

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.mairwunnx.capsa.Intent

/**
 * Draws one UI model and sends the user’s [Intent]s.
 *
 * The type gives a slot no way to reach a router, `viewModel()`, a DI container, or a second slice.
 * The `ui` parameter holds the result of a projection, not a slice or a domain model.
 *
 * For example, a like slot gets a ready model and turns clicks into an [Intent]:
 *
 * ```
 * val likeSlot = Slot<LikeUi> { ui, onIntent, modifier ->
 *     LikeButton(
 *         ui = ui,
 *         onClick = { onIntent(LikeClicked) },
 *         modifier = modifier,
 *     )
 * }
 *
 * likeSlot(ui, viewModel::send, Modifier.fillMaxWidth())
 * ```
 */
public fun interface Slot<in U> {

    /**
     * Draws `ui` with the given `modifier` and passes the user’s actions to `onIntent`.
     *
     * @param ui the UI model of the slot
     * @param onIntent receives the user’s actions
     * @param modifier the modifier of the root composable
     */
    @Composable
    public operator fun invoke(ui: U, onIntent: (Intent) -> Unit, modifier: Modifier)

    /**
     * Draws `ui` with an empty [Modifier].
     *
     * The overload keeps a single abstract method, so the interface still supports SAM conversion.
     */
    @Composable
    public operator fun invoke(ui: U, onIntent: (Intent) -> Unit) {
        invoke(ui, onIntent, Modifier)
    }
}
