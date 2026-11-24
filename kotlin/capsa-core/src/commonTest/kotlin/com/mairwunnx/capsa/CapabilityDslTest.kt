package com.mairwunnx.capsa

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CapabilityDslTest {

    private data class Count(val value: Int = 0)

    private sealed interface CounterIntent : Intent
    private data class Plus(val by: Int) : CounterIntent
    private data object Reset : CounterIntent
    private data object Unknown : Intent

    private sealed interface CounterSignal : Signal
    private data object Loaded : CounterSignal
    private data object Changed : CounterSignal
    private data object Saved : CounterSignal

    private data class Done(val value: Int) : Effect

    private val counter = capability<Count, CounterIntent, Nothing>(name = "counter", initial = Count()) {
        onIntent<Plus> { state, intent -> Decision(state = state.copy(value = state.value + intent.by)) }
        onIntent<Reset> { state, _ -> Decision(state = Count(), effect = Done(state.value)) }
    }

    @Test
    fun handlesAndIntentsAreDerivedFromReactions() {
        assertEquals(setOf(Plus::class, Reset::class), counter.handles)
        assertEquals(setOf(Plus::class, Reset::class), counter.intents)
    }

    @Test
    fun signalReactionsAreHandledButAreNotIntents() {
        val loader = capability<Count, Nothing, CounterSignal>(name = "loader", initial = Count()) {
            onSignal<Loaded> { state, _ -> Decision(state = state) }
        }

        assertEquals(setOf(Loaded::class), loader.handles)
        assertTrue(loader.intents.isEmpty())
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
            capability<Count, CounterIntent, Nothing>(name = "dup", initial = Count()) {
                onIntent<Plus> { state, _ -> Decision(state = state) }
                onIntent<Plus> { state, _ -> Decision(state = state) }
            }
        }
    }

    @Test
    fun reactionOnASupertypeCatchesItsSubtypes() {
        val anyIntent = capability<Count, Intent, Nothing>(name = "any", initial = Count()) {
            onIntent<Intent> { state, _ -> Decision(state = state.copy(value = state.value + 1)) }
        }

        assertEquals(Count(1), anyIntent.decide(Count(), Plus(10)).state)
        assertEquals(Count(1), anyIntent.decide(Count(), Reset).state)
    }

    @Test
    fun reactionsOnASupertypeAndItsSubtypeAreRejected() {
        val supertypeFirst: CapabilityBuilder<Count, Intent, Nothing>.() -> Unit = {
            onIntent<Intent> { state, _ -> Decision(state = state) }
            onIntent<Plus> { state, _ -> Decision(state = state) }
        }
        val subtypeFirst: CapabilityBuilder<Count, Intent, Nothing>.() -> Unit = {
            onIntent<Plus> { state, _ -> Decision(state = state) }
            onIntent<Intent> { state, _ -> Decision(state = state) }
        }
        for (build in listOf(supertypeFirst, subtypeFirst)) {
            if (Intent::class.overlaps(Plus::class)) {
                assertFailsWith<IllegalArgumentException> { capability(name = "overlap", initial = Count(), build = build) }
            } else {
                // Kotlin/Native can’t compare classes, so the overlap fails when the fact arrives.
                assertFailsWith<IllegalStateException> { capability(name = "overlap", initial = Count(), build = build).decide(Count(), Plus(1)) }
            }
        }
    }

    @Test
    fun decisionWithOnlyAStateCarriesNothingElse() {
        val decision = Decision<Count, CounterSignal>(state = Count(1))

        assertEquals(Count(1), decision.state)
        assertTrue(decision.effects.isEmpty())
        assertTrue(decision.works.isEmpty())
        assertTrue(decision.cancels.isEmpty())
        assertTrue(decision.signals.isEmpty())
    }

    @Test
    fun decisionSkipsNullSingleItems() {
        val decision = Decision<Count, CounterSignal>(
            state = Count(),
            effect = null,
            effects = listOf(Done(1)),
            work = null,
            works = listOf(work(key = WorkKey("load"), onError = { Loaded }) { Loaded }),
            cancel = null,
            cancels = listOf(WorkKey("old")),
            signal = null,
            signals = listOf(Changed),
        )

        assertEquals(listOf<Effect>(Done(1)), decision.effects)
        assertEquals(listOf(WorkKey("load")), decision.works.map { it.key })
        assertEquals(listOf(WorkKey("old")), decision.cancels)
        assertEquals(listOf<Signal>(Changed), decision.signals)
    }

    @Test
    fun decisionFillsEveryKindFromItsSingleItem() {
        val decision = Decision<Count, CounterSignal>(
            state = Count(),
            effect = Done(1),
            work = work(key = WorkKey("load"), onError = { Loaded }) { Loaded },
            cancel = WorkKey("old"),
            signal = Changed,
        )

        assertEquals(listOf<Effect>(Done(1)), decision.effects)
        assertEquals(listOf(WorkKey("load")), decision.works.map { it.key })
        assertEquals(listOf(WorkKey("old")), decision.cancels)
        assertEquals(listOf<Signal>(Changed), decision.signals)
    }

    @Test
    fun decisionPutsTheSingleItemBeforeTheList() {
        val decision = Decision<Count, CounterSignal>(
            state = Count(),
            effect = Done(1),
            effects = listOf(Done(2), Done(3)),
            work = work(key = WorkKey("first"), onError = { Loaded }) { Loaded },
            works = listOf(work(key = WorkKey("second"), onError = { Saved }) { Saved }),
            cancel = WorkKey("a"),
            cancels = listOf(WorkKey("b"), WorkKey("c")),
            signal = Changed,
            signals = listOf(Saved, Loaded),
        )

        assertEquals(listOf<Effect>(Done(1), Done(2), Done(3)), decision.effects)
        assertEquals(listOf(WorkKey("first"), WorkKey("second")), decision.works.map { it.key })
        assertEquals(listOf(WorkKey("a"), WorkKey("b"), WorkKey("c")), decision.cancels)
        assertEquals(listOf<Signal>(Changed, Saved, Loaded), decision.signals)
    }

    @Test
    fun bootstrapIsNullWithoutADeclarationAndDecidesWithOne() {
        val plain = capability<Count, CounterIntent, Nothing>(name = "plain", initial = Count()) {
            onIntent<Plus> { state, _ -> Decision(state = state) }
        }
        val loading = capability<Count, Nothing, CounterSignal>(name = "loading", initial = Count()) {
            bootstrap { state -> Decision(state = state.copy(value = state.value + 1), signal = Loaded) }
        }

        assertNull(plain.bootstrap(Count()))
        val decision = loading.bootstrap(Count(4))
        assertEquals(Count(5), decision?.state)
        assertEquals(listOf<Signal>(Loaded), decision?.signals)
        assertTrue(loading.handles.isEmpty())
    }

    @Test
    fun secondBootstrapIsRejectedAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            capability<Count, Nothing, Nothing>(name = "twice", initial = Count()) {
                bootstrap { state -> Decision(state = state) }
                bootstrap { state -> Decision(state = state) }
            }
        }
    }

    @Test
    fun reactionToStartedIsRejectedAtBuildTime() {
        assertFailsWith<IllegalArgumentException> {
            capability<Count, Nothing, Nothing>(name = "started", initial = Count()) {
                onSignal<Started> { state, _ -> Decision(state = state) }
            }
        }
    }

    private interface Liked : Intent
    private interface Shared : Intent
    private data object LikedAndShared : Liked, Shared

    @Test
    fun factThatMatchesTwoUnrelatedReactionsIsRejectedInDecide() {
        val social = capability<Int, Intent, Nothing>(name = "social", initial = 0) {
            onIntent<Liked> { s, _ -> Decision(state = s + 1) }
            onIntent<Shared> { s, _ -> Decision(state = s + 10) }
        }

        val error = assertFailsWith<IllegalStateException> { social.decide(0, LikedAndShared) }

        assertTrue(error.message.orEmpty().contains("Liked"))
        assertTrue(error.message.orEmpty().contains("Shared"))
    }

    @Test
    fun theRememberedReactionGivesTheSameAnswerOnEveryFactOfAClass() {
        val social = capability<Int, Intent, Nothing>(name = "social", initial = 0) {
            onIntent<Liked> { s, _ -> Decision(state = s + 1) }
            onIntent<Shared> { s, _ -> Decision(state = s + 10) }
            onIntent<Plus> { s, intent -> Decision(state = s + intent.by) }
        }

        repeat(2) {
            assertEquals(3, social.decide(0, Plus(3)).state)
            assertEquals(5, social.decide(0, Plus(5)).state)
            assertFailsWith<IllegalStateException> { social.decide(0, LikedAndShared) }
            assertFailsWith<IllegalStateException> { social.decide(0, Unknown) }
        }
    }

    @Test
    @Suppress("IgnoredReturnValue")
    fun builderRejectsInstallAndASecondBuildAfterBuild() {
        val scope = CoroutineScope(Job())
        val builder = CapsaBuilder()
        builder.install(capability<Int, Nothing, Nothing>(name = "first", initial = 0) {})
        builder.build(scope)

        assertFailsWith<IllegalStateException> { builder.install(capability<Int, Nothing, Nothing>(name = "late", initial = 0) {}) }
        assertFailsWith<IllegalStateException> { builder.build(scope) }
        scope.cancel()
    }
}
