// ktlint and detekt for every Kotlin module. Both run in `check`, so `build` fails on a finding.

import dev.detekt.gradle.Detekt

plugins {
    id("org.jlleitschuh.gradle.ktlint")
    id("dev.detekt")
}

ktlint {
    // The rules and the style come from .editorconfig in the repository root.
    version.set(libs.version("ktlint"))
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(layout.settingsDirectory.file("config/detekt.yml"))
    parallel = true
    // Paths in the reports start at the repository root, so GitHub links each finding in SARIF to its file.
    basePath.set(layout.settingsDirectory.asFile.parentFile)
}

// Files with `actual` declarations, by the naming the modules follow: Platform.jvmCommon.kt, Capsa.android.kt, and so on.
val actualFiles = listOf("**/*.jvmCommon.kt", "**/*.jvm.kt", "**/*.android.kt", "**/*.native.kt")

// The JVM and Android compilations take the common code too and run every rule with types.
// detekt 2.0.0-alpha.6 compiles all source sets of a compilation as one module, without the source set structure
// that the Kotlin Gradle plugin passes. So each expect/actual pair fails with "expect and corresponding actual are declared
// in the same module" and "has no corresponding expected declaration", and detekt warns about compiler errors.
// The typed tasks leave the actual files out, which leaves no such pair, and the light tasks below take those files instead.
val typedTasks = tasks.withType<Detekt>().matching { it.name != "detekt" && !it.name.endsWith("SourceSet") }
typedTasks.configureEach {
    exclude(actualFiles)
}

// The light analysis without types covers what the typed tasks can't see: the actual files of the JVM and Android
// source sets, and the Apple and native source sets as a whole. The common code is in the typed tasks already.
val jvmAndAndroidSourceSets = listOf("detektJvm", "detektAndroid")
val lightTasks = tasks.withType<Detekt>().matching { task ->
    task.name.endsWith("SourceSet") && !task.name.startsWith("detektCommon")
}
lightTasks.configureEach {
    if (jvmAndAndroidSourceSets.any { name.startsWith(it) }) include(actualFiles)
}

// The plain `detekt` task would run the light analysis over every file and miss the rules with types.
// It runs the typed and the light tasks above instead, the same set as `check`.
tasks.named<Detekt>("detekt") {
    enabled = false
    dependsOn(typedTasks, lightTasks)
}
tasks.named("check") {
    dependsOn("detekt")
}
