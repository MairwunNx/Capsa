package com.mairwunnx.capsa.viewmodel

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * `ProcessLifecycleOwner` drops below `STARTED` 700 ms after the last activity stops, so a rotation doesn’t count
 * as the background. `INITIALIZED` means that `androidx.startup` didn’t run its initializer, for example because the app
 * turned it off. Then the layer stays silent and the collectors of the screen decide alone.
 */
internal actual fun appForeground(): Flow<Boolean>? =
    ProcessLifecycleOwner.get().lifecycle.currentStateFlow
        .map { it == Lifecycle.State.INITIALIZED || it.isAtLeast(Lifecycle.State.STARTED) }
        .distinctUntilChanged()
