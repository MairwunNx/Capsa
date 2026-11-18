package com.mairwunnx.capsa.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.savedstate.SavedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import com.mairwunnx.capsa.Capability
import com.mairwunnx.capsa.CapabilityScope
import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaRuntime
import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.effectHandlers
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.plus

/**
 * Hosts the capabilities of a screen in an AndroidX [ViewModel], on Android, iOS, and the JVM.
 *
 * A subclass installs capabilities with [install] or [installSaved], builds the UI state, and, if it needs to, registers handlers
 * in [handleEffects]. The runtime starts when the subclass constructor ends, or earlier on the first [send]
 * or the first access to [effects]. On the JVM without a main dispatcher, only [send] or [effects] start it.
 *
 * ```
 * class LikeViewModel(likes: LikesCapability) : CapsaViewModel() {
 *     val likes: StateFlow<LikesSlice> = install(likes)
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
 * Uncaught errors of the runtime, of projections, of snapshots, and of the screen’s effect handlers go through [capsaScope].
 * With [Capsa.failFast] on, they crash the app, so a bug shows up at once. Otherwise they go to [logger] as errors,
 * and the screen keeps working on its last valid state.
 *
 * @param logger the logger for failing effect handlers and the runtime journal
 * @param savedStateHandle the store of snapshots for [installSaved], or `null` if the screen saves nothing
 * @param config the runtime settings
 */
