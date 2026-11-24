// The benchmarks of capsa-benchmarks on a phone: androidx.benchmark runs them in ART, which compiles code differently from the JVM.
// Never published, CI doesn't run them. tools/device_bench.sh runs them on a phone connected over USB.

plugins {
    id("com.android.library")
    id("androidx.benchmark")
    id("capsa.lint")
}

android {
    namespace = "com.mairwunnx.capsa.benchmarks.android"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
    }

    // A debuggable build runs slower and androidx.benchmark refuses to measure it, so the tests run on the release build.
    testBuildType = "release"
    buildTypes {
        release {
            isDefault = true
        }
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.toolchain.get().toInt())
}

dependencies {
    // The benchmark classes of the JVM run, so the phone measures the same code with the same settings.
    androidTestImplementation(projects.capsaBenchmarks)
    androidTestImplementation(projects.capsaCore)
    androidTestImplementation(libs.androidx.benchmark.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
