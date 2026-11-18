package com.mairwunnx.capsa.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.currentCompositeKeyHashCode
import androidx.compose.runtime.retain.RetainObserver
import androidx.compose.runtime.retain.retain
import androidx.compose.runtime.saveable.LocalSaveableStateRegistry
import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Creates the runtime of a screen for this place in the composition and keeps it as long as the screen lives.
 *
 * The lambda runs once, with [CapsaScope] as the receiver. It either returns a [CapsaComposable]:
 *
 * ```
 * val like = rememberCapsa { LikeCapsule(this, app) }
 * ```
 *
 * or installs the capabilities itself and ends with [CapsaScope.expose], which gives a [CapsaHandle]:
 *
 * ```
 * val like = rememberCapsa {
 *     val like = installSaved(likes(likePorts), LikeSaver, key = "like")
 *     handleEffects { on<LikeTracked> { analytics.log(it) } }
 *     expose(like.mapState(capsaScope) { it.toUi() })
 * }
 * val ui by like.state.collectAsStateWithLifecycle()
 * ```
 *
 * What the lambda reads is fixed at that moment, the same as with `viewModel { }`: pass a value that should create
 * a new runtime to `keys`, for example the id of a product.
 *
 * Everything else connects by itself:
 *
 * - **Rotation.** `retain` keeps the runtime through a configuration change on Android. iOS, macOS, and the desktop
 *   don’t recreate the composition on rotation or resize.
 * - **Death of the process.** The snapshots of `installSaved` go to [LocalSaveableStateRegistry]
 *   and come back when the screen is created again.
 * - **The end of the screen.** When Compose retires the runtime, its scope ends, and every work stops.
 *   New `keys` retire the old runtime too.
 * - **Visibility** follows the collectors of the screen, see [CapsaComposable].
 *
 * The runtime lives as long as the `RetainedValuesStore` around it keeps it. Without one of its own the screen keeps
 * the runtime while it stays in the composition: a screen on the back stack loses it, the works stop, and the saved
 * slices come back on return. A navigation that wraps the content of each entry in a store of its own keeps the runtime
 * while the entry is on the back stack. Navigation 3, for example, does it with a `NavEntryDecorator`
 * over `RetainedValuesStoreRegistry`.
 *
 * @param keys a change of any key creates a new runtime and retires the old one
 * @param logger the logger for failing effect handlers and the runtime journal
 * @param config the runtime settings. By default works run on `Dispatchers.Default`, and the journal goes to `logger`
 * @param create returns a [CapsaComposable] or the result of [CapsaScope.expose]
 * @return the same object on every recomposition
 * @throws IllegalStateException if `create` returns something else
 */
@Composable
public fun <R : Any> rememberCapsa(
    vararg keys: Any?,
    logger: CapsaLogger = CapsaLogger.Default,
    config: CapsaConfig = defaultCapsaConfig(logger),
    create: CapsaScope.() -> R,
): R {
    val registry = LocalSaveableStateRegistry.current
    // The place in the composition, the same key that rememberSaveable takes. Common code sees an expect type with toString() only.
    val saveKey = "capsa:" + currentCompositeKeyHashCode.toString()
    val retained = retain(*keys) {
        val snapshots = ComposeSnapshots(registry?.consumeRestored(saveKey) as? SavedState)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val capsa = try {
            CapsaScope(scope, logger, config, snapshots).create().also {
                check(it is CapsaComposable || it is CapsaHandle<*>) {
                    "rememberCapsa returned ${it::class.simpleName}. Return a CapsaComposable, or end the lambda with expose()."
                }
            }
        } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
            scope.cancel()
            throw e
        }
        RetainedCapsa(capsa, scope, snapshots)
    }
    // A rotation brings a new registry with the new activity, while the object stays: register with every registry anew.
    DisposableEffect(retained, registry, saveKey) {
        val entry = if (registry != null && retained.snapshots.isNotEmpty()) registry.registerProvider(saveKey) { retained.snapshots.save() } else null
        onDispose { entry?.unregister() }
    }
    return retained.capsa
}

/** Ends the scope of the runtime when Compose no longer keeps it. */
private class RetainedCapsa<R : Any>(val capsa: R, private val scope: CoroutineScope, val snapshots: ComposeSnapshots) : RetainObserver {
    override fun onRetained() = Unit

    override fun onEnteredComposition() = Unit

    override fun onExitedComposition() = Unit

    override fun onRetired() {
        scope.cancel()
    }

    override fun onUnused() {
        scope.cancel()
    }
}

/** Keeps every snapshot of one screen in one entry of the saveable state registry, under the key of `installSaved`. */
private class ComposeSnapshots(private val restored: SavedState?) : SnapshotStore {
    private val providers = LinkedHashMap<String, () -> SavedState>()

    override fun restored(key: String): SavedState? = restored?.read { if (contains(key)) getSavedState(key) else null }

    override fun register(key: String, provide: () -> SavedState) {
        providers[key] = provide
    }

    fun isNotEmpty(): Boolean = providers.isNotEmpty()

    fun save(): SavedState = savedState { providers.forEach { (key, provide) -> putSavedState(key, provide()) } }
}
