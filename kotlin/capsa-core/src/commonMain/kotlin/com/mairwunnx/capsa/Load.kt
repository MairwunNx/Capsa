package com.mairwunnx.capsa

/**
 * Represents loading data in a slice: in progress, done, or failed.
 *
 * It holds neither the exception nor the progress, so transport details stay out of the capability’s state.
 */
public sealed interface Load<out T> {

    /** Shows that the request hasn’t returned the data yet. */
    public data object Loading : Load<Nothing>

    /** Holds the loaded `value`. */
    public data class Loaded<out T>(public val value: T) : Load<T>

    /** Shows a failed load without its transport cause. */
    public data object Failed : Load<Nothing>
}

/** Returns [Load.Loaded.value], or `null` for [Load.Loading] and [Load.Failed]. */
public fun <T> Load<T>.valueOrNull(): T? = (this as? Load.Loaded)?.value
