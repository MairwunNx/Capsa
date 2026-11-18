package com.mairwunnx.capsa.viewmodel

// Kotlin/Native prints the hash code as an unsigned hex number, like the JVM.
internal actual fun Any.hasDefaultToString(): Boolean = toString().endsWith("@" + hashCode().toUInt().toString(HEX_RADIX))

private const val HEX_RADIX = 16
