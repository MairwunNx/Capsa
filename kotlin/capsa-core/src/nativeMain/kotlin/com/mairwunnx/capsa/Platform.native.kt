package com.mairwunnx.capsa

import kotlin.reflect.KClass

// Kotlin/Native can’t tell whether one class inherits another, see the expect declaration.
@PublishedApi
internal actual fun KClass<*>.overlaps(other: KClass<*>): Boolean = false
