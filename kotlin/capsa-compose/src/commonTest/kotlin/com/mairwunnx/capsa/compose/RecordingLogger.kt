package com.mairwunnx.capsa.compose

import com.mairwunnx.capsa.CapsaLogger

/** Keeps the errors it receives, so a test can check what reached the error policy of a release build. */
internal class RecordingLogger : CapsaLogger {

    val errors = mutableListOf<Throwable?>()

    override fun log(level: CapsaLogger.Level, error: Throwable?, message: () -> String) {
        if (level == CapsaLogger.Level.Error) errors += error
    }
}
