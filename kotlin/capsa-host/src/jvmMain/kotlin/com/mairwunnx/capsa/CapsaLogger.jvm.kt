package com.mairwunnx.capsa

// The same shape as a Logcat line: "D/Capsa: message".
internal actual fun writeToPlatformLog(level: CapsaLogger.Level, message: String, error: Throwable?) {
    System.err.println("${level.name.first()}/$LOG_TAG: $message")
    error?.printStackTrace()
}
