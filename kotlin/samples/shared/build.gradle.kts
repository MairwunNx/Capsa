// The shared code of the demo app: every sample of the wiki as a Compose Multiplatform screen for Android, iOS, and the desktop.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("org.jetbrains.kotlin.plugin.compose")
    alias(libs.plugins.compose.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    id("capsa.lint")
}

detekt {
    // Compose code is full of sizes and colors: samples/detekt.yml lets them be.
    config.from(file("../detekt.yml"))
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())

    android {
        namespace = "com.mairwunnx.capsa.samples.shared"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.samples.minSdk.get().toInt()
    }
    jvm()
    listOf(iosArm64(), iosSimulatorArm64()).forEach { target ->
        // The iOS app links the framework that Xcode builds through embedAndSignAppleFrameworkForXcode.
        target.binaries.framework {
            baseName = "Shared"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.capsaCompose)
            implementation(projects.capsaViewmodel)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.material.icons.core)
            implementation(libs.androidx.lifecycle.viewmodel.compose)
            implementation(libs.androidx.lifecycle.viewmodel.navigation3)
            implementation(libs.androidx.navigation3.ui)
        }
        commonTest.dependencies {
            implementation(projects.capsaTest)
            implementation(libs.kotlin.test)
        }
        jvmTest.dependencies {
            // Clicks through every sample on the desktop, without a window.
            implementation(libs.compose.ui.test)
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
        }
    }
}

// Only Xcode needs the framework: it calls embedAndSignAppleFrameworkForXcode with SDK_NAME set.
// `./gradlew build` would link four frameworks of Compose side by side, which takes minutes and runs the daemon out of memory.
// The iOS tests still compile and link the Kotlin code, so build checks it without the frameworks.
val fromXcode = providers.environmentVariable("SDK_NAME").isPresent
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().matching { it.binary is org.jetbrains.kotlin.gradle.plugin.mpp.Framework }.configureEach {
    enabled = fromXcode
}
