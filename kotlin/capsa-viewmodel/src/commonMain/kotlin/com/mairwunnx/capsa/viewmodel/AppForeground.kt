package com.mairwunnx.capsa.viewmodel

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.flow.Flow

/**
 * Tells whether the app is in the foreground: `true` in the foreground, `false` in the background.
 *
 * Android reads `ProcessLifecycleOwner`, iOS and macOS listen to the notifications of the app. The JVM returns `null`:
 * a desktop app has no single foreground, and the collectors of the screen decide alone.
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
