package com.mairwunnx.capsa.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * A subclass installs capabilities with [install], builds the UI state, and, if it needs to, registers handlers
 * in [handleEffects]. The runtime starts when the subclass constructor ends, or earlier on the first [send]
 * or the first access to [effects].
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
 * Uncaught errors of the runtime, of projections, and of the screen’s effect handlers go through [capsaScope].
 * With [Capsa.failFast] on, they crash the app, so a bug shows up at once. Otherwise they go to [logger] as errors,
 * and the screen keeps working on its last valid state.
 *
 * @param logger the logger for failing effect handlers and the runtime journal
 * @param config the runtime settings
 */
@Suppress("AbstractClassCanBeConcreteClass") // A screen extends it, nobody creates it as is.
public abstract class CapsaViewModel(
    protected val logger: CapsaLogger = CapsaLogger.Default,
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
        report = { error -> logger.log(CapsaLogger.Level.Error, error) { "Capsa failure" } },
    )

    private val builder = CapsaBuilder()
    private val runtime = atomic<CapsaRuntime?>(null)
    private val startLock = SynchronizedObject()
    private val outbox = Channel<Effect>(Channel.UNLIMITED)

    init {
        viewModelScope.coroutineContext[Job]?.invokeOnCompletion { outbox.cancel() }
        // Not `immediate`: the start waits until the subclass constructor ends.
        viewModelScope.launchReporting(
            context = Dispatchers.Main,
            onError = { logError("Capsa: the runtime did not start", it) },
        ) {
            ensureStarted()
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
        CapsaEffects(flow = outbox.receiveAsFlow(), fail = ::fail, visible = { ensureStarted().onVisibilityChanged(it) })
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
        viewModelScope.launchReporting(onError = { logError("Capsa: the effect stream stopped", it) }) {
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
 * The screen passes this object to `CollectEffects` instead of the view model itself.
 *
 * @property flow the effects that the view model didn’t take
 */
public class CapsaEffects internal constructor(
    public val flow: Flow<Effect>,
    internal val fail: (Throwable) -> Unit,
    internal val visible: (Boolean) -> Unit,
)
