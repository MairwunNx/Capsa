// Convention for every published Kotlin Multiplatform module of Capsa.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("capsa.publish")
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

