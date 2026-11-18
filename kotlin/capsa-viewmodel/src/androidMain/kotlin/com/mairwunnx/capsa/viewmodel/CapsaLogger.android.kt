package com.mairwunnx.capsa.viewmodel

import android.util.Log

internal actual fun writeToPlatformLog(level: CapsaLogger.Level, message: String, error: Throwable?) {
    when (level) {
        CapsaLogger.Level.Debug -> Log.d(LOG_TAG, message, error)
        CapsaLogger.Level.Error -> Log.e(LOG_TAG, message, error)
    }
}
