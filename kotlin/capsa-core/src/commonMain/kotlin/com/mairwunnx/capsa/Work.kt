package com.mairwunnx.capsa

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.retryWhen
import kotlin.jvm.JvmInline
import kotlin.time.Duration

/**
 * Identifies an async work inside one capability.
 *
 * The runtime adds the owner’s name, so equal keys of different capabilities don’t clash.
 */
@JvmInline
public value class WorkKey(public val name: String)

/** Tells the runtime what to do when a work starts again with the same [WorkKey]. */
public enum class WorkPolicy {
    /**
     * Cancels the previous run and starts the new one. The [work] and [stream] factories use it by default.
     *
     * The runtime drops the signals of the previous run that still wait in the queue: an old answer never overwrites a new one.
     */
    Latest,

    /** Ignores the new run while the previous one is still going. Fits an initial load. */
    KeepExisting,

    /** Runs the works side by side. A `cancel` by the key stops every active run at once. */
    Parallel,
}

/**
 * Describes an async work as a key, a start policy, and a flow of signals.
 *
 * The runtime collects a one-shot request and a long subscription the same way, as `Flow<Signal>`.
 * A one-shot request emits one signal, a subscription emits several.
 *
 * The `onError` handler turns an exception into a [Signal] that the work chooses.
 * Coroutine cancellation isn’t an error.
 */
public class Work internal constructor(
    public val key: WorkKey,
    public val policy: WorkPolicy,
    private val source: () -> Flow<Signal>,
    private val onError: (Throwable) -> Signal,
    public val whileVisible: Boolean = false,
    public val debounce: Duration = Duration.ZERO,
    public val retry: Retry? = null,
) {

    /**
     * Returns the flow of signals with the `onError` handler attached.
     *
     * The runtime collects this flow directly. A test can collect it with fake dependencies
     * without running the whole runtime.
     */
    public fun signals(): Flow<Signal> = signals(onFailure = {})

    /** Like [signals], but reports the exception to the runtime first, for the log and the history. */
    internal fun signals(onFailure: (Throwable) -> Unit): Flow<Signal> =
        flow {
            if (debounce.isPositive()) delay(debounce)
            emitAll(source().retrying(retry))
        }.catch {
            onFailure(it)
            emit(onError(it))
        }

    override fun toString(): String = buildString {
        append("Work(").append(key.name).append(", ").append(policy)
        if (whileVisible) append(", whileVisible")
        if (debounce.isPositive()) append(", debounce=").append(debounce)
        if (retry != null) append(", retry=").append(retry)
        append(')')
    }
}

/**
 * Creates a one-shot work that turns one call of `block` into one [Signal].
 *
 * The runtime runs `block` in its scope and passes an exception to `onError`.
 *
 * `debounce` delays `block`. Together with the default [WorkPolicy.Latest] this is a debounce:
 * a new run with the same key cancels the wait of the previous one, and the request leaves only after a pause.
 *
 * ```
 * on<QueryChanged> { state, fact ->
 *     state.copy(query = fact.text).commit(
 *         work(SEARCH, onError = { SearchFailed }, debounce = 300.milliseconds) {
 *             SearchLoaded(ports.search(fact.text))
 *         },
 *     )
 * }
 * ```
 *
 * @param key the key of the work within the capability
 * @param onError turns an exception from `block` into a signal
 * @param policy what to do when a work with this key is already running
 * @param debounce the pause before `block` is called
 * @param retry retries on failure. `null` means no retries
 * @param block the `suspend` function of the request
 */
public fun work(
    key: WorkKey,
    onError: (Throwable) -> Signal,
    policy: WorkPolicy = WorkPolicy.Latest,
    debounce: Duration = Duration.ZERO,
    retry: Retry? = null,
    block: suspend () -> Signal,
): Work = Work(
    key = key,
    policy = policy,
    source = { flow { emit(block()) } },
    onError = onError,
    debounce = debounce,
    retry = retry,
)

/**
 * Creates a subscription to a flow of signals.
 *
 * The flow lives until a `cancel` by the key, a restart with [WorkPolicy.Latest], or the end of the scope.
 * The `block` factory fits repository flows, `combine`, and other flow sources.
 *
 * With `whileVisible` the runtime cancels the flow while the screen is hidden and starts it again when the screen
 * comes back. This fits timers and frequent updates that the back stack doesn’t need. The flow must be able to start
 * from the current value: the runtime calls `block` again after the screen returns.
 *
 * @param key the key of the work within the capability
 * @param onError turns an exception from the flow into a signal
 * @param policy what to do when a work with this key is already running
 * @param whileVisible stop the flow while the screen is hidden
 * @param retry subscribes again on failure. `null` means no retries
 * @param block the factory of the signal flow
 */
public fun stream(
    key: WorkKey,
    onError: (Throwable) -> Signal,
    policy: WorkPolicy = WorkPolicy.Latest,
    whileVisible: Boolean = false,
    retry: Retry? = null,
    block: () -> Flow<Signal>,
): Work = Work(
    key = key,
    policy = policy,
    source = block,
    onError = onError,
    whileVisible = whileVisible,
    retry = retry,
)

private fun Flow<Signal>.retrying(retry: Retry?): Flow<Signal> =
    if (retry == null) {
        this
    } else {
        retryWhen { cause, attempt ->
            val again = attempt < retry.times && retry.retryIf(cause)
            if (again) delay(retry.delayBefore(attempt.toInt()))
            again
        }
    }
