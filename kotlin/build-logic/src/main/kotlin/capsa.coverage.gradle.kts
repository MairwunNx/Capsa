// The root project: one Kover report over every library module. Samples and benchmarks stay out.
// Kover's total report of a module takes its JVM tests and its Android host tests, so the merged report counts both.

import kotlinx.kover.gradle.plugin.dsl.CoverageUnit

plugins {
    // base gives the root project check and build. Kover hangs koverVerify on check, so ./gradlew build checks the bound.
    base
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    listOf("capsa-core", "capsa-host", "capsa-viewmodel", "capsa-compose", "capsa-test", "capsa-viewmodel-test").forEach { kover(project(":$it")) }
}

kover {
    reports {
        total {
            verify {
                // The merged report covers 90.8 % of the lines, and it holds steady from run to run.
                // The bound sits a bit lower: about 15 uncovered lines more fail `koverVerify`.
                rule("Line coverage of every library module") {
                    minBound(89, CoverageUnit.LINE)
                }
            }
        }
    }
}
