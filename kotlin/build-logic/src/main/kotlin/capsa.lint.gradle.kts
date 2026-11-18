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
}

// The JVM and Android compilations take the common code too and run every rule with types.
// The other source sets, Apple and native, get the light analysis without types.
val coveredByCompilations = listOf("detektCommon", "detektJvm", "detektAndroid")
tasks.named("check") {
    dependsOn(
        tasks.withType<Detekt>().matching { task ->
            task.name != "detekt" && (!task.name.endsWith("SourceSet") || coveredByCompilations.none { task.name.startsWith(it) })
        },
    )
}
