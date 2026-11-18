package com.mairwunnx.capsa.viewmodel

import kotlin.experimental.ExperimentalNativeApi

// A debug framework fails fast, a release one stays crash-free.
@OptIn(ExperimentalNativeApi::class)
internal actual fun detectFailFast(): Boolean = Platform.isDebugBinary
