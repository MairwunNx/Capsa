package com.mairwunnx.capsa.viewmodel

import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.processUnhandledException

@OptIn(ExperimentalNativeApi::class)
internal actual fun deliverUncaughtToPlatform(error: Throwable) {
    processUnhandledException(error)
}

// Kotlin/Native prints the hash code as an unsigned hex number, like the JVM.
internal actual fun Any.hasDefaultToString(): Boolean = toString().endsWith("@" + hashCode().toUInt().toString(HEX_RADIX))

private const val HEX_RADIX = 16
