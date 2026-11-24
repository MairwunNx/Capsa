package com.mairwunnx.capsa.detekt

import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

internal const val CAPSA = "com.mairwunnx.capsa"

/** Tells whether the file sees `name` from `pkg`: it lives in that package or imports the name or the whole package. */
internal fun KtFile.sees(name: String, pkg: String = CAPSA): Boolean =
    packageFqName.asString() == pkg || importDirectives.any { directive ->
        val imported = directive.importedFqName?.asString()
        imported == "$pkg.$name" || (directive.isAllUnder && imported == pkg)
    }

/** The name of the function a call invokes, as written: `stateIn` for `flow.stateIn(…)`. */
internal val KtCallExpression.calleeName: String? get() = calleeExpression?.text

/** Every call in this file or class. */
internal fun KtFile.calls(): List<KtCallExpression> = collectDescendantsOfType()

/** The simple names of the direct supertypes, without packages and type arguments: `Intent` for `com.x.Intent`. */
internal val KtClassOrObject.superNames: List<String>
    get() = superTypeListEntries.mapNotNull { entry ->
        entry.typeReference?.text?.substringBefore('<')?.substringBefore('(')?.substringAfterLast('.')?.trim()
    }
