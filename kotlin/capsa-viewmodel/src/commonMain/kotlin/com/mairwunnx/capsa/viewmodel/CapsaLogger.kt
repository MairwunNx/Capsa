package com.mairwunnx.capsa.viewmodel

/**
 * Receives what Capsa writes: the runtime journal at [Level.Debug] and the errors at [Level.Error].
 *
 * `message` is lazy. Call it only for a line you write: the journal builds a string for every fact,
 * so a logger that skips a level costs almost nothing.
 *
 * ```
 * val logger = CapsaLogger { level, error, message ->
 *     if (level == CapsaLogger.Level.Error) crashReporter.record(error, message())
 * }
 * ```
 *
 * Without a logger of its own, a [CapsaViewModel] uses [Default].
 */
public fun interface CapsaLogger {

    /**
     * Writes a line at `level`.
     *
     * @param level how important the line is
     * @param error the error the line is about, or `null`
     * @param message builds the text of the line
     */
    public fun log(level: Level, error: Throwable?, message: () -> String)

    /** How important a line is. */
    public enum class Level {
        /** The runtime journal: facts, decisions, works. */
        Debug,

        /** A bug that Capsa caught and survived. */
        Error,
    }

    public companion object {

        /**
         * Writes to the platform log with the tag `Capsa`: Logcat on Android, `NSLog` on iOS and macOS, `System.err` on the JVM.
         *
         * Errors always go out. The journal goes out only while [Capsa.failFast] is on, that is, in a debug build:
         * a release build neither spends time on it nor puts the user’s data in the system log.
         */
        public val Default: CapsaLogger = CapsaLogger { level, error, message ->
            if (level == Level.Error || Capsa.failFast) writeToPlatformLog(level, message(), error)
        }
    }
}

/** The tag of every line in the platform log. */
internal const val LOG_TAG: String = "Capsa"

/** Writes `message` and `error` to the platform log with [LOG_TAG]. */
internal expect fun writeToPlatformLog(level: CapsaLogger.Level, message: String, error: Throwable?)
