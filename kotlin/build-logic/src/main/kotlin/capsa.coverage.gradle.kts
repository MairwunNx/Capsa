// The root project: one Kover report over every library module. Samples and benchmarks stay out.

plugins {
    id("org.jetbrains.kotlinx.kover")
}

dependencies {
    listOf("capsa-core", "capsa-host", "capsa-viewmodel", "capsa-compose", "capsa-test").forEach { kover(project(":$it")) }
}
