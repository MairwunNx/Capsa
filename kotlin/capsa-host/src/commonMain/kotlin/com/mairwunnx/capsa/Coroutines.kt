package com.mairwunnx.capsa

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
 * it’s a failure of the block, not a cancellation from outside. An error that isn’t an `Exception` goes to the scope as usual.
 *
 * If `onError` throws itself, for example because the app’s logger is down, its `Exception` goes to `onErrorFailed`
 * with the original error attached as suppressed. Nothing reaches the scope, so a release build doesn’t crash.
 */
internal fun CoroutineScope.launchReporting(
    context: CoroutineContext = EmptyCoroutineContext,
    onError: (Exception) -> Unit,
    onErrorFailed: (Exception) -> Unit,
    block: suspend CoroutineScope.() -> Unit,
): Job = launch(context) {
    try {
        block()
    } catch (e: TimeoutCancellationException) {
        deliver(e, onError, onErrorFailed)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        deliver(e, onError, onErrorFailed)
    }
}

private fun deliver(error: Exception, onError: (Exception) -> Unit, onErrorFailed: (Exception) -> Unit) {
    try {
        onError(error)
    } catch (e: Exception) {
        e.addSuppressed(error)
        onErrorFailed(e)
    }
}
