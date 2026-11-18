// ktlint for every Kotlin module. It runs in `check`, so `build` fails on a finding.

plugins {
    id("org.jlleitschuh.gradle.ktlint")
}

ktlint {
    // The rules and the style come from .editorconfig in the repository root.
    version.set(libs.version("ktlint"))
}
