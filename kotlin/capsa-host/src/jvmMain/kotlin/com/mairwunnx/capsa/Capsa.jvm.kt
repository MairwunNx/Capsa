package com.mairwunnx.capsa

// A JVM app has no debug flag to read, so it stays crash-free unless it sets Capsa.failFast itself.
internal actual fun detectFailFast(): Boolean = false
