package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CapabilityDslTest {

    private data class Count(val value: Int = 0)
    private data class Plus(val by: Int) : Intent
    private data object Reset : Intent
    private data object Unknown : Intent
    private data class Done(val value: Int) : Effect

    private val counter = capability("counter", Count()) {
        on<Plus> { state, fact -> state.copy(value = state.value + fact.by).commit() }
        on<Reset> { state, _ -> Count().commit(effects = listOf(Done(state.value))) }
    }

    @Test
    fun handlesIsDerivedFromReactions() {
        assertEquals(setOf(Plus::class, Reset::class), counter.handles)
    }

    @Test
    fun decideRoutesTheFactToItsReaction() {
        val decision = counter.decide(Count(1), Plus(2))

        assertEquals(Count(3), decision.state)
        assertTrue(decision.effects.isEmpty())
    }

    @Test
    fun reactionCanEmitAnEffect() {
        val decision = counter.decide(Count(5), Reset)

        assertEquals(Count(0), decision.state)
        assertEquals(listOf<Effect>(Done(5)), decision.effects)
    }

    @Test
    fun factOutsideHandlesFailsLoudlyInADirectCall() {
        assertFailsWith<IllegalStateException> { counter.decide(Count(), Unknown) }
    }

    @Test
    fun duplicateReactionIsRejectedAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            capability("dup", Count()) {
                on<Plus> { state, _ -> state.commit() }
                on<Plus> { state, _ -> state.commit() }
            }
        }
    }

    @Test
    fun reactionOnASupertypeCatchesItsSubtypes() {
        val anyIntent = capability("any", Count()) {
            on<Intent> { state, _ -> state.copy(value = state.value + 1).commit() }
        }

        assertEquals(Count(1), anyIntent.decide(Count(), Plus(10)).state)
        assertEquals(Count(1), anyIntent.decide(Count(), Reset).state)
    }

    @Test
    fun commitKeepsCancelAndWorksInTheDecision() {
        val key = WorkKey("load")
        val decision = Count().commit(
            works = listOf(work(key, onError = { Started }) { Started }),
            cancel = listOf(WorkKey("old")),
        )

        assertEquals(listOf(key), decision.works.map { it.key })
        assertEquals(listOf(WorkKey("old")), decision.cancels)
    }

    @Test
    fun reactionsOnASupertypeAndItsSubtypeAreRejected() {
        val supertypeFirst: CapabilityBuilder<Count>.() -> Unit = {
            on<Intent> { state, _ -> state.commit() }
            on<Plus> { state, _ -> state.commit() }
        }
        val subtypeFirst: CapabilityBuilder<Count>.() -> Unit = {
            on<Plus> { state, _ -> state.commit() }
            on<Intent> { state, _ -> state.commit() }
        }
        for (build in listOf(supertypeFirst, subtypeFirst)) {
            if (Intent::class.overlaps(Plus::class)) {
                assertFailsWith<IllegalArgumentException> { capability("overlap", Count(), build) }
            } else {
                // Kotlin/Native can’t compare classes, so the overlap fails when the fact arrives.
                assertFailsWith<IllegalStateException> { capability("overlap", Count(), build).decide(Count(), Plus(1)) }
            }
        }
    }

    @Test
    fun singleWorkEffectAndSignalOverloadsFillTheirLists() {
        val key = WorkKey("load")

        assertEquals(listOf(key), Count().commit(work(key, onError = { Started }) { Started }).works.map { it.key })
        assertEquals(listOf<Effect>(Done(1)), Count().commit(Done(1)).effects)
        assertEquals(listOf<Signal>(Started), Count().commit(Started).signals)
    }

    private interface Liked : Intent
    private interface Shared : Intent
    private data object LikedAndShared : Liked, Shared

    @Test
    fun factThatMatchesTwoUnrelatedReactionsIsRejectedInDecide() {
        val social = capability("social", 0) {
            on<Liked> { s, _ -> (s + 1).commit() }
            on<Shared> { s, _ -> (s + 10).commit() }
        }

        val error = assertFailsWith<IllegalStateException> { social.decide(0, LikedAndShared) }

        assertTrue(error.message.orEmpty().contains("Liked"))
        assertTrue(error.message.orEmpty().contains("Shared"))
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun builderRejectsInstallAndASecondBuildAfterBuild() {
        val scope = CoroutineScope(Job())
        val builder = CapsaBuilder()
        builder.install(capability("first", 0) {})
        builder.build(scope)

        assertFailsWith<IllegalStateException> { builder.install(capability("late", 0) {}) }
        assertFailsWith<IllegalStateException> { builder.build(scope) }
        scope.cancel()
    }
}