@Suppress("AbstractClassCanBeConcreteClass") // A screen extends it, nobody creates it as is.
public abstract class CapsaViewModel(
    protected val logger: CapsaLogger = CapsaLogger.Default,
    private val savedStateHandle: SavedStateHandle? = null,
    private val config: CapsaConfig = defaultConfig(logger),
) : ViewModel(),
    CapabilityScope {

    /**
     * The scope of the runtime and of projections, with the error policy of Capsa.
     *
     * Pass it to `mapState` and `combineState` instead of `viewModelScope`. Otherwise a failing projection
     * crashes a release build.
     */
    protected val capsaScope: CoroutineScope = viewModelScope + capsaExceptionHandler(
        failFast = Capsa.failFast,
        fallback = viewModelScope.coroutineContext[CoroutineExceptionHandler],
        report = ::report,
    )

    private val builder = CapsaBuilder()
    private val runtime = atomic<CapsaRuntime?>(null)
    private val startLock = SynchronizedObject()
    private val outbox = Channel<Effect>(Channel.UNLIMITED)

    init {
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { outbox.cancel() }
        // Not `immediate`: the start waits until the subclass constructor ends. Without a main thread there is no later
        // moment to wait for: a start on another thread would race the constructor, so send() or effects start the runtime.
        if (hasMainDispatcher()) {
            viewModelScope.launchReporting(
                context = Dispatchers.Main,
                onError = { logError("Capsa: the runtime did not start", it) },
                onErrorFailed = ::fail,
            ) {
                ensureStarted()
            }
        }
    }

    /** Passes a user `intent` to the runtime. */
    public fun send(intent: Intent) {
        ensureStarted().append(intent)
    }

    /** Passes an outside `signal` to the runtime, such as the result of another screen. */
    protected fun signal(signal: Signal) {
        ensureStarted().append(signal)
    }

    /**
     * The effects for the screen. The first access starts the runtime.
     *
     * The channel keeps effects until someone subscribes and serves a single consumer. The object lives as long as
     * the view model, so `CollectEffects` doesn’t restart the subscription on recomposition.
     */
    public val effects: CapsaEffects by lazy {
        ensureStarted()
        CapsaEffects(flow = outbox.receiveAsFlow(), onFail = ::fail, onVisible = { ensureStarted().onVisibilityChanged(it) })
    }

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
    protected fun <S : Any> install(capability: Capability<S>): StateFlow<S> {
        checkNotStarted("install")
        return builder.install(capability)
    }

    /**
     * Installs `capability` and keeps the chosen part of its state in [SavedStateHandle].
     *
     * `saver` restores the snapshot before the capability is installed. After the start it receives every new state:
     * a snapshot replaces the value under `key`, and `null` removes it.
     *
     * @param capability a capability of the screen
     * @param saver turns the state into a snapshot and back
     * @param key the key of the snapshot in [SavedStateHandle]
     * @return a read-only flow of the state
     * @throws IllegalStateException if there is no `savedStateHandle` or the runtime has already started
     * @throws IllegalArgumentException if the name of the capability is already taken
     */
    protected fun <S : Any, P : Any> installSaved(capability: Capability<S>, saver: SliceSaver<S, P>, key: String): StateFlow<S> {
        checkNotStarted("installSaved")
        val handle = checkNotNull(savedStateHandle) { "installSaved() needs a SavedStateHandle in the constructor" }
        val restored = handle.get<SavedState>(key)?.let { saved -> restoreOrNull(saver, saved, key) }
        val state = builder.install(capability, restored)
        viewModelScope.launchReporting(onError = { logError("Capsa: snapshot $key was not saved", it) }, onErrorFailed = ::fail) {
            state.collect { current -> save(saver = saver, state = current, handle = handle, key = key) }
        }
        return state
    }

    /** A broken snapshot must not break the screen: the capability starts from [Capability.initial]. */
    private fun <S : Any, P : Any> restoreOrNull(saver: SliceSaver<S, P>, saved: SavedState, key: String): S? =
        try {
            saver.restore(decodeFromSavedState(saver.serializer, saved))
        } catch (e: Exception) {
            fail(IllegalStateException("Capsa could not restore snapshot \"$key\"", e))
            null
        }

    /** One failed save doesn’t stop saving the next states. */
    private fun <S : Any, P : Any> save(saver: SliceSaver<S, P>, state: S, handle: SavedStateHandle, key: String) {
        try {
            val saved = saver.save(state)
            if (saved != null) handle[key] = encodeToSavedState(saver.serializer, saved) else handle.remove<SavedState>(key)
        } catch (e: Exception) {
            fail(IllegalStateException("Capsa could not save snapshot \"$key\"", e))
        }
    }

    /** Hands `error` to the error policy of Capsa: a crash with [Capsa.failFast] on, an error in the log otherwise. */
    internal fun fail(error: Throwable) {
        capsaScope.coroutineContext[CoroutineExceptionHandler]?.handleException(capsaScope.coroutineContext, error)
    }

    private fun checkNotStarted(method: String) {
        check(runtime.value == null) {
            "$method() after the runtime started. Call it while the subclass initializes, and leave send() and effects alone " +
                "until the constructor ends. In a test, set Main to StandardTestDispatcher."
        }
    }

    /** `send` and `signal` work from any thread: the runtime is created exactly once. */
    private fun ensureStarted(): CapsaRuntime = runtime.value ?: synchronized(startLock) { runtime.value ?: start() }

    private fun start(): CapsaRuntime {
        val handlers = effectHandlers(onUnhandled = { outbox.trySend(it) }) { handleEffects() }
        val started = builder.build(capsaScope, config)
        runtime.value = started
        viewModelScope.launchReporting(onError = { logError("Capsa: the effect stream stopped", it) }, onErrorFailed = ::fail) {
            started.effects.collect { dispatch(handlers, it) }
        }
        return started
    }

    private suspend fun dispatch(handlers: EffectHandlers, effect: Effect) {
        try {
            handlers.handle(effect)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onEffectFailed(effect, e)
        }
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

    /**
     * Writes an error that the release policy keeps away from a crash. A logger that throws here has nowhere left
     * to report to, so the platform log gets both errors instead of the app crashing.
     */
    private fun report(error: Throwable) {
        try {
            logger.log(CapsaLogger.Level.Error, error) { "Capsa failure" }
        } catch (e: Exception) {
            e.addSuppressed(error)
            writeToPlatformLog(CapsaLogger.Level.Error, "Capsa failure, and the logger failed too", e)
        }
    }

    private fun logError(text: String, error: Exception) {
        logger.log(CapsaLogger.Level.Error, error) { text }
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

        private fun defaultConfig(logger: CapsaLogger) = CapsaConfig(
            workContext = workDispatcher.value,
            observer = LoggingCapsaObserver(logger),
        )
    }
}

/**
 * Returns `false` when the platform has no main dispatcher, such as a plain JVM app without `kotlinx-coroutines-swing`
 * or a test without `Dispatchers.setMain`. `viewModelScope` checks the same way: a missing dispatcher throws on access
 * to `immediate`, while `Dispatchers.Main` itself would throw only on the first dispatch.
 */
internal fun hasMainDispatcher(): Boolean =
    try {
        Dispatchers.Main.immediate
        true
    } catch (_: NotImplementedError) {
        false
    } catch (_: IllegalStateException) {
        false
    }

/**
 * Decides the fate of an uncaught Capsa error.
 *
 * With `failFast`, the error goes to the previous handler of the scope, or without one to the platform, and the app crashes.
 * Otherwise the error goes to `report`, and the scope keeps working.
 */
internal fun capsaExceptionHandler(
    failFast: Boolean,
    fallback: CoroutineExceptionHandler?,
    report: (Throwable) -> Unit,
): CoroutineExceptionHandler = CoroutineExceptionHandler { context, error ->
    when {
        !failFast -> report(error)
        fallback != null -> fallback.handleException(context, error)
        else -> deliverUncaughtToPlatform(error)
    }
}

/**
 * The effects of a screen together with the error policy for their handlers.
 *
 * The screen passes this object to `CollectEffects` instead of the view model itself. A host without Compose,
 * such as SwiftUI, collects [flow] and calls [fail] and [setVisible] on its own.
 *
 * @property flow the effects that the view model didn’t take
 */
public class CapsaEffects internal constructor(
    public val flow: Flow<Effect>,
    private val onFail: (Throwable) -> Unit,
    private val onVisible: (Boolean) -> Unit,
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

    /**
     * Tells the runtime whether the screen is visible. The runtime pauses works with `whileVisible` while it isn’t
     * and starts them again when it comes back.
     *
     * The host calls it with `true` when the screen starts collecting [flow] and with `false` when it stops.
     *
     * @param visible `true` if the user sees the screen
     */
    public fun setVisible(visible: Boolean) {
        onVisible(visible)
    }
}
