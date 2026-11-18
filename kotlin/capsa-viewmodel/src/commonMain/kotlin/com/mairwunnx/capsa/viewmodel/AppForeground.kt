package com.mairwunnx.capsa.viewmodel

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow

/**
 * Tells whether the app is in the foreground: `true` in the foreground, `false` in the background.
 *
 * Android reads `ProcessLifecycleOwner`. iOS reads the state of the app and listens to its background notifications.
 * macOS looks at the windows of the app: a hidden app (⌘H) or all windows minimized to the Dock or closed, while
 * the process keeps running, hide the screen. A window that another app covers doesn’t. The JVM returns `null`:
 * Compose Desktop already drops the lifecycle of a minimized or hidden window to `CREATED`,
 * so `collectAsStateWithLifecycle()` and `CollectEffects` stop collecting, and the collectors decide alone.
 */
internal expect fun appForeground(): Flow<Boolean>?

/** Where [CapsaViewModel] learns about the foreground of the app. Tests replace the platform source with their own. */
internal object AppForeground {
    private val source = atomic<() -> Flow<Boolean>?>(::appForeground)

    fun flow(): Flow<Boolean>? = source.value()

    fun replace(flow: Flow<Boolean>?) {
        source.value = { flow }
    }

    fun reset() {
        source.value = ::appForeground
    }
}
