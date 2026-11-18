package com.mairwunnx.capsa

/** Returns the class and the method of the top stack frame, or `null` where the platform has no readable stack trace. */
internal expect fun Throwable.topFrame(): Pair<String, String>?
