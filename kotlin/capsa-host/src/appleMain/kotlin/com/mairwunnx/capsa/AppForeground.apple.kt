package com.mairwunnx.capsa

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import platform.Foundation.NSNotification
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSThread

/**
 * Follows the foreground of the app through its notifications.
 *
 * Each notification of `changes` maps to a reader of the new state, which gets the notification itself. `initial` reads
 * the state when the collection starts, after the observers are in place, so a change in between isn’t lost. A reader
 * returns `null` when it can’t tell, for example without a running app in a test binary, and the flow emits nothing then:
 * the app counts as being in the foreground. The app state lives on the main thread, so `initial` runs only there.
 */
internal fun appStateFlow(changes: Map<String?, (NSNotification?) -> Boolean?>, initial: () -> Boolean?): Flow<Boolean> = callbackFlow {
    val center = NSNotificationCenter.defaultCenter
    val observers = changes.map { (name, read) -> center.addObserverForName(name, null, null) { note -> read(note)?.let { trySend(it) } } }
    if (NSThread.isMainThread) initial()?.let { trySend(it) }
    awaitClose { observers.forEach(center::removeObserver) }
}.distinctUntilChanged()
