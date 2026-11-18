package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ViolationReportTest {

    private data class Tick(val n: Int) : Signal
    private data object Boom : Intent
    private data object Orphan : Intent
    private data object Load : Intent
    private data class Loaded(val ok: Boolean) : Signal

    private val counter = capability("counter", 0) {
        on<Tick> { s, f -> (s + f.n).commit() }
        on<Boom> { _, _ -> error("bug") }
        on<Load> { s, _ -> s.commit(work(WorkKey("load"), onError = { Loaded(false) }) { error("offline") }) }
        on<Loaded> { s, _ -> s.commit() }
    }

    private class Harness(
        val runtime: CapsaRuntime,
        val state: StateFlow<Int>,
        val errors: List<Throwable>,
        val scope: CoroutineScope,
    )

    private fun TestScope.start(config: CapsaConfig = CapsaConfig()): Harness {
        val errors = mutableListOf<Throwable>()
        val scope = CoroutineScope(
            StandardTestDispatcher(testScheduler) + SupervisorJob() + CoroutineExceptionHandler { _, e -> errors += e },
        )
        val builder = CapsaBuilder()
        val state = builder.install(counter)
        return Harness(runtime = builder.build(scope, config), state = state, errors = errors, scope = scope)
    }

    @Test
    fun violationCarriesTheHistoryAndTheOriginalErrorAsCause() = runTest {
        val rt = start()

        rt.runtime.append(Tick(1))
        rt.runtime.append(Boom)
        rt.runtime.append(Tick(2))
        testScheduler.runCurrent()

        val error = assertIs<CapsaViolationException>(rt.errors.single())
        assertEquals("bug", error.cause?.message)
        assertEquals(
            listOf("Signal Started -> nobody", "Signal Tick(n=1) -> counter", "Intent Boom -> counter"),
            error.history,
        )
        assertTrue(error.message.orEmpty().startsWith("DecideFailed: counter failed on Intent Boom\nCapsa history, newest last:"))
        assertEquals(3, rt.state.value)
        rt.scope.cancel()
    }

    @Test
    fun unhandledFactGetsAFrameWithItsClassSoReportsGroupByFact() = runTest {
        val rt = start()

        rt.runtime.append(Orphan)
        testScheduler.runCurrent()

        val error = assertIs<CapsaViolationException>(rt.errors.single())
        assertNull(error.cause)
        // Only the JVM and Android have a writable stack trace, see prependFrame.
        error.topFrame()?.let { (className, methodName) ->
            assertEquals(Orphan.platformClassName(), className)
            assertEquals("unhandledFact", methodName)
        }
        rt.scope.cancel()
    }

    @Test
    fun decideFailureKeepsTheRealStackOnTop() = runTest {
        val rt = start()

        rt.runtime.append(Boom)
        testScheduler.runCurrent()

        val error = assertIs<CapsaViolationException>(rt.errors.single())
        assertTrue(error.topFrame()?.second != "unhandledFact")
        rt.scope.cancel()
    }

    @Test
    fun failedWorkIsObservedAndLandsInTheHistory() = runTest {
        val failures = mutableListOf<Pair<String, Throwable>>()
        val observer = object : CapsaObserver {
            override fun onWorkFailed(capability: String, key: WorkKey, error: Throwable) {
                failures += "$capability/${key.name}" to error
            }
        }
        val rt = start(CapsaConfig(observer = observer))

        rt.runtime.append(Load)
        testScheduler.runCurrent()
        rt.runtime.append(Orphan)
        testScheduler.runCurrent()

        assertEquals("counter/load", failures.single().first)
        assertEquals("offline", failures.single().second.message)
        val history = assertIs<CapsaViolationException>(rt.errors.single()).history
        assertTrue("counter: start work \"load\"" in history)
        assertTrue("counter: work \"load\" failed with ${IllegalStateException("offline")}" in history)
        rt.scope.cancel()
    }

    @Test
    fun failingObserverDoesNotStopTheFact() = runTest {
        val observer = object : CapsaObserver {
            override fun onFact(fact: Fact) = error("observer bug")
        }
        val rt = start(CapsaConfig(observer = observer))

        rt.runtime.append(Tick(5))
        testScheduler.runCurrent()

        assertEquals(5, rt.state.value)
        assertTrue(rt.errors.isNotEmpty())
        assertTrue(rt.errors.all { it.message == "observer bug" })
        rt.scope.cancel()
    }

    @Test
    fun quietOnViolationKeepsTheRuntimeSilent() = runTest {
        val reported = mutableListOf<CapsaViolationException>()
        val rt = start(CapsaConfig(onViolation = { reported += it }))

        rt.runtime.append(Boom)
        rt.runtime.append(Tick(1))
        testScheduler.runCurrent()

        assertIs<CapsaViolation.DecideFailed>(reported.single().violation)
        assertTrue(rt.errors.isEmpty())
        assertEquals(1, rt.state.value)
        rt.scope.cancel()
    }

    @Test
    fun historyKeepsTheNewestEntriesInOrder() {
        val history = CapsaHistory(capacity = 3)
        val counterHolder = CapabilityHolder(counter, null)

        (1..5).forEach { history.add(CapsaHistory.FactHandled(Tick(it), listOf(counterHolder))) }

        assertEquals(
            listOf("Signal Tick(n=3) -> counter", "Signal Tick(n=4) -> counter", "Signal Tick(n=5) -> counter"),
            history.render(),
        )
    }

    @Test
    fun longFactTextIsClipped() {
        val long = object : Signal {
            override fun toString() = "x".repeat(1000)
        }

        val text = describeFact(long)

        assertEquals("Signal " + "x".repeat(300) + "…", text)
    }

    @Test
    fun exceptionKeepsTheViolation() {
        val violation = CapsaViolation.UnhandledFact(Orphan)

        assertSame(violation, CapsaViolationException(violation).violation)
    }
}
