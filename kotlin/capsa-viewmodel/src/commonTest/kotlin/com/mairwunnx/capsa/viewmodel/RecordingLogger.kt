package com.mairwunnx.capsa.viewmodel

import com.mairwunnx.capsa.Capsa
import com.mairwunnx.capsa.CapsaLogger
/** Keeps every line it receives, so a test can check what Capsa wrote and at which level. */
internal class RecordingLogger : CapsaLogger {

    class Line(val level: CapsaLogger.Level, val error: Throwable?, val message: String)

    val lines = mutableListOf<Line>()

    val errors: List<Throwable?>
        get() = lines.filter { it.level == CapsaLogger.Level.Error }.map { it.error }

    override fun log(level: CapsaLogger.Level, error: Throwable?, message: () -> String) {
        lines += Line(level, error, message())
    }
}
