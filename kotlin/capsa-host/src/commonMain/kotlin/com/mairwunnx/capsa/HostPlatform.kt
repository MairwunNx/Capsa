package com.mairwunnx.capsa

/**
 * Hands `error` to the platform as an uncaught error: the handler of the current thread on the JVM and Android,
 * the unhandled exception hook on Kotlin/Native. Both crash the app by default.
 */
internal expect fun deliverUncaughtToPlatform(error: Throwable)

/**
 * Returns `true` if the class doesn’t override `toString()`, so it prints the class name, `@`, and the hash code.
 *
 * The JVM and Android compare the line with the binary name of the class. Kotlin/Native names an anonymous class
 * in a way that reflection can’t read, so there the hash code at the end of the line decides.
 */
internal expect fun Any.hasDefaultToString(): Boolean
