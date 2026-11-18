package com.mairwunnx.capsa.compose

import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaEffects
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.CapsaVisibility
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.SliceSaver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * Hosts the capabilities of a screen right in the composition, without a view model, on Android, iOS, macOS, and the JVM.
 *
 * A subclass installs capabilities with [install] or [installSaved], builds the UI state, and, if it needs to, registers
 * handlers in [handleEffects], the same as `CapsaViewModel` from `capsa-viewmodel`. The constructor takes the [CapsaScope]
 * that [rememberCapsa] passes, and the class builds the rest from what it gets, here the Android `Application`:
 *
 * ```
 * class LikeCapsule(scope: CapsaScope, app: Application) : CapsaComposable(scope) {
 *     private val likesApi = LikesApi(app)
 *     private val analytics = Analytics(app)
 *
 *     private val like: StateFlow<LikeSlice> = installSaved(likes(LikePorts(likesApi::setLiked)), LikeSaver, key = "like")
 *     val ui: StateFlow<LikeUi> = like.mapState(capsaScope) { it.toUi() }
 *
 *     override fun EffectHandlers.Builder.handleEffects() {
 *         on<LikeTracked> { analytics.log(it) }
 *     }
 * }
 *
 * @Composable
 * fun LikeScreen(onLikeError: () -> Unit) {
 *     val app = LocalContext.current.applicationContext as Application
 *     val like = rememberCapsa { LikeCapsule(this, app) }
 *     val ui by like.ui.collectAsStateWithLifecycle()
 *     CollectEffects(like.effects, effectHandlers { on<ShowLikeError> { onLikeError() } })
 *     LikeContent(ui, onIntent = like::send)
 * }
 * ```
 *
 * A screen without a class installs right in the lambda of [rememberCapsa], see [CapsaScope].
 *
 * The runtime starts on the main thread once the subclass constructor ends, or earlier on the first [send] or the first
 * access to [effects]. Decisions run on `Dispatchers.Main.immediate`, works on `Dispatchers.Default`.
 *
 * ### How long it lives
 *
 * [rememberCapsa] keeps the object with `retain` from Compose. A rotation on Android doesn’t recreate it, and the works
 * keep running. When the screen leaves the composition for good, the scope ends, and with it the runtime and its works.
 * A screen on the back stack lives as long as the navigation keeps a `RetainedValuesStore` for it, see [rememberCapsa].
 *
 * The object outlives the activity on Android, so it must not hold the activity or its `Context`:
 * pass the `Application` instead, as with a view model.
 *
 * ### When the screen is visible
 *
 * The same as with a view model: the screen is visible while someone collects a slice, a projection, or [effects],
 * and the app is in the foreground. `collectAsStateWithLifecycle()` and `CollectEffects` stop below `STARTED`.
 * A rotation doesn’t restart the works within [CapsaVisibility.Watched.grace].
 *
 * Uncaught errors of the runtime, of projections, of snapshots, and of the effect handlers go through [capsaScope]:
 * with [Capsa.failFast] on they crash the app, otherwise they go to [logger].
 *
 * @param scope what [rememberCapsa] passes: the lifetime, the logger, the config, and where snapshots go
 */
@Suppress("AbstractClassCanBeConcreteClass") // A screen extends it, nobody creates it as is.
public abstract class CapsaComposable(scope: CapsaScope) : CapabilityScope {

    /** The logger for failing effect handlers and the runtime journal. */
    protected val logger: CapsaLogger = scope.logger

    private val context = scope
    private val core = scope.core

    /**
     * The scope of the runtime and of projections, with the error policy of Capsa.
     *
     * Pass it to `mapState` and `combineState`. It ends together with the object.
     */
    protected val capsaScope: CoroutineScope = core.capsaScope

    init {
        scope.attach(this, handleEffects = { handleEffects() }, onEffectFailed = { effect, error -> onEffectFailed(effect, error) })
        core.startAfterConstructor()
    }

    /** Passes a user `intent` to the runtime. */
    public fun send(intent: Intent) {
        core.send(intent)
    }

    /** Passes an outside `signal` to the runtime, such as the result of another screen. */
    protected fun signal(signal: Signal) {
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

    /**
     * Registers the effects that the object handles without the screen, such as analytics.
     *
     * Effects without a matching handler go to [effects].
     */
    protected open fun EffectHandlers.Builder.handleEffects(): Unit = Unit

    /**
     * Installs `capability` into the runtime and returns the flow of its state.
     *
     * Call it while the subclass initializes, before [send] and before the first access to [effects].
     *
     * @param capability a capability of the screen
     * @return a read-only flow of the state
     * @throws IllegalStateException if the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    protected fun <S : Any> install(capability: Capability<S>): StateFlow<S> = core.install(capability)

    /**
     * Installs `capability` and keeps the chosen part of its state through the death of the process.
     *
     * The snapshot goes to the saveable state registry of the composition, the same one that `rememberSaveable` uses.
     * `saver` restores the snapshot before the capability is installed and runs again only when the platform saves
     * the screen. Without a registry, for example in a test without UI, the capability works and nothing is saved.
     *
     * @param capability a capability of the screen
     * @param saver turns the state into a snapshot and back
     * @param key the key of the snapshot, unique within the object
     * @return a read-only flow of the state
     * @throws IllegalStateException if the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    protected fun <S : Any, P : Any> installSaved(capability: Capability<S>, saver: SliceSaver<S, P>, key: String): StateFlow<S> =
        context.installSavedFor(capability, saver, key)

    /**
     * Handles `error` that the handler of `effect` threw.
     *
     * By default it hands the error to the error policy of Capsa: a crash with [Capsa.failFast] on, an error in the log otherwise.
     * An override can turn it into `signal(...)`, so a capability shows the error to the user.
     *
     * @param effect the effect whose handler failed
     * @param error the error of the handler
     */
    protected open suspend fun onEffectFailed(effect: Effect, error: Exception) {
        core.fail(IllegalStateException("Capsa effect $effect failed in the CapsaComposable", error))
    }
}
