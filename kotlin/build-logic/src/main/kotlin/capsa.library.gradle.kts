// Convention for every published Kotlin Multiplatform module of Capsa.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("capsa.publish")
    id("org.jetbrains.kotlinx.kover")
    id("org.jetbrains.kotlinx.binary-compatibility-validator")
}

kotlin {
    explicitApi()
    jvmToolchain(libs.version("jvm-toolchain").toInt())

    android {
        namespace = "com.mairwunnx.capsa." + project.name.removePrefix("capsa-").replace('-', '.')
        compileSdk = libs.version("android-compileSdk").toInt()
        minSdk = libs.version("android-minSdk").toInt()
        withHostTest {}
    }
    jvm()
    iosArm64()
    iosSimulatorArm64()
    macosArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(libs.findLibrary("kotlin-test").get())
        }
    }
}

// The ABI dumps in api/ guard the public API: `check` fails when it changes without `apiDump`.
// The Kotlin Gradle plugin has its own abiValidation, but its DSL is still experimental.
apiValidation {
    @OptIn(kotlinx.validation.ExperimentalBCVApi::class)
    klib {
        enabled = true
    }
}
