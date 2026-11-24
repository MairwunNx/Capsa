package com.mairwunnx.capsa.detekt

import dev.detekt.api.RuleSet
import dev.detekt.api.RuleSetId
import dev.detekt.api.RuleSetProvider

/**
 * The rule set `capsa` for detekt 2: the mistakes that compile but break a Capsa screen.
 *
 * Add it to a module with `detektPlugins("com.mairwunnx.capsa:capsa-detekt:<version>")`. Every rule is on by default,
 * and `capsa:` in your detekt config turns a rule off: `capsa: { FactWithoutData: { active: false } }`.
 *
 * The rules read the code without types, so they work in every source set, the native ones included.
 * They see only what one file declares: a fact whose sealed interface lives in another file escapes [FactWithoutData].
 */
public class CapsaRuleSetProvider : RuleSetProvider {

    override val ruleSetId: RuleSetId = RuleSetId("capsa")

    override fun instance(): RuleSet = RuleSet(
        ruleSetId,
        listOf(::FactWithoutData, ::DecisionWithPositionalArguments, ::EagerStateInHost, ::AdvanceUntilIdleWithHarness),
    )
}
