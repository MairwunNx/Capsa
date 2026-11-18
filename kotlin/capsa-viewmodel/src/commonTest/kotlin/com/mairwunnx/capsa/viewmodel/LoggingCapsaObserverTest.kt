package com.mairwunnx.capsa.viewmodel

import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.Signal
import com.mairwunnx.capsa.WorkKey
import com.mairwunnx.capsa.commit
import com.mairwunnx.capsa.work
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LoggingCapsaObserverTest {

    private data class Count(val value: Int)
    private data class Plus(val by: Int) : Intent
    private data object Close : Effect
    private data object Open : Effect
    private data object Done : Signal

    private val load = WorkKey("load")

    @Test
    fun factLineNamesWhereTheEventCameFrom() {
        assertEquals("Intent Plus(by=2)", formatFact(Plus(2)))
        assertEquals("Signal Done", formatFact(Done))
    }

    @Test
    fun decisionWithoutEffectsAndSignalsIsASingleStateLine() {
        assertEquals(listOf("  counter: state Count(value=1)"), formatDecision("counter", Count(1).commit()))
    }

    @Test
    fun everyEffectAndSignalGetsItsOwnLineAndWorksAreLeftToTheWorkLines() {
        val decision = Count(0).commit(
            effects = listOf(Close, Open),
            works = listOf(work(load, onError = { Done }) { Done }),
            cancel = listOf(load),
            signals = listOf(Done),
        )

        assertEquals(
            listOf(
                "  counter: state Count(value=0)",
                "  counter: send effect Close",
                "  counter: send effect Open",
                "  counter: emit signal Done",
            ),
            formatDecision("counter", decision),
        )
    }

    @Test
    fun workLineSaysWhatHappenedToWhichWork() {
        assertEquals("  timer: start work \"load\"", formatWork("timer", load, started = true))
        assertEquals("  timer: cancel work \"load\"", formatWork("timer", load, started = false))
    }

    @Test
    fun failedWorkLineNamesTheError() {
        val error = IllegalStateException("offline")

        assertEquals("  stats: work \"load\" failed with $error", formatWorkFailed("stats", load, error))
    }

    @Test
    fun factWithoutReadableToStringIsRejected() {
        val plain = object : Intent {}

        assertFailsWith<IllegalStateException> { checkReadable(plain) }
        checkReadable(Plus(1))
        checkReadable(Done)
    }
}
