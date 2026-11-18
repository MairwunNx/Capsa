package com.mairwunnx.capsa.viewmodel

/**
 * Returns `true` if the class doesn’t override `toString()`, so it prints the class name, `@`, and the hash code.
 *
 * The JVM and Android compare the line with the binary name of the class. Kotlin/Native names an anonymous class
 * in a way that reflection can’t read, so there the hash code at the end of the line decides.
 */
internal expect fun Any.hasDefaultToString(): Boolean
