// The Android entry point of the demo app.

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    // Adds the benchmarkRelease build type that samples:macrobenchmark measures, and takes the profile it generates.
    id("androidx.baselineprofile")
    id("capsa.lint")
}

android {
    namespace = "com.mairwunnx.capsa.samples"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.mairwunnx.capsa.samples"
        minSdk = libs.versions.android.samples.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // The release build shows the crash-free side of Capsa on a phone, so it installs without a key of its own.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    implementation(projects.samples.shared)
    implementation(libs.androidx.activity.compose)
    // Installs the baseline profile on phones without Google Play, and lets the macrobenchmarks compile the app with it.
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.samples.macrobenchmark)
}
