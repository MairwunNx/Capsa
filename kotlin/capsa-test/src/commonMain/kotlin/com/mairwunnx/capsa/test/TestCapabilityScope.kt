package com.mairwunnx.capsa.test

import com.mairwunnx.capsa.CapabilityScope

/**
 * Provides a [CapabilityScope] to create capabilities in a test.
 *
 * In an app, a host provides this scope: `CapsaViewModel`, `CapsaComposable`, or the lambda of `rememberCapsa`.
 */
public object TestCapabilityScope : CapabilityScope

/**
 * Runs `block` in [TestCapabilityScope] and returns its result.
 *
 * With it, a test calls the constructor functions that take the context parameter [CapabilityScope].
 *
 * ```
 * private val likes = inCapabilityScope { likes(loadLikes = { flowOf(...) }) }
 * ```
 *
 * @param block the block that creates the capability
 * @return the result of `block`
 */
public inline fun <T> inCapabilityScope(block: CapabilityScope.() -> T): T = TestCapabilityScope.block()
