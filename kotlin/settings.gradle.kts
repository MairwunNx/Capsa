pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    // Downloads the JDK of jvmToolchain when the machine has none.
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "capsa"

include(
    ":capsa-core",
    ":capsa-host",
    ":capsa-viewmodel",
    ":capsa-compose",
    ":capsa-test",
    ":capsa-bom",
    ":capsa-benchmarks",
    ":samples:shared",
    ":samples:androidApp",
    ":samples:desktopApp",
)
