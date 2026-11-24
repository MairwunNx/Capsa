package com.mairwunnx.capsa.detekt

import dev.detekt.api.Config
import dev.detekt.test.lint
import kotlin.test.Test
import kotlin.test.assertEquals

class CapsaRulesTest {

    private val config = Config.empty

    @Test
    fun aPlainClassFactIsReported() {
        val findings = FactWithoutData(config).lint(
            """
            import com.mairwunnx.capsa.Intent
            import com.mairwunnx.capsa.Signal
            import com.mairwunnx.capsa.Effect

            sealed interface SearchIntent : Intent
            data class QueryChanged(val text: String) : SearchIntent
            class Cleared : SearchIntent
            object Refreshed : SearchIntent
            data object Retried : SearchIntent
            class ResultsLoaded(val items: List<String>) : Signal
            object ShowError : Effect
            enum class Mode : Intent { Fast, Slow }
            abstract class Base : Intent
            class Unrelated
            """.trimIndent(),
            compile = false,
        )

        assertEquals(listOf("Cleared", "Refreshed", "ResultsLoaded", "ShowError"), findings.map { it.message.substringBefore(' ') })
    }

    @Test
    fun aFileWithoutCapsaIsLeftAlone() {
        val findings = FactWithoutData(config).lint(
            """
            interface Intent
            class Clicked : Intent
            """.trimIndent(),
            compile = false,
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun aDecisionWithAPositionalArgumentIsReported() {
        val findings = DecisionWithPositionalArguments(config).lint(
            """
            import com.mairwunnx.capsa.Decision

            fun named(state: Int) = Decision(state = state + 1)
            fun positional(state: Int) = Decision(state + 1)
            fun mixed(state: Int) = Decision<Int, Nothing>(state, effect = null)
            """.trimIndent(),
            compile = false,
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun anEagerStateInInAHostIsReported() {
        val findings = EagerStateInHost(config).lint(
            """
            import com.mairwunnx.capsa.viewmodel.CapsaViewModel
            import kotlinx.coroutines.flow.SharingStarted
            import kotlinx.coroutines.flow.stateIn

            class SearchViewModel : CapsaViewModel() {
                private val search = install(search())
                val eager = search.stateIn(viewModelScope, SharingStarted.Eagerly, null)
                val lazy = search.shareIn(viewModelScope, started = SharingStarted.Lazily)
                val fine = search.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
            }

            class Elsewhere {
                val eager = flow.stateIn(scope, SharingStarted.Eagerly, null)
            }
            """.trimIndent(),
            compile = false,
        )

        assertEquals(2, findings.size)
    }

    @Test
    fun advanceUntilIdleWithTheHarnessIsReported() {
        val findings = AdvanceUntilIdleWithHarness(config).lint(
            """
            import com.mairwunnx.capsa.test.capsaHarness
            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            fun searchWaitsForNothing() = runTest {
                val harness = capsaHarness(search)
                harness.append(QueryChanged("app"))
                advanceUntilIdle()
            }
            """.trimIndent(),
            compile = false,
        )

        assertEquals(1, findings.size)
    }

    @Test
    fun advanceUntilIdleWithoutTheHarnessIsLeftAlone() {
        val findings = AdvanceUntilIdleWithHarness(config).lint(
            """
            import kotlinx.coroutines.test.advanceUntilIdle
            import kotlinx.coroutines.test.runTest

            fun plain() = runTest { advanceUntilIdle() }
            """.trimIndent(),
            compile = false,
        )

        assertEquals(0, findings.size)
    }

    @Test
    fun theProviderHoldsEveryRule() {
        val rules = CapsaRuleSetProvider().instance().rules.keys.map { it.value }

        assertEquals(listOf("FactWithoutData", "DecisionWithPositionalArguments", "EagerStateInHost", "AdvanceUntilIdleWithHarness"), rules)
    }
}
