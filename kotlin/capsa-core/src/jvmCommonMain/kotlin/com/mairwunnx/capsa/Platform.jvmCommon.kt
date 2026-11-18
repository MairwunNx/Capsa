package com.mairwunnx.capsa

import kotlin.reflect.KClass

@PublishedApi
internal actual fun KClass<*>.overlaps(other: KClass<*>): Boolean =
    java.isAssignableFrom(other.java) || other.java.isAssignableFrom(java)

internal actual fun deliverUncaughtToPlatform(error: Throwable) {
    val thread = Thread.currentThread()
    thread.uncaughtExceptionHandler.uncaughtException(thread, error)
}

internal actual fun Throwable.isFatal(): Boolean = this is OutOfMemoryError

internal actual fun Throwable.prependFrame(className: String, methodName: String, fileName: String) {
    stackTrace = arrayOf(StackTraceElement(className, methodName, fileName, -1)) + stackTrace
}

internal actual fun Any.platformClassName(): String = javaClass.name
