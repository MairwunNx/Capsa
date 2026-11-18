package com.mairwunnx.capsa.viewmodel

import platform.Foundation.NSLog

internal actual fun writeToPlatformLog(level: CapsaLogger.Level, message: String, error: Throwable?) {
    val line = buildString {
        append(level.name.first()).append('/').append(LOG_TAG).append(": ").append(message)
        if (error != null) append('\n').append(error.stackTraceToString())
    }
    // NSLog reads its first argument as a format string, so a % in a fact must not start a format specifier.
    NSLog(line.replace("%", "%%"))
}
