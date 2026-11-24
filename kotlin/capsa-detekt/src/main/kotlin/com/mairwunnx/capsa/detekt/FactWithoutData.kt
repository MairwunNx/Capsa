package com.mairwunnx.capsa.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Reports an intent, a signal, or an effect that isn’t a `data class` or a `data object`.
 *
 * The journal and the history of a crash report print facts with `toString()`. A plain class prints `a.b@1f2e`
 * in a release build, and nobody can read the report. A debug build fails on such a fact only when it reaches the runtime.
 * The rule follows the sealed interfaces of the same file, such as `sealed interface SearchIntent : Intent`.
 */
internal class FactWithoutData(config: Config) : Rule(
    config,
    "A fact or an effect must be a data class or a data object, so the journal and crash reports can print it.",
) {

    override fun visit(root: KtFile) {
        val roots = FACTS.filter { root.sees(it) }
        if (roots.isEmpty()) return
        val declarations = root.collectDescendantsOfType<KtClassOrObject>()
        val factTypes = roots.toMutableSet()
        // A sealed interface of a capability extends Intent or Signal, and the facts extend that interface.
        var grown = true
        while (grown) {
            grown = declarations.filter { it is KtClass && it.isInterface() && it.superNames.any(factTypes::contains) }
                .mapNotNull { it.name }
                .any(factTypes::add)
        }
        declarations.filter { it.isConcrete() && it.superNames.any(factTypes::contains) && !it.hasModifier(KtTokens.DATA_KEYWORD) }
            .forEach { fact ->
                val entity = if (fact.nameIdentifier != null) Entity.atName(fact) else Entity.from(fact)
                report(Finding(entity, "${fact.name ?: "This object"} is a fact or an effect: make it a data class or a data object."))
            }
    }

    private fun KtClassOrObject.isConcrete(): Boolean = when (this) {
        is KtObjectDeclaration -> !isCompanion()
        is KtClass -> !isInterface() && !isEnum() && !hasModifier(KtTokens.ABSTRACT_KEYWORD) && !hasModifier(KtTokens.SEALED_KEYWORD)
        else -> false
    }

    private companion object {
        val FACTS = listOf("Intent", "Signal", "Effect")
    }
}
