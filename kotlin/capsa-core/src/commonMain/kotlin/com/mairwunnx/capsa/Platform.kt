package com.mairwunnx.capsa

import kotlin.reflect.KClass

/**
 * Returns `true` if one of the classes inherits the other.
 *
 * The JVM and Android compare the classes exactly. Kotlin/Native has no reflection for that and always returns `false`:
 * there the builders catch only two registrations of the same class, and a fact or an effect that matches
 * two of them fails when it arrives.
 */
internal expect fun KClass<*>.overlaps(other: KClass<*>): Boolean

/**
 * Hands `error` to the platform as an uncaught error: the handler of the current thread on the JVM and Android,
 * the unhandled exception hook on Kotlin/Native.
 */
internal expect fun deliverUncaughtToPlatform(error: Throwable)

/**
 * Returns `true` for an error after which the process can’t go on.
 *
 * On the JVM and Android these are `VirtualMachineError` except `StackOverflowError` (out of memory, an internal VM failure)
 * and `LinkageError` (a broken or missing class). Kotlin/Native has no such errors: it terminates on its own.
 */
internal expect fun Throwable.isFatal(): Boolean

/**
 * Puts a synthetic frame on top of the stack trace, so crash reports group by it.
 *
 * Only the JVM and Android have a writable stack trace. Kotlin/Native leaves the exception as it is.
 */
internal expect fun Throwable.prependFrame(className: String, methodName: String, fileName: String)

/** Returns the runtime name of the class: the binary name on the JVM and Android, the qualified name on Kotlin/Native. */
internal expect fun Any.platformClassName(): String
