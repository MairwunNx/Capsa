package com.mairwunnx.capsa

internal actual fun deliverUncaughtToPlatform(error: Throwable) {
    val thread = Thread.currentThread()
    // A running thread always has a handler: its own one or its thread group.
    checkNotNull(thread.uncaughtExceptionHandler).uncaughtException(thread, error)
}

internal actual fun Any.hasDefaultToString(): Boolean = toString().startsWith(javaClass.name + "@")
