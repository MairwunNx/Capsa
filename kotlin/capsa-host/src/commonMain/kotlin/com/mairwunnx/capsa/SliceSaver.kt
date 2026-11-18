package com.mairwunnx.capsa

import kotlinx.serialization.KSerializer

/**
 * Turns the state of a capability into a snapshot for `SavedStateHandle` and back.
 *
 * The snapshot keeps what the user typed and decided on the screen. Data from the network is cheaper to load again
 * after the process comes back.
 *
 * ```
 * @Serializable
 * data class FormSaved(val name: String, val email: String)
 *
 * object FormSaver : SliceSaver<FormSlice, FormSaved> {
 *     override val serializer = FormSaved.serializer()
 *     override fun save(state: FormSlice) = FormSaved(state.name, state.email)
 *     override fun restore(saved: FormSaved) = FormSlice(name = saved.name, email = saved.email)
 * }
 * ```
 *
 * @param S the state of the capability
 * @param P the snapshot
 */
public interface SliceSaver<S : Any, P : Any> {

    /** Writes the snapshot into `SavedStateHandle` and reads it back, on every platform. */
    public val serializer: KSerializer<P>

    /**
     * Makes a snapshot of `state`, or returns `null` if there is nothing to save.
     *
     * @param state the current state of the capability
     * @return the snapshot for `SavedStateHandle`, or `null`
     */
    public fun save(state: S): P?

    /**
     * Restores the initial state of the capability from `saved`.
     *
     * The result usually holds the saved input, but not the network data of the final state.
     *
     * @param saved the snapshot from `SavedStateHandle`
     * @return the state to install the capability with
     */
    public fun restore(saved: P): S
}
