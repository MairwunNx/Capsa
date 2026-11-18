package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import platform.Foundation.NSNotificationCenter

/**
 * Emits `false` on the notification `hidden` and `true` on `shown`.
 *
 * The flow emits nothing at the start: the app counts as being in the foreground until the first notification.
 * Reading the state of the app needs the main thread and a running app, which a view model can’t count on.
 */
internal fun foregroundNotifications(hidden: String?, shown: String?): Flow<Boolean> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val onHidden = center.addObserverForName(hidden, null, null) { _ -> trySend(false) }
    val onShown = center.addObserverForName(shown, null, null) { _ -> trySend(true) }
    awaitClose {
        center.removeObserver(onHidden)
        center.removeObserver(onShown)
    }
}
