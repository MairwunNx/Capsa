package com.mairwunnx.capsa

/** Returns the class and the method of every stack frame, top first, or an empty list where the platform has no readable stack trace. */
internal expect fun Throwable.frames(): List<Pair<String, String>>
