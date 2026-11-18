package com.mairwunnx.capsa.viewmodel

import kotlinx.coroutines.flow.Flow
import platform.AppKit.NSApp
import platform.AppKit.NSApplicationDidChangeOcclusionStateNotification
import platform.AppKit.NSApplicationOcclusionStateVisible

/**
 * The app is in the foreground while at least part of one of its windows is on screen. macOS reports it as the occlusion
 * state of the app, which covers a hidden app (⌘H), windows minimized to the Dock, closed windows of a process that keeps
 * running, and windows that other apps or another Space cover completely.
 */
internal actual fun appForeground(): Flow<Boolean>? = appStateFlow(
    changes = mapOf(NSApplicationDidChangeOcclusionStateNotification to ::readForeground),
    initial = ::readForeground,
)

/** Reads the occlusion state of a running app, or `null` without one: `NSApp` stays `nil` until something creates the app. */
private fun readForeground(): Boolean? = NSApp?.let { it.occlusionState and NSApplicationOcclusionStateVisible != 0uL }
