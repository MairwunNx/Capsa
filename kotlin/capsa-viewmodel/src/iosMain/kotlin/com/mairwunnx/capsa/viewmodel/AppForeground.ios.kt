package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.flow.Flow
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification

internal actual fun appForeground(): Flow<Boolean>? =
    foregroundNotifications(hidden = UIApplicationDidEnterBackgroundNotification, shown = UIApplicationWillEnterForegroundNotification)
