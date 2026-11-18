package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapabilityBuilder
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.commit

// Thirty unrelated intents: a capability can hold up to thirty reactions that don't overlap.
internal data object I0 : Intent
internal data object I1 : Intent
internal data object I2 : Intent
internal data object I3 : Intent
internal data object I4 : Intent
internal data object I5 : Intent
internal data object I6 : Intent
internal data object I7 : Intent
internal data object I8 : Intent
internal data object I9 : Intent
internal data object I10 : Intent
internal data object I11 : Intent
internal data object I12 : Intent
internal data object I13 : Intent
internal data object I14 : Intent
internal data object I15 : Intent
internal data object I16 : Intent
internal data object I17 : Intent
internal data object I18 : Intent
internal data object I19 : Intent
internal data object I20 : Intent
internal data object I21 : Intent
internal data object I22 : Intent
internal data object I23 : Intent
internal data object I24 : Intent
internal data object I25 : Intent
internal data object I26 : Intent
internal data object I27 : Intent
internal data object I28 : Intent
internal data object I29 : Intent

/** The intents in order, `intents[n]` is `In`. */
internal val intents: List<Intent> = listOf(I0, I1, I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I12, I13, I14, I15, I16, I17, I18, I19, I20, I21, I22, I23, I24, I25, I26, I27, I28, I29)

private val bump: (Int, Intent) -> Decision<Int> = { state, _ -> (state + 1).commit() }

/** Registers `count` reactions to the intents from `intents[from]` on, each one adds one to the state. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun CapabilityBuilder<Int>.onIntents(from: Int, count: Int) {
    val range = from until from + count
    if (0 in range) on<I0>(bump)
    if (1 in range) on<I1>(bump)
    if (2 in range) on<I2>(bump)
    if (3 in range) on<I3>(bump)
    if (4 in range) on<I4>(bump)
    if (5 in range) on<I5>(bump)
    if (6 in range) on<I6>(bump)
    if (7 in range) on<I7>(bump)
    if (8 in range) on<I8>(bump)
    if (9 in range) on<I9>(bump)
    if (10 in range) on<I10>(bump)
    if (11 in range) on<I11>(bump)
    if (12 in range) on<I12>(bump)
    if (13 in range) on<I13>(bump)
    if (14 in range) on<I14>(bump)
    if (15 in range) on<I15>(bump)
    if (16 in range) on<I16>(bump)
    if (17 in range) on<I17>(bump)
    if (18 in range) on<I18>(bump)
    if (19 in range) on<I19>(bump)
    if (20 in range) on<I20>(bump)
    if (21 in range) on<I21>(bump)
    if (22 in range) on<I22>(bump)
    if (23 in range) on<I23>(bump)
    if (24 in range) on<I24>(bump)
    if (25 in range) on<I25>(bump)
    if (26 in range) on<I26>(bump)
    if (27 in range) on<I27>(bump)
    if (28 in range) on<I28>(bump)
    if (29 in range) on<I29>(bump)
}
