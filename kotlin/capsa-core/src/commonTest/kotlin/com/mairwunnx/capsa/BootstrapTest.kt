package com.mairwunnx.capsa

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** Checks [CapabilityBuilder.bootstrap]: the first decision of each capability, made while the runtime handles [Started]. */
class BootstrapTest {

    private sealed interface FeedIntent : Intent
    private data object Refresh : FeedIntent

    private sealed interface FeedSignal : Signal
    private data object Ready : FeedSignal

    private val violations = mutableListOf<CapsaViolation>()

    private fun TestScope.build(builder: CapsaBuilder): CapsaRuntime =
        builder.build(backgroundScope, CapsaConfig(visibility = CapsaVisibility.Host, onViolation = { violations += it.violation }))

    @Test
    @Suppress("IgnoredReturnValue")
    fun bootstrapsRunOnceInInstallOrderBeforeTheFirstFact() = runTest {
        val log = mutableListOf<String>()
        val feed = capability<Int, FeedIntent, Nothing>(name = "feed", initial = 0) {
            bootstrap { state ->
                log += "feed bootstrap"
                Decision(state = state)
            }
            onIntent<Refresh> { state, _ ->
                log += "feed refresh"
                Decision(state = state)
            }
        }
        val badge = capability<Int, Nothing, Nothing>(name = "badge", initial = 0) {
            bootstrap { state ->
                log += "badge bootstrap"
                Decision(state = state)
            }
        }
        val builder = CapsaBuilder()
        builder.install(feed)
        builder.install(badge)
        val runtime = build(builder)

        runtime.append(Refresh)
        testScheduler.runCurrent()
        runtime.append(Refresh)
        testScheduler.runCurrent()

        assertEquals(listOf("feed bootstrap", "badge bootstrap", "feed refresh", "feed refresh"), log)
        assertTrue(violations.isEmpty())
    }

    @Test
    fun bootstrapGetsTheInitialOrTheRestoredSliceAndWritesItsDecision() = runTest {
        val seen = mutableListOf<Pair<String, Int>>()
        fun counter(name: String) = capability<Int, Nothing, Nothing>(name = name, initial = 1) {
            bootstrap { state ->
                seen += name to state
                Decision(state = state * 10)
            }
        }
        val builder = CapsaBuilder()
        val fresh = builder.install(counter("fresh"))
        val restored = builder.install(counter("restored"), restored = 7)
        build(builder)

        testScheduler.runCurrent()

        assertEquals(listOf("fresh" to 1, "restored" to 7), seen)
        assertEquals(10, fresh.value)
        assertEquals(70, restored.value)
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun signalsOfTheBootstrapsAreHandledBeforeTheNextFact() = runTest {
        val log = mutableListOf<String>()
        val feed = capability<Int, FeedIntent, FeedSignal>(name = "feed", initial = 0) {
            bootstrap { state ->
                log += "feed bootstrap"
                Decision(state = state, signal = Ready)
            }
            onIntent<Refresh> { state, _ ->
                log += "feed refresh"
                Decision(state = state)
            }
        }
        val badge = capability<Int, Nothing, Nothing>(name = "badge", initial = 0) {
            bootstrap { state ->
                log += "badge bootstrap"
                Decision(state = state)
            }
            onSignal<Ready> { state, _ ->
                log += "badge ready"
                Decision(state = state + 1)
            }
        }
        val builder = CapsaBuilder()
        builder.install(feed)
        val badges = builder.install(badge)
        val runtime = build(builder)

        runtime.append(Refresh)
        testScheduler.runCurrent()

        assertEquals(listOf("feed bootstrap", "badge bootstrap", "badge ready", "feed refresh"), log)
        assertEquals(1, badges.value)
    }

    @Test
    fun throwingBootstrapReportsDecideFailedAndKeepsTheSlice() = runTest {
        val broken = capability<Int, Nothing, Nothing>(name = "broken", initial = 3) {
            bootstrap { error("bootstrap bug") }
        }
        val healthy = capability<Int, Nothing, Nothing>(name = "healthy", initial = 0) {
            bootstrap { state -> Decision(state = state + 1) }
        }
        val builder = CapsaBuilder()
        val brokenSlice = builder.install(broken)
        val healthySlice = builder.install(healthy)
        build(builder)

        testScheduler.runCurrent()

        val violation = assertIs<CapsaViolation.DecideFailed>(violations.single())
        assertEquals("broken", violation.capability)
        assertEquals(Started, violation.fact)
        assertEquals("bootstrap bug", violation.error.message)
        assertEquals(3, brokenSlice.value)
        assertEquals(1, healthySlice.value)
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun screenWithoutABootstrapReportsNoViolation() = runTest {
        val feed = capability<Int, FeedIntent, Nothing>(name = "feed", initial = 0) {
            onIntent<Refresh> { state, _ -> Decision(state = state + 1) }
        }
        val builder = CapsaBuilder()
        builder.install(feed)
        build(builder)

        testScheduler.runCurrent()

        assertTrue(violations.isEmpty())
    }
}
