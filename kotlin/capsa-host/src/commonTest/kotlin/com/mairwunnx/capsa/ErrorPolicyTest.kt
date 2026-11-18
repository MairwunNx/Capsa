package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorPolicyTest {

    @Test
    fun failFastHandsTheErrorToThePreviousHandlerOtherwiseItIsReported() {
        val previous = mutableListOf<Throwable>()
        val reported = mutableListOf<Throwable>()
        val fallback = CoroutineExceptionHandler { _, e -> previous += e }
        val error = IllegalStateException("bug")

        capsaExceptionHandler(failFast = true, fallback = fallback, report = { reported += it })
            .handleException(EmptyCoroutineContext, error)
        capsaExceptionHandler(failFast = false, fallback = fallback, report = { reported += it })
            .handleException(EmptyCoroutineContext, error)

        assertEquals(listOf<Throwable>(error), previous)
        assertEquals(listOf<Throwable>(error), reported)
    }
}
