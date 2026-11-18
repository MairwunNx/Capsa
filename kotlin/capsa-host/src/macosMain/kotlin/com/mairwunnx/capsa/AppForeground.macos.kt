package com.mairwunnx.capsa

import kotlinx.coroutines.flow.Flow
import platform.AppKit.NSApp
import platform.AppKit.NSApplicationDidChangeOcclusionStateNotification
import platform.AppKit.NSApplicationDidHideNotification
import platform.AppKit.NSApplicationDidUnhideNotification
import platform.AppKit.NSWindow
import platform.AppKit.NSWindowDidDeminiaturizeNotification
import platform.AppKit.NSWindowDidMiniaturizeNotification
import platform.AppKit.NSWindowWillCloseNotification
import platform.Foundation.NSNotification

/**
 * The app is in the foreground while it isn’t hidden (⌘H) and at least one of its windows is open and not minimized
 * to the Dock. A window that another app covers still counts: the user may move it aside at any moment.
 * So all windows minimized or closed, while the process keeps running, hide the screen.
 *
 * A closing window still reports itself as visible, so the reader skips it. A change of occlusion follows every other way
 * a window appears or goes away, such as a new window or `orderOut`, and the reader checks the windows again then.
 */
internal actual fun appForeground(): Flow<Boolean>? = appStateFlow(
    changes = mapOf(
        NSApplicationDidHideNotification to ::readForeground,
        NSApplicationDidUnhideNotification to ::readForeground,
        NSWindowDidMiniaturizeNotification to ::readForeground,
        NSWindowDidDeminiaturizeNotification to ::readForeground,
        NSWindowWillCloseNotification to ::readForeground,
        NSApplicationDidChangeOcclusionStateNotification to ::readForeground,
    ),
    initial = { readForeground(null) },
)

/**
 * Reads whether the app shows a window, or `null` without a running app: `NSApp` stays `nil` until something creates
 * the app. The window of a `WillClose` notification counts as gone.
 */
private fun readForeground(notification: NSNotification?): Boolean? {
    val app = NSApp ?: return null
    val closing = notification?.takeIf { it.name == NSWindowWillCloseNotification }?.`object`
    return !app.hidden && app.windows.any { it is NSWindow && it !== closing && it.visible && !it.miniaturized }
}
