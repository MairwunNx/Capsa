package com.mairwunnx.capsa

import kotlin.reflect.KClass

/**
 * Returns `true` if one of the classes inherits the other.
 *
 * The JVM and Android compare the classes exactly. Kotlin/Native has no reflection for that and always returns `false`:
 * there the builders catch only two registrations of the same class, and a fact or an effect that matches
 * two of them fails when it arrives.
 */
@PublishedApi
internal expect fun KClass<*>.overlaps(other: KClass<*>): Boolean
