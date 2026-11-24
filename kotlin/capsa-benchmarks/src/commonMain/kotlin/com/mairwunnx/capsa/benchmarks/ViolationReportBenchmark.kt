package com.mairwunnx.capsa.benchmarks

import com.mairwunnx.capsa.CapsaBuilder
import com.mairwunnx.capsa.CapsaConfig
import com.mairwunnx.capsa.CapsaRuntime
import com.mairwunnx.capsa.CapsaViolationException
import com.mairwunnx.capsa.Intent
import com.mairwunnx.capsa.capability
import kotlinx.benchmark.Benchmark
import kotlinx.benchmark.Scope
import kotlinx.benchmark.Setup
import kotlinx.benchmark.State
import kotlinx.benchmark.TearDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel

/**
 * A fact nobody handles: the runtime writes it to a full history, renders the history, and builds the exception for the crash report.
 *
 * The handler keeps the exception instead of throwing it, so the benchmark measures the report, not the error handler of the scope.
 */
@State(Scope.Benchmark)
class ViolationReportBenchmark {

    private lateinit var scope: CoroutineScope
    private lateinit var runtime: CapsaRuntime
    private var report: CapsaViolationException? = null

    @Setup
    fun setUp() {
        scope = unconfinedScope()
        val builder = CapsaBuilder()
        val counter = builder.install(capability<Int, BenchIntent, Nothing>(name = "counter", initial = 0) { onIntents(from = 0, count = 1) })
        runtime = builder.build(scope, CapsaConfig(onViolation = { report = it }))
        // Fills the whole history, so every report renders all of its entries.
        repeat(HISTORY) { runtime.append(I0) }
        runtime.append(Unhandled)
        check(counter.value == HISTORY) { "The runtime didn't process the facts inside append()" }
        checkNotNull(report) { "The runtime didn't report the unhandled fact inside append()" }
    }

    @TearDown
    fun tearDown() {
        scope.cancel()
    }

    @Benchmark
    fun reportUnhandledFact(): CapsaViolationException? {
        runtime.append(Unhandled)
        return report
    }

    private data object Unhandled : Intent

    private companion object {
        const val HISTORY = 32
    }
}
