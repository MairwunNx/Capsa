package com.mairwunnx.capsa

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FatalErrorTest {

    @Test
    fun vmAndLinkageErrorsAreFatal() {
        assertTrue(OutOfMemoryError().isFatal())
        assertTrue(InternalError().isFatal())
        assertTrue(NoClassDefFoundError().isFatal())
        assertTrue(ExceptionInInitializerError().isFatal())
    }

    @Test
    fun stackOverflowAndOrdinaryErrorsAreNotFatal() {
        assertFalse(StackOverflowError().isFatal())
        assertFalse(IllegalStateException().isFatal())
        assertFalse(AssertionError().isFatal())
    }
}
