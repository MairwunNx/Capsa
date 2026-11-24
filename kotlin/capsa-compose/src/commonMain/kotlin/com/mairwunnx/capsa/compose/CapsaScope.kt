package com.mairwunnx.capsa.compose

import androidx.savedstate.SavedState
import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaHostCore
import com.mairwunnx.capsa.CapsaLogger
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.LoggingCapsaObserver
import com.mairwunnx.capsa.SliceSaver
import com.mairwunnx.capsa.SnapshotStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow

/**
 * Where the capabilities of a screen get installed: the receiver of [rememberCapsa] and the constructor parameter of [CapsaComposable].
 *
 * A screen uses it in one of two ways. A class passes it to [CapsaComposable] and installs through its own members.
 * A lambda installs right here and ends with [expose]:
 *
 * ```
 * val like = rememberCapsa {
 *     val like = installSaved(likes(likePorts), LikeSaver, key = "like")
 *     handleEffects { on<LikeTracked> { analytics.log(it) } }
 *     expose(like.mapState(capsaScope) { it.toUi() })
 * }
 * ```
 *
 * The two ways don’t mix: a lambda that installs and also returns a class fails at once.
 *
 * [rememberCapsa] creates the scope. A test or a host of your own creates it with the public constructor: then everything
 * lives as long as `scope`, and [installSaved] saves nothing. The scope carries nothing of the platform, such as
 * the Android `Context`: the screen passes its dependencies itself.
 */
