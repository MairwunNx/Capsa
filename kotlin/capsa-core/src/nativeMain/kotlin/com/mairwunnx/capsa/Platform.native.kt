package com.mairwunnx.capsa

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.processUnhandledException
import kotlin.reflect.KClass

// Kotlin/Native can’t tell whether one class inherits another, see the expect declaration.
@PublishedApi
internal actual fun KClass<*>.overlaps(other: KClass<*>): Boolean = false

@OptIn(ExperimentalNativeApi::class)
internal actual fun deliverUncaughtToPlatform(error: Throwable) {
    processUnhandledException(error)
}

// Kotlin/Native terminates the process on its own when the memory runs out.
internal actual fun Throwable.isFatal(): Boolean = false

internal actual fun Throwable.prependFrame(className: String, methodName: String, fileName: String) = Unit

internal actual fun Any.platformClassName(): String = this::class.qualifiedName ?: this::class.simpleName ?: "anonymous"
