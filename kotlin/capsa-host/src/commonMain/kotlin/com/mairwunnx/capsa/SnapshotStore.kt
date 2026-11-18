package com.mairwunnx.capsa

import androidx.savedstate.SavedState

/**
 * Where a host keeps the snapshots of [CapsaHostCore.installSaved]: `SavedStateHandle` for a view model,
 * the saveable state registry of the composition for a `CapsaComposable`.
 */
@InternalCapsaApi
public interface SnapshotStore {

    /** Returns the snapshot that the platform restored for `key`, or `null`. */
    public fun restored(key: String): SavedState?

    /** Registers `provide`, which encodes the snapshot of `key` when the platform saves the screen. */
    public fun register(key: String, provide: () -> SavedState)
}
