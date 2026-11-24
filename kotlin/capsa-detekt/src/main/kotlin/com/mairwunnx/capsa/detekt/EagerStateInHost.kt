package com.mairwunnx.capsa.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Reports `stateIn` or `shareIn` with `SharingStarted.Eagerly` or `Lazily` inside a `CapsaViewModel` or a `CapsaComposable`.
 *
 * The runtime counts the screen as visible while someone collects its slices. A flow that the host shares eagerly
 * never stops collecting, so the screen stays visible in the background, and its `whileVisible` streams never pause.
 * `mapState` and `combineState` build the same model and pass the collectors of the UI through.
 */
internal class EagerStateInHost(config: Config) : Rule(
    config,
    "A host shares its flows with mapState, combineState, or WhileSubscribed: an eager stateIn keeps the screen visible for good.",
) {

    override fun visit(root: KtFile) {
        val hosts = HOSTS.filter { (name, pkg) -> root.sees(name, pkg) }.map { it.first }
        if (hosts.isEmpty()) return
        root.collectDescendantsOfType<KtClass>()
            .filter { host -> host.superNames.any(hosts::contains) }
            .flatMap { host -> host.collectDescendantsOfType<KtCallExpression>() }
            .filter { it.calleeName in SHARING && it.valueArguments.any { argument -> EAGER.containsMatchIn(argument.text) } }
            .forEach { call ->
                report(
                    Finding(
                        Entity.from(call),
                        "${call.calleeName} with an eager start keeps the screen visible for good. Use mapState, combineState, or SharingStarted.WhileSubscribed.",
                    ),
                )
            }
    }

    private companion object {
        val HOSTS = listOf("CapsaViewModel" to "$CAPSA.viewmodel", "CapsaComposable" to "$CAPSA.compose")
        val SHARING = setOf("stateIn", "shareIn")
        val EAGER = Regex("""\b(Eagerly|Lazily)\b""")
    }
}
