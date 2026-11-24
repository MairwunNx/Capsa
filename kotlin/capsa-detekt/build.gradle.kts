// Rules for detekt 2 that catch the common mistakes of a Capsa screen. A JVM library: detekt loads it as a plugin.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("capsa.publish")
    id("capsa.lint")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
    id("org.jetbrains.dokka")
}

description = "Rules for detekt that catch the common mistakes of a Capsa screen."

kotlin {
    explicitApi()
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    // detekt brings its API when it loads the plugin.
    compileOnly(libs.detekt.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.kotlin.test)
}

tasks.test {
    useJUnitPlatform()
}
