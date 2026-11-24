// Macrobenchmarks of the demo app on a phone, and the baseline profile of Capsa. Never published, CI doesn't run them.
// tools/device_bench.sh runs them on a phone connected over USB.

plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
    id("capsa.lint")
}

android {
    namespace = "com.mairwunnx.capsa.samples.macrobenchmark"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Macrobenchmark needs Android 7.0, as the demo app does.
        minSdk = libs.versions.android.samples.minSdk.get().toInt()
        targetSdk = libs.versions.android.compileSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    targetProjectPath = ":samples:androidApp"
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

baselineProfile {
    // The phone is connected by hand, there is no managed device.
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.test.ext.junit)
}
