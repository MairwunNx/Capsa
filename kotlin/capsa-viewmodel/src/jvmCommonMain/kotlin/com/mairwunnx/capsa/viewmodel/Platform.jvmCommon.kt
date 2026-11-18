package com.mairwunnx.capsa.viewmodel

internal actual fun Any.hasDefaultToString(): Boolean = toString().startsWith(javaClass.name + "@")
