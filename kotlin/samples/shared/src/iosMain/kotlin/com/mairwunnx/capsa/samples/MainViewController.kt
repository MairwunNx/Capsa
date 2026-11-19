package com.mairwunnx.capsa.samples

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

/** The iOS entry point: `ContentView.swift` shows this controller. */
fun mainViewController(): UIViewController = ComposeUIViewController { App() }
