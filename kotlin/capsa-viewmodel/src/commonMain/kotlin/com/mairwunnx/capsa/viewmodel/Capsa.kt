package com.mairwunnx.capsa.viewmodel

import kotlinx.atomicfu.atomic

/** Settings that every Capsa screen of the app shares. */
public object Capsa {

    private val failFastValue = atomic(detectFailFast())

    /**
     * Decides what happens to a bug that Capsa catches: a failing `decide`, projection, snapshot, or effect handler.
     *
     * With `true`, Capsa crashes the app at once, so a bug can’t hide during development. With `false`, Capsa is crash-free:
     * it hands the error to the logger, and the screen keeps working on its last valid state. Debug builds want the first,
     * release builds the second.
     *
     * Capsa detects the build on its own. On Android, `CapsaInitializer` reads the `debuggable` flag of the app at startup.
     * On iOS and macOS, Capsa checks `Platform.isDebugBinary`. On the JVM, the value is `false`. When detection fails,
     * the value stays `false`, so a release build never crashes because of it.
     *
     * The app can set the value itself, for example `Capsa.failFast = BuildConfig.DEBUG` in `Application.onCreate`.
     * A [CapsaViewModel] reads it once, when the view model is created.
     */
    public var failFast: Boolean
        get() = failFastValue.value
        set(value) {
            failFastValue.value = value
        }
}

/** Returns the value of [Capsa.failFast] before the app or the platform sets it. */
internal expect fun detectFailFast(): Boolean