public class CapsaScope internal constructor(
    scope: CoroutineScope,
    internal val logger: CapsaLogger,
    config: CapsaConfig,
    private val snapshots: SnapshotStore,
) : CapabilityScope {

    /**
     * Creates a scope that lives as long as `scope`.
     *
     * @param scope the lifetime of the runtime. Decisions run on its dispatcher
     * @param logger the logger for failing effect handlers and the runtime journal
     * @param config the runtime settings, [defaultConfig] unless you pass your own. To change one setting,
     * pass `defaultConfig(logger).copy(…)`: a config built from scratch drops the work dispatcher and the journal
     */
    public constructor(
        scope: CoroutineScope,
        logger: CapsaLogger = CapsaLogger.Default,
        config: CapsaConfig = defaultConfig(logger),
    ) : this(scope, logger, config, NoSnapshots)

    private var registerEffects: EffectHandlers.Builder.() -> Unit = {}
    private var handleFailure: (suspend (Effect, Exception) -> Unit)? = null
    private var owner: CapsaComposable? = null
    private var installedHere = false

    internal val core: CapsaHostCore = CapsaHostCore(
        scope = scope,
        logger = logger,
        config = config,
        handleEffects = { registerEffects() },
        onEffectFailed = { effect, error -> effectFailed(effect, error) },
    )

    /**
     * The scope of the runtime and of projections, with the error policy of Capsa.
     *
     * Pass it to `mapState` and `combineState`. It ends together with the screen.
     */
    @Suppress("MemberNameEqualsClassName") // The same name as in CapsaComposable and CapsaViewModel.
    public val capsaScope: CoroutineScope = core.capsaScope

    /**
     * Installs `capability` into the runtime and returns the flow of its state.
     *
     * @throws IllegalStateException if a [CapsaComposable] already uses this scope or the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    public fun <S : Any> install(capability: Capability<S>): StateFlow<S> {
        useHere("install")
        return core.install(capability)
    }

    /**
     * Installs `capability` and keeps the chosen part of its state through the death of the process.
     *
     * The snapshot goes to the saveable state registry of the composition, the same one that `rememberSaveable` uses.
     * Without a registry the capability works and nothing is saved.
     *
     * @param key the key of the snapshot, unique within the screen
     * @throws IllegalStateException if a [CapsaComposable] already uses this scope or the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    public fun <S : Any, P : Any> installSaved(capability: Capability<S>, saver: SliceSaver<S, P>, key: String): StateFlow<S> {
        useHere("installSaved")
        return core.installSaved(capability, saver, key, snapshots)
    }

    /**
     * Registers the effects that the screen handles without its UI, such as analytics. Effects without a matching
     * handler go to [CapsaHandle.effects]. A second call replaces the first.
     */
    public fun handleEffects(register: EffectHandlers.Builder.() -> Unit) {
        useHere("handleEffects")
        registerEffects = register
    }

    /**
     * Handles an error that a handler from [handleEffects] threw. By default the error goes to the error policy of Capsa:
     * a crash with `Capsa.failFast` on, an error in the log otherwise. The runtime goes on with the next effects.
     */
    public fun onEffectFailed(handle: suspend (effect: Effect, error: Exception) -> Unit) {
        useHere("onEffectFailed")
        handleFailure = handle
    }

    /**
     * Ends the setup and hands `state` to the screen together with [CapsaHandle.send] and [CapsaHandle.effects].
     *
     * `state` is whatever the screen reads: one projection, several flows in a `data class`, or `Unit`.
     * The runtime starts on the main thread right after.
     */
    public fun <T> expose(state: T): CapsaHandle<T> {
        useHere("expose")
        core.startAfterConstructor()
        return CapsaHandle(state, core)
    }

    /** Called by [CapsaComposable] while it initializes: from now on the class decides. */
    internal fun attach(capsa: CapsaComposable, handleEffects: EffectHandlers.Builder.() -> Unit, onEffectFailed: suspend (Effect, Exception) -> Unit) {
        check(owner == null) { "A CapsaScope serves one CapsaComposable. Create the class once in rememberCapsa." }
        check(!installedHere) { MIXED }
        owner = capsa
        registerEffects = handleEffects
        handleFailure = onEffectFailed
    }

    /** [CapsaComposable.installSaved] goes here: the snapshots live in the scope. */
    internal fun <S : Any, P : Any> installSavedFor(capability: Capability<S>, saver: SliceSaver<S, P>, key: String): StateFlow<S> =
        core.installSaved(capability, saver, key, snapshots)

    private suspend fun effectFailed(effect: Effect, error: Exception) {
        val handle = handleFailure
        if (handle != null) handle(effect, error) else core.fail(IllegalStateException("Capsa effect $effect failed in rememberCapsa", error))
    }

    private fun useHere(method: String) {
        check(owner == null) { "$method() on the scope of a CapsaComposable. $MIXED" }
        installedHere = true
    }

    public companion object {

        /**
         * Returns the config a screen of [rememberCapsa] takes by default: works on `Dispatchers.Default`
         * and the runtime journal in `logger` through [LoggingCapsaObserver].
         *
         * Change one setting on top of it with [CapsaConfig.copy], and the rest stays:
         *
         * ```
         * val clock = rememberCapsa(config = CapsaScope.defaultConfig().copy(visibility = CapsaVisibility.Watched(grace = 2.seconds))) {
         *     expose(install(clock()))
         * }
         * ```
         *
         * @param logger the logger that receives the runtime journal. Pass the one you give to [rememberCapsa]
         */
        // Dispatchers.Default is only the default: a test passes a config of its own.
        @Suppress("InjectDispatcher")
        public fun defaultConfig(logger: CapsaLogger = CapsaLogger.Default): CapsaConfig = CapsaConfig(
            workContext = Dispatchers.Default,
            observer = LoggingCapsaObserver(logger),
        )
    }
}

/** Why the lambda of [rememberCapsa] can’t both install and return a [CapsaComposable]. */
private const val MIXED = "Either install in the lambda of rememberCapsa and end it with expose(), or return a CapsaComposable that installs itself."

/** Saves nothing: the capabilities start from their initial state. */
private object NoSnapshots : SnapshotStore {
    override fun restored(key: String): SavedState? = null

    override fun register(key: String, provide: () -> SavedState) = Unit
}
