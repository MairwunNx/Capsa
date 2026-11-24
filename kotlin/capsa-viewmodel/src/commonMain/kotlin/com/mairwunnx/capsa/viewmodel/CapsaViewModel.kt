package com.mairwunnx.capsa.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaEffects
import com.mairwunnx.capsa.CapsaHostCore
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.CapsaVisibility
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.LoggingCapsaObserver
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.SliceSaver
import com.mairwunnx.capsa.SnapshotStore
import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * Hosts the capabilities of a screen in an AndroidX [ViewModel], on Android, iOS, and the JVM.
 *
 * A subclass installs capabilities with [install] or [installSaved], builds the UI state, and, if it needs to, registers handlers
 * in [handleEffects]. The runtime starts when the subclass constructor ends, or earlier on the first [send]
 * or the first access to [effects]. On the JVM without a main dispatcher, only [send] or [effects] start it.
 * `capsa-viewmodel` doesn’t bring one: a desktop app gets it from `capsa-compose` or adds `kotlinx-coroutines-swing` itself,
 * and a test sets it with `Dispatchers.setMain`.
 *
 * The view model is the [CapabilityScope], so it creates its capabilities itself from their ports:
 *
 * ```
 * class LikeViewModel(likePorts: LikePorts, private val analytics: Analytics) : CapsaViewModel() {
 *     val like: StateFlow<LikeSlice> = install(likes(likePorts))
 *
 *     override fun EffectHandlers.Builder.handleEffects() {
 *         on<LikeTracked> { analytics.log(it) }
 *     }
 * }
 * ```
 *
 * [handleEffects] takes the service effects, such as analytics. The other effects go to [effects] for the screen.
 * `CollectEffects` from `capsa-compose` throws if the screen has no handler for an effect.
 *
 * A failing handler goes to [onEffectFailed] and doesn’t stop the pipeline: by default a debug build crashes
 * and a release build logs the error, the same as with the handlers on the screen. In tests,
 * `StandardTestDispatcher` holds the start of the runtime until the constructor ends,
 * while `UnconfinedTestDispatcher` can start it earlier.
 *
 * The default config runs works on `Dispatchers.Default`, and decisions stay on the thread of `viewModelScope`.
 * [LoggingCapsaObserver] writes the runtime journal. A test replaces the work dispatcher with [setWorkDispatcher].
 *
 * ### When the screen is visible
 *
 * The runtime pauses works with `whileVisible` while the screen is hidden, and the view model needs no call for it.
 * The screen is visible while someone collects a slice, a projection, or [effects], and the app is in the foreground.
 * `collectAsStateWithLifecycle()` and `CollectEffects` stop collecting below `STARTED`, so the back stack and
 * the background hide the screen. A rotation doesn’t restart the works: the new screen starts collecting within
 * [CapsaVisibility.Watched.grace], one second by default. The foreground comes from `ProcessLifecycleOwner` on Android,
 * from the app state on iOS, and from the windows of the app on macOS. It also covers `collectAsState()`, which keeps collecting in the background.
 *
 * Read slices inside the view model through `.value` or build projections with `mapState` and `combineState`:
 * they don’t count as collectors. Your own `stateIn(viewModelScope, SharingStarted.Eagerly)` or `launch { slice.collect {} }`
 * over a slice is a collector that never leaves, so the screen stays visible for good. `SharingStarted.WhileSubscribed`
 * passes the collectors of the UI through. A test without UI collects the state, for example in `backgroundScope`,
 * otherwise works with `whileVisible` don’t start.
 *
 * Uncaught errors of the runtime, of projections, of snapshots, and of the screen’s effect handlers go through [capsaScope].
 * With [Capsa.failFast] on, they crash the app, so a bug shows up at once. Otherwise they go to [logger] as errors,
 * and the screen keeps working on its last valid state.
 *
 * ![A failing decide crashes a debug build and leaves a release build running](https://raw.githubusercontent.com/MairwunNx/Capsa/master/docs/kdoc/crash-free.gif)
 *
 * [See the animation](https://raw.githubusercontent.com/MairwunNx/Capsa/master/docs/kdoc/crash-free.gif)
 *
 * @param logger the logger for failing effect handlers and the runtime journal
 * @param savedStateHandle the store of snapshots for [installSaved], or `null` if the screen saves nothing
 * @param config the runtime settings, [defaultConfig] unless you pass your own. To change one setting,
 * pass `defaultConfig(logger).copy(…)`: a config built from scratch drops the work dispatcher and the journal
 */
@Suppress("AbstractClassCanBeConcreteClass") // A screen extends it, nobody creates it as is.
public abstract class CapsaViewModel(
    protected val logger: CapsaLogger = CapsaLogger.Default,
    savedStateHandle: SavedStateHandle? = null,
    config: CapsaConfig = defaultConfig(logger),
) : ViewModel(),
    CapabilityScope {

    private val core = CapsaHostCore(
        scope = viewModelScope,
        logger = logger,
        config = config,
        handleEffects = { handleEffects() },
        onEffectFailed = { effect, error -> onEffectFailed(effect, error) },
        startHint = " In a test, set Main to StandardTestDispatcher.",
    )

    /**
     * The scope of the runtime and of projections, with the error policy of Capsa.
     *
     * Pass it to `mapState` and `combineState` instead of `viewModelScope`. Otherwise a failing projection
     * crashes a release build.
     */
    protected val capsaScope: CoroutineScope = core.capsaScope

    private val snapshots: SnapshotStore? = savedStateHandle?.let(::HandleSnapshots)

    init {
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
     * The channel keeps effects until someone subscribes and serves a single consumer. The object lives as long as
     * the view model, so `CollectEffects` doesn’t restart the subscription on recomposition. The collector of
     * [CapsaEffects.flow] makes the screen visible, like a collector of a slice.
     */
    public val effects: CapsaEffects
        get() = core.effects

    /**
     * Registers the effects that the view model handles without the screen.
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
     * Installs `capability` and keeps the chosen part of its state in [SavedStateHandle].
     *
     * `saver` restores the snapshot before the capability is installed. Later, `saver` runs only when the system saves
     * the screen: it gets the current state and encodes the snapshot then, not on every change. `null` saves nothing,
     * so the capability starts from its initial state after the process comes back.
     *
     * @param capability a capability of the screen
     * @param saver turns the state into a snapshot and back
     * @param key the key of the snapshot in [SavedStateHandle]
     * @return a read-only flow of the state
     * @throws IllegalStateException if there is no `savedStateHandle` or the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    protected fun <S : Any, P : Any> installSaved(capability: Capability<S>, saver: SliceSaver<S, P>, key: String): StateFlow<S> {
        val store = checkNotNull(snapshots) { "installSaved() needs a SavedStateHandle in the constructor" }
        return core.installSaved(capability, saver, key, store)
    }

    /** Hands `error` to the error policy of Capsa: a crash with [Capsa.failFast] on, an error in the log otherwise. */
    internal fun fail(error: Throwable) {
        core.fail(error)
    }

    /**
     * Handles `error` that the handler of `effect` threw.
     *
     * By default it hands the error to the error policy of Capsa: a crash with [Capsa.failFast] on, an error in the log otherwise.
     * An override can turn it into `signal(...)`, so a capability shows the error to the user.
     * The runtime goes on with the next effects.
     *
     * @param effect the effect whose handler failed
     * @param error the error of the handler
     */
    protected open suspend fun onEffectFailed(effect: Effect, error: Exception) {
        fail(IllegalStateException("Capsa effect $effect failed in the ViewModel", error))
    }

    public companion object {

        // Dispatchers.Default is only the default: tests replace it with setWorkDispatcher.
        @Suppress("InjectDispatcher")
        private val workDispatcher = atomic<CoroutineDispatcher>(Dispatchers.Default)

        /**
         * Replaces the work dispatcher for view models created after the call. Tests on virtual time need it,
         * like `Dispatchers.setMain`. [resetWorkDispatcher] brings back `Dispatchers.Default`.
         */
        public fun setWorkDispatcher(dispatcher: CoroutineDispatcher) {
            workDispatcher.value = dispatcher
        }

        /** Brings back the default work dispatcher, `Dispatchers.Default`. */
        @Suppress("InjectDispatcher")
        public fun resetWorkDispatcher() {
            workDispatcher.value = Dispatchers.Default
        }

        /**
         * Returns the config a view model takes by default: works on the work dispatcher, `Dispatchers.Default` unless
         * a test replaced it with [setWorkDispatcher], and the runtime journal in `logger` through [LoggingCapsaObserver].
         *
         * Change one setting on top of it with [CapsaConfig.copy], and the rest stays. A view model with a logger
         * of its own passes the same logger here:
         *
         * ```
         * class ClockViewModel : CapsaViewModel(config = defaultConfig().copy(visibility = CapsaVisibility.Watched(grace = 2.seconds)))
         * ```
         *
         * @param logger the logger that receives the runtime journal
         */
        public fun defaultConfig(logger: CapsaLogger = CapsaLogger.Default): CapsaConfig = CapsaConfig(
            workContext = workDispatcher.value,
            observer = LoggingCapsaObserver(logger),
        )
    }
}

/** Keeps snapshots in [SavedStateHandle]: the key of a snapshot is the key of [CapsaViewModel.installSaved]. */
private class HandleSnapshots(private val handle: SavedStateHandle) : SnapshotStore {
    override fun restored(key: String): SavedState? = handle.get<SavedState>(key)

    override fun register(key: String, provide: () -> SavedState) {
        handle.setSavedStateProvider(key) { provide() }
    }
}
