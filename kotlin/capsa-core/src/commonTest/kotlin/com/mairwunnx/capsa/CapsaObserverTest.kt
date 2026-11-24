package com.mairwunnx.capsa

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame

class CapsaObserverTest {

    private data object Tapped : Intent

    private class Recording(private val name: String, private val log: MutableList<String>) : CapsaObserver {
        override fun onFact(fact: Fact) {
            log += "$name: $fact"
        }

        override fun onWorkFailed(capability: String, key: WorkKey, error: Throwable) {
            log += "$name: ${key.name} failed"
        }
    }

    @Test
    fun ofPassesEachEventToEveryObserverInOrder() {
        val log = mutableListOf<String>()
        val observer = CapsaObserver.of(Recording("journal", log), Recording("metrics", log))

        observer.onFact(Tapped)
        observer.onWorkFailed("likes", WorkKey("save"), IllegalStateException("offline"))

        assertEquals(listOf("journal: Tapped", "metrics: Tapped", "journal: save failed", "metrics: save failed"), log)
    }

    @Test
    fun ofWithoutObserversIsNone() {
        assertSame(CapsaObserver.None, CapsaObserver.of())
        assertSame(CapsaObserver.None, CapsaObserver.of(CapsaObserver.None, CapsaObserver.None))
    }

    @Test
    fun ofOneObserverReturnsItAsItIs() {
        val journal = Recording("journal", mutableListOf())

        assertSame(journal, CapsaObserver.of(journal, CapsaObserver.None))
    }

    @Test
    fun aFailingObserverDoesNotKeepTheEventFromTheOthers() {
        val log = mutableListOf<String>()
        val broken = object : CapsaObserver {
            override fun onFact(fact: Fact) = error("broken")
        }
        val observer = CapsaObserver.of(broken, Recording("metrics", log))

        val error = assertFailsWith<IllegalStateException> { observer.onFact(Tapped) }

        assertEquals("broken", error.message)
        assertEquals(listOf("metrics: Tapped"), log)
    }
}
