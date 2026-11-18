package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Launches `block` and hands its `Exception` to `onError` instead of the scope.
 *
 * Cancellation passes through, so the coroutine ends as cancelled. A timeout of `withTimeout` counts as an error:
 * it’s a failure of the block, not a cancellation from outside. An error that isn’t an `Exception`, or one that
 * `onError` throws, goes to the scope as usual.
 */
internal fun CoroutineScope.launchReporting(
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Exception) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context) {
    try {
        block()
    } catch (e: TimeoutCancellationException) {
        onError(e)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        onError(e)
    }
}
