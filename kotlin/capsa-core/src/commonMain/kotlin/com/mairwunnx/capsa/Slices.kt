package com.mairwunnx.capsa

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transform

/**
 * Turns one slice into a UI model.
 *
 * The slice holds domain data, for example `likes: Int`, and `transform` does the formatting.
 * [mapState] computes the initial value from [StateFlow.value] synchronously, so the result is available at once.
 * By default [SharingStarted.Eagerly] starts the subscription together with the call.
 * If `transform` fails, the projection skips that value and stays on the last good one, and the error goes
 * to the error handler of the scope. The next value of the slice goes through `transform` again.
 * `CapsaViewModel.capsaScope` crashes the app on such an error only in debug builds.
 *
 * @param scope the scope of the subscription to the slice
 * @param started the start policy of the subscription
 * @param transform a pure transformation that reads no time, user, or flags
 */
public fun <T, R> StateFlow<T>.mapState(
    scope: CoroutineScope,
    started: SharingStarted = SharingStarted.Eagerly,
    transform: (T) -> R,
): StateFlow<R> =
    projectReporting(scope = scope, started = started, initialValue = transform(value), transform = transform)

/**
 * Combines two slices into one derived state.
 *
 * The projection joins the states outside the capabilities, so no capability gets access to another one’s slice.
 * [combineState] computes the initial value from the current values of `a` and `b` synchronously.
 *
 * @param transform a pure transformation of the values of `a` and `b`
 */
public fun <A, B, R> combineState(
    scope: CoroutineScope,
    a: StateFlow<A>,
    b: StateFlow<B>,
    started: SharingStarted = SharingStarted.Eagerly,
    transform: (A, B) -> R,
): StateFlow<R> = combine(a, b, ::Pair)
    .projectReporting(
        scope = scope,
        started = started,
        initialValue = transform(a.value, b.value),
    ) { (x, y) -> transform(x, y) }

/** Combines three slices by the same rules as the overload for two. */
public fun <A, B, C, R> combineState(
    scope: CoroutineScope,
    a: StateFlow<A>,
    b: StateFlow<B>,
    c: StateFlow<C>,
    started: SharingStarted = SharingStarted.Eagerly,
    transform: (A, B, C) -> R,
): StateFlow<R> = combine(a, b, c, ::Triple)
    .projectReporting(
        scope = scope,
        started = started,
        initialValue = transform(a.value, b.value, c.value),
    ) { (x, y, z) -> transform(x, y, z) }

/**
 * Combines four slices into one derived state.
 *
 * Five or more sources need an intermediate state for a part of the data.
 */
public fun <A, B, C, D, R> combineState(
    scope: CoroutineScope,
    a: StateFlow<A>,
    b: StateFlow<B>,
    c: StateFlow<C>,
    d: StateFlow<D>,
    started: SharingStarted = SharingStarted.Eagerly,
    transform: (A, B, C, D) -> R,
): StateFlow<R> = combine(a, b, c, d, ::Four)
    .projectReporting(
        scope = scope,
        started = started,
        initialValue = transform(a.value, b.value, c.value, d.value),
    ) { transform(it.a, it.b, it.c, it.d) }

private data class Four<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

/**
 * Applies `transform` to each value and keeps the result in a [StateFlow].
 *
 * A failing `transform` is a bug in the projection code. The value gets skipped, the error goes to the error handler
 * of the scope, and the flow keeps working: one bad slice doesn’t freeze the UI for the rest of the screen’s life.
 */
private fun <T, R> Flow<T>.projectReporting(
    scope: CoroutineScope,
    started: SharingStarted,
    initialValue: R,
    transform: (T) -> R,
): StateFlow<R> = transform { value ->
    val projected = try {
        transform(value)
    } catch (@Suppress("TooGenericExceptionCaught") e: Throwable) {
        e.rethrowIfFatal(scope)
        scope.deliverUncaught(e)
        return@transform
    }
    emit(projected)
}.stateInReporting(scope, started, initialValue)

/**
 * Like `stateIn`, but a failure of the upstream goes to the error handler of the scope instead of crashing it.
 *
 * Cancellation and errors that aren’t an [Exception] pass through. If the error handler fails too,
 * the platform gets that failure as an uncaught error.
 */
private fun <T> Flow<T>.stateInReporting(scope: CoroutineScope, started: SharingStarted, initialValue: T): StateFlow<T> =
    catch { e ->
        if (e is CancellationException || e !is Exception) throw e
        try {
            scope.deliverUncaught(e)
        } catch (deliveryFailure: Exception) {
            if (deliveryFailure is CancellationException) throw deliveryFailure
            deliverUncaughtToPlatform(deliveryFailure)
        }
    }.stateIn(scope, started, initialValue)
