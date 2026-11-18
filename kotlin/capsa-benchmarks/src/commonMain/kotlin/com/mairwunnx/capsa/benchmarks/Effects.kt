package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.Effect
import com.mairwunnx.capsa.EffectHandlers

// Thirty unrelated effects: a set of handlers can hold up to thirty handlers that don't overlap.
internal data object E0 : Effect
internal data object E1 : Effect
internal data object E2 : Effect
internal data object E3 : Effect
internal data object E4 : Effect
internal data object E5 : Effect
internal data object E6 : Effect
internal data object E7 : Effect
internal data object E8 : Effect
internal data object E9 : Effect
internal data object E10 : Effect
internal data object E11 : Effect
internal data object E12 : Effect
internal data object E13 : Effect
internal data object E14 : Effect
internal data object E15 : Effect
internal data object E16 : Effect
internal data object E17 : Effect
internal data object E18 : Effect
internal data object E19 : Effect
internal data object E20 : Effect
internal data object E21 : Effect
internal data object E22 : Effect
internal data object E23 : Effect
internal data object E24 : Effect
internal data object E25 : Effect
internal data object E26 : Effect
internal data object E27 : Effect
internal data object E28 : Effect
internal data object E29 : Effect

/** The effects in order, `effects[n]` is `En`. */
internal val effects: List<Effect> = listOf(E0, E1, E2, E3, E4, E5, E6, E7, E8, E9, E10, E11, E12, E13, E14, E15, E16, E17, E18, E19, E20, E21, E22, E23, E24, E25, E26, E27, E28, E29)

/** Registers `count` handlers for the effects from `effects[0]` on, each one adds one to `handled`. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun EffectHandlers.Builder.onEffects(count: Int, handled: IntArray) {
    val handle: (Effect) -> Unit = { handled[0]++ }
    if (0 < count) on<E0>(handle)
    if (1 < count) on<E1>(handle)
    if (2 < count) on<E2>(handle)
    if (3 < count) on<E3>(handle)
    if (4 < count) on<E4>(handle)
    if (5 < count) on<E5>(handle)
    if (6 < count) on<E6>(handle)
    if (7 < count) on<E7>(handle)
    if (8 < count) on<E8>(handle)
    if (9 < count) on<E9>(handle)
    if (10 < count) on<E10>(handle)
    if (11 < count) on<E11>(handle)
    if (12 < count) on<E12>(handle)
    if (13 < count) on<E13>(handle)
    if (14 < count) on<E14>(handle)
    if (15 < count) on<E15>(handle)
    if (16 < count) on<E16>(handle)
    if (17 < count) on<E17>(handle)
    if (18 < count) on<E18>(handle)
    if (19 < count) on<E19>(handle)
    if (20 < count) on<E20>(handle)
    if (21 < count) on<E21>(handle)
    if (22 < count) on<E22>(handle)
    if (23 < count) on<E23>(handle)
    if (24 < count) on<E24>(handle)
    if (25 < count) on<E25>(handle)
    if (26 < count) on<E26>(handle)
    if (27 < count) on<E27>(handle)
    if (28 < count) on<E28>(handle)
    if (29 < count) on<E29>(handle)
}
