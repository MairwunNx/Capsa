package com.mairwunnx.capsa.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtFile

/**
 * Reports a `Decision(…)` with an argument passed by position.
 *
 * A decision reads as a sentence only with names: `Decision(state = …, work = …, cancel = …)`. By position, a reader
 * can’t tell a single work from a list of works or a cancel from a signal, and a new parameter shifts the meaning.
 */
internal class DecisionWithPositionalArguments(config: Config) : Rule(
    config,
    "Name every argument of Decision: Decision(state = …, work = …).",
) {

    override fun visit(root: KtFile) {
        if (!root.sees("Decision")) return
        root.calls()
            .filter { it.calleeName == "Decision" && it.valueArguments.any { argument -> !argument.isNamed() } }
            .forEach { call -> report(Finding(Entity.from(call), "Name every argument of Decision, such as Decision(state = …, work = …).")) }
    }
}
