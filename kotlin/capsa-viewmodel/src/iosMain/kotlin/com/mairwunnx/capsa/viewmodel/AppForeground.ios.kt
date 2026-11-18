package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.flow.Flow
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillEnterForegroundNotification

/**
 * The app is in the foreground unless iOS sent it to the background. An inactive app, for example under the Control Center,
 * is still on screen. The notifications carry the new state themselves: during `WillEnterForeground` the app still reports
 * the background.
 */
internal actual fun appForeground(): Flow<Boolean>? = appStateFlow(
    changes = mapOf(
        UIApplicationDidEnterBackgroundNotification to { _ -> false },
        UIApplicationWillEnterForegroundNotification to { _ -> true },
    ),
    initial = ::readForeground,
)

/** Reads the state of a running app, or `null` without one, as in a test binary or an app extension. */
private fun readForeground(): Boolean? {
    val app: UIApplication? = UIApplication.sharedApplication
    return app?.let { it.applicationState != UIApplicationState.UIApplicationStateBackground }
}
