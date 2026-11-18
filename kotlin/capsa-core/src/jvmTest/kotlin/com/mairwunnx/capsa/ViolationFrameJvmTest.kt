package com.mairwunnx.capsa

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The common [ViolationReportTest] skips the frame check where the stack is empty, as on Kotlin/Native.
 * On the JVM the stack is always there, so this test demands it.
 */
class ViolationFrameJvmTest {

    private data object Orphan : Intent

    @Test
    fun unhandledFactReportStartsWithAFrameOfTheFactClass() = runTest {
        val reported = mutableListOf<CapsaViolationException>()
        val runtime = CapsaBuilder().build(backgroundScope, CapsaConfig(onViolation = { reported += it }))

        runtime.append(Orphan)
        testScheduler.runCurrent()

        val stack = reported.single().stackTrace
        assertTrue(stack.size > 1, "the report keeps the real stack under the synthetic frame")
        val top = stack.first()
        assertEquals(Orphan::class.java.name, top.className)
        assertEquals("unhandledFact", top.methodName)
        assertEquals("Capsa", top.fileName)
    }
}
