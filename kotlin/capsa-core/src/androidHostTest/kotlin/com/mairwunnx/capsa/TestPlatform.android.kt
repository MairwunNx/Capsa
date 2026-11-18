package com.mairwunnx.capsa

internal actual fun Throwable.topFrame(): Pair<String, String>? = stackTrace.firstOrNull()?.let { it.className to it.methodName }
