package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.flow.Flow
import platform.AppKit.NSApplicationDidHideNotification
import platform.AppKit.NSApplicationDidUnhideNotification

internal actual fun appForeground(): Flow<Boolean>? =
    foregroundNotifications(hidden = NSApplicationDidHideNotification, shown = NSApplicationDidUnhideNotification)
