package com.mairwunnx.capsa.viewmodel

import android.content.Context
import android.content.pm.ApplicationInfo
import androidx.startup.Initializer

/**
 * Sets [Capsa.failFast] from the `debuggable` flag of the app: a debug build crashes on a Capsa bug, a release build doesn’t.
 *
 * `androidx.startup` runs the initializer before `Application.onCreate`, the manifest of `capsa-viewmodel` registers it.
 * An app that turns off automatic initializers sets [Capsa.failFast] itself.
 */
public class CapsaInitializer : Initializer<Unit> {

    override fun create(context: Context) {
        Capsa.failFast = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    override fun dependencies(): List<Class<out Initializer<*>>> = emptyList()
}
