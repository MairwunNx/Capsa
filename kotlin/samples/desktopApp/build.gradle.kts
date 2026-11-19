// The desktop entry point of the demo app: `./gradlew :samples:desktopApp:run`.

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.multiplatform)
    id("capsa.lint")
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(projects.samples.shared)
    implementation(compose.desktop.currentOs)
    // Dispatchers.Main on the desktop: the Swing event thread.
    implementation(libs.kotlinx.coroutines.swing)
}

compose.desktop {
    application {
        mainClass = "com.mairwunnx.capsa.samples.MainKt"
    }
}
