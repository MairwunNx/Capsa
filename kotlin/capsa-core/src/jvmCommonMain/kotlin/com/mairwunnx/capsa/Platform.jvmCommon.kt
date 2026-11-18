package com.mairwunnx.capsa

import kotlin.reflect.KClass

@PublishedApi
internal actual fun KClass<*>.overlaps(other: KClass<*>): Boolean =
    java.isAssignableFrom(other.java) || other.java.isAssignableFrom(java)
