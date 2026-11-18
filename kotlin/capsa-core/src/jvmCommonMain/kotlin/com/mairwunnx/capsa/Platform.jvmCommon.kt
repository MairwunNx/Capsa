package com.mairwunnx.capsa

import kotlin.reflect.KClass

@PublishedApi
internal actual fun KClass<*>.overlaps(other: KClass<*>): Boolean =
    java.isAssignableFrom(other.java) || other.java.isAssignableFrom(java)

internal actual fun deliverUncaughtToPlatform(error: Throwable) {
    val thread = Thread.currentThread()
    // A running thread always has a handler: its own one or its thread group.
    checkNotNull(thread.uncaughtExceptionHandler).uncaughtException(thread, error)
}

// After these errors the JVM or the loaded code can’t be trusted: out of memory, an internal failure of the VM,
// a broken or missing class. A stack overflow unwinds cleanly, so the runtime reports it like any other bug in a capability.
internal actual fun Throwable.isFatal(): Boolean =
    (this is VirtualMachineError && this !is StackOverflowError) || this is LinkageError

internal actual fun Throwable.prependFrame(className: String, methodName: String, fileName: String) {
    stackTrace = arrayOf(StackTraceElement(className, methodName, fileName, -1)) + stackTrace
}

internal actual fun Any.platformClassName(): String = javaClass.name
