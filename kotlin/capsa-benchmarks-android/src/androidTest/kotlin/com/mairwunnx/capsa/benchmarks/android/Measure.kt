package com.mairwunnx.capsa.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated

/**
 * Measures `operation` of a benchmark from capsa-benchmarks the way JMH does: `setUp` before the run, `tearDown` after it.
 *
 * The phone runs the same classes as the JVM, so both measure the same code.
 */
internal inline fun <B> BenchmarkRule.measure(benchmark: B, setUp: B.() -> Unit = {}, tearDown: B.() -> Unit = {}, crossinline operation: B.() -> Any?) {
    benchmark.setUp()
    try {
        measureRepeated { benchmark.operation() }
    } finally {
        benchmark.tearDown()
    }
}
