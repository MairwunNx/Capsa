package com.mairwunnx.capsa

import androidx.savedstate.SavedState
import androidx.savedstate.read
import androidx.savedstate.savedState
import androidx.savedstate.serialization.decodeFromSavedState
import androidx.savedstate.serialization.encodeToSavedState
import kotlinx.atomicfu.atomic
import kotlinx.atomicfu.locks.SynchronizedObject
import kotlinx.atomicfu.locks.synchronized
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.plus

/**
 * The part of a host that `CapsaViewModel` and `CapsaComposable` share: installing capabilities, starting the runtime once,
 * the effects of the screen, saved snapshots, the foreground of the app, and the error policy.
 *
 * The host creates the core while it initializes and passes its own members as callbacks. The core calls them only
 * when the runtime starts, so a subclass that isn’t constructed yet is never touched.
 *
 * @param scope the lifetime of the host: `viewModelScope` or the scope of a `CapsaComposable`. Its end stops the runtime
 * @param logger the logger for failing effect handlers and stopped streams
 * @param config the runtime settings
 * @param handleEffects registers the effects that the host handles without the screen
 * @param onEffectFailed handles an error of a handler from `handleEffects`
 * @param startHint the advice to add when `install` comes after the start
 */
@InternalCapsaApi
public class CapsaHostCore(
    private val scope: CoroutineScope,
    private val logger: CapsaLogger,
    private val config: CapsaConfig,
    private val handleEffects: EffectHandlers.Builder.() -> Unit,
    private val onEffectFailed: suspend (effect: Effect, error: Exception) -> Unit,
    private val startHint: String = "",
) {

    /** The scope of the runtime and of projections, with the error policy of Capsa. */
    public val capsaScope: CoroutineScope = scope + capsaExceptionHandler(
        failFast = Capsa.failFast,
        fallback = scope.coroutineContext[CoroutineExceptionHandler],
        report = ::report,
    )

    private val builder = CapsaBuilder()
    private val runtime = atomic<CapsaRuntime?>(null)
    private val startLock = SynchronizedObject()
    private val outbox = Channel<Effect>(Channel.UNLIMITED)

    init {
        scope.coroutineContext[Job]?.invokeOnCompletion { outbox.cancel() }
    }

    /**
     * The effects for the screen. The first access starts the runtime.
     *
     * The channel keeps effects until someone subscribes and serves a single consumer. The collector of
     * [CapsaEffects.flow] makes the screen visible, like a collector of a slice.
     */
    public val effects: CapsaEffects by lazy {
        val runtime = ensureStarted()
        CapsaEffects(flow = runtime.watched(outbox.receiveAsFlow()), onFail = ::fail)
    }

    /**
     * Starts the runtime on the main thread once the constructor of the host ends.
     *
     * Not `immediate`: the start waits until the subclass constructor ends. Without a main thread there is no later
     * moment to wait for: a start on another thread would race the constructor, so [send] or [effects] start the runtime.
     */
    public fun startAfterConstructor() {
        if (!hasMainDispatcher()) return
        scope.launchReporting(context = Dispatchers.Main, onError = { logError("Capsa: the runtime did not start", it) }, onErrorFailed = ::fail) {
            ensureStarted()
        }
    }

    /** Passes a user `intent` to the runtime. */
    public fun send(intent: Intent) {
        ensureStarted().append(intent)
    }

    /** Passes an outside `signal` to the runtime. */
    public fun signal(signal: Signal) {
        ensureStarted().append(signal)
    }

    /** Installs `capability` and returns the flow of its state. */
    public fun <S : Any> install(capability: Capability<S>): StateFlow<S> {
        checkNotStarted("install")
        return builder.install(capability)
    }

    /**
     * Installs `capability` and keeps the chosen part of its state in `store`.
     *
     * `saver` restores the snapshot before the capability is installed. Later, `saver` runs only when the platform saves
     * the screen. A broken snapshot goes to the error policy, and the capability starts from [Capability.initial].
     */
    public fun <S : Any, P : Any> installSaved(capability: Capability<S>, saver: SliceSaver<S, P>, key: String, store: SnapshotStore): StateFlow<S> {
        checkNotStarted("installSaved")
        val restored = store.restored(key)?.let { saved -> restoreOrNull(saver, saved, key) }
        val state = builder.install(capability, restored)
        store.register(key) { save(saver = saver, state = state.value, key = key) }
        return state
    }

    /** Hands `error` to the error policy of Capsa: a crash with [Capsa.failFast] on, an error in the log otherwise. */
    public fun fail(error: Throwable) {
        capsaScope.coroutineContext[CoroutineExceptionHandler]?.handleException(capsaScope.coroutineContext, error)
    }

    /** `send` and `signal` work from any thread: the runtime is created exactly once. */
    public fun ensureStarted(): CapsaRuntime = runtime.value ?: synchronized(startLock) { runtime.value ?: start() }

    /** A broken snapshot must not break the screen: the capability starts from [Capability.initial]. */
    private fun <S : Any, P : Any> restoreOrNull(saver: SliceSaver<S, P>, saved: SavedState, key: String): S? =
        try {
            saved.read { if (contains(SNAPSHOT_KEY)) getSavedState(SNAPSHOT_KEY) else null }
                ?.let { snapshot -> saver.restore(decodeFromSavedState(saver.serializer, snapshot)) }
        } catch (e: Exception) {
            fail(IllegalStateException("Capsa could not restore snapshot \"$key\"", e))
            null
        }

    /**
     * Encodes the snapshot of `state` when the platform saves the screen.
     *
     * The snapshot sits in a wrapper, so `null` and a snapshot that encodes to nothing stay apart. A failing `saver`
     * goes to the error policy and saves nothing for `key`: the other keys of the screen are saved as usual.
     */
    private fun <S : Any, P : Any> save(saver: SliceSaver<S, P>, state: S, key: String): SavedState {
        val snapshot = try {
            saver.save(state)?.let { encodeToSavedState(saver.serializer, it) }
        } catch (e: Exception) {
            fail(IllegalStateException("Capsa could not save snapshot \"$key\"", e))
            null
        }
        return savedState { if (snapshot != null) putSavedState(SNAPSHOT_KEY, snapshot) }
    }

    private fun checkNotStarted(method: String) {
        check(runtime.value == null) {
            "$method() after the runtime started. Call it while the subclass initializes, and leave send() and effects alone " +
                "until the constructor ends.$startHint"
        }
    }

    private fun start(): CapsaRuntime {
        val handlers = effectHandlers(onUnhandled = { outbox.trySend(it) }) { handleEffects() }
        val started = builder.build(capsaScope, config)
        runtime.value = started
        scope.launchReporting(onError = { logError("Capsa: the effect stream stopped", it) }, onErrorFailed = ::fail) {
            started.effects.collect { dispatch(handlers, it) }
        }
        AppForeground.flow()?.let { foreground ->
            scope.launchReporting(onError = { logError("Capsa: the app foreground stream stopped", it) }, onErrorFailed = ::fail) {
                foreground.collect(started::onVisibilityChanged)
            }
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
}

/** The key of the snapshot inside the wrapper of [CapsaHostCore.installSaved]. */
private const val SNAPSHOT_KEY = "snapshot"

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
