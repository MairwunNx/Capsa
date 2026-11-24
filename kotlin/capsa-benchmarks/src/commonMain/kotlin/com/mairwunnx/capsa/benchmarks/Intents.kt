package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapabilityBuilder
import com.mairwunnx.capsa.Decision
import com.mairwunnx.capsa.Intent

/** The intents of the benchmarks. Each capability owns a range of them, so no two capabilities own the same intent. */
internal sealed interface BenchIntent : Intent

// Thirty unrelated intents: a capability can hold up to thirty reactions that don't overlap.
internal data object I0 : BenchIntent
internal data object I1 : BenchIntent
internal data object I2 : BenchIntent
internal data object I3 : BenchIntent
internal data object I4 : BenchIntent
internal data object I5 : BenchIntent
internal data object I6 : BenchIntent
internal data object I7 : BenchIntent
internal data object I8 : BenchIntent
internal data object I9 : BenchIntent
internal data object I10 : BenchIntent
internal data object I11 : BenchIntent
internal data object I12 : BenchIntent
internal data object I13 : BenchIntent
internal data object I14 : BenchIntent
internal data object I15 : BenchIntent
internal data object I16 : BenchIntent
internal data object I17 : BenchIntent
internal data object I18 : BenchIntent
internal data object I19 : BenchIntent
internal data object I20 : BenchIntent
internal data object I21 : BenchIntent
internal data object I22 : BenchIntent
internal data object I23 : BenchIntent
internal data object I24 : BenchIntent
internal data object I25 : BenchIntent
internal data object I26 : BenchIntent
internal data object I27 : BenchIntent
internal data object I28 : BenchIntent
internal data object I29 : BenchIntent

/** The intents in order, `intents[n]` is `In`. */
internal val intents: List<Intent> = listOf(I0, I1, I2, I3, I4, I5, I6, I7, I8, I9, I10, I11, I12, I13, I14, I15, I16, I17, I18, I19, I20, I21, I22, I23, I24, I25, I26, I27, I28, I29)

private val bump: (Int, BenchIntent) -> Decision<Int, Nothing> = { state, _ -> Decision(state = state + 1) }

/** Registers `count` reactions to the intents from `intents[from]` on, each one adds one to the state. */
@Suppress("CyclomaticComplexMethod", "LongMethod")
internal fun CapabilityBuilder<Int, BenchIntent, Nothing>.onIntents(from: Int, count: Int) {
    val range = from until from + count
    if (0 in range) onIntent<I0>(bump)
    if (1 in range) onIntent<I1>(bump)
    if (2 in range) onIntent<I2>(bump)
    if (3 in range) onIntent<I3>(bump)
    if (4 in range) onIntent<I4>(bump)
    if (5 in range) onIntent<I5>(bump)
    if (6 in range) onIntent<I6>(bump)
    if (7 in range) onIntent<I7>(bump)
    if (8 in range) onIntent<I8>(bump)
    if (9 in range) onIntent<I9>(bump)
    if (10 in range) onIntent<I10>(bump)
    if (11 in range) onIntent<I11>(bump)
    if (12 in range) onIntent<I12>(bump)
    if (13 in range) onIntent<I13>(bump)
    if (14 in range) onIntent<I14>(bump)
    if (15 in range) onIntent<I15>(bump)
    if (16 in range) onIntent<I16>(bump)
    if (17 in range) onIntent<I17>(bump)
    if (18 in range) onIntent<I18>(bump)
    if (19 in range) onIntent<I19>(bump)
    if (20 in range) onIntent<I20>(bump)
    if (21 in range) onIntent<I21>(bump)
    if (22 in range) onIntent<I22>(bump)
    if (23 in range) onIntent<I23>(bump)
    if (24 in range) onIntent<I24>(bump)
    if (25 in range) onIntent<I25>(bump)
    if (26 in range) onIntent<I26>(bump)
    if (27 in range) onIntent<I27>(bump)
    if (28 in range) onIntent<I28>(bump)
    if (29 in range) onIntent<I29>(bump)
}
