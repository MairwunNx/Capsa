plugins {
    id("capsa.library.compose")
}

description = "Compose Multiplatform helpers for Capsa: effect collection and slots, with or without a view model."

// Without a target SDK in the library manifest Robolectric runs the oldest SDK it supports. This file points it at compileSdk.
val robolectricConfig = tasks.register("robolectricConfig") {
    val sdk = libs.versions.android.compileSdk
    val output = layout.buildDirectory.dir("generated/robolectric")
    inputs.property("sdk", sdk)
    outputs.dir(output)
    doLast {
        output.get().file("robolectric.properties").asFile.writeText("sdk=${sdk.get()}\n")
    }
}

kotlin {
    android {
        // Tests on a phone or an emulator: `./gradlew :capsa-compose:connectedAndroidDeviceTest`.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    // CompositionTest is an expect class, which Kotlin still marks as Beta.
    compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")

    sourceSets {
        all {
            languageSettings.optIn("com.mairwunnx.capsa.InternalCapsaApi")
        }
        commonMain.dependencies {
            api(projects.capsaHost)
            api(libs.compose.runtime)
            api(libs.compose.runtime.saveable)
            api(libs.compose.runtime.retain)
            api(libs.compose.ui)
            api(libs.androidx.lifecycle.runtime.compose)
        }
        // Compose Desktop has no Dispatchers.Main of its own, and rememberCapsa and CollectEffects run on it.
        // Swing gives it on the desktop, the other platforms bring their own main dispatcher.
        jvmMain.dependencies {
            runtimeOnly(libs.kotlinx.coroutines.swing)
        }
        commonTest.dependencies {
            implementation(projects.capsaViewmodel)
            implementation(libs.kotlinx.coroutines.test)
        }
        // The stub android.jar of host tests throws "not mocked" on android.os.Trace and android.util.Log, which a composition calls.
        // Robolectric gives the tests a real Android framework, see CompositionTest.
        getByName("androidHostTest") {
            dependencies {
                implementation(libs.robolectric)
            }
            resources.srcDir(robolectricConfig)
        }
        // A real composition in a real window on the phone, see SlotOnDeviceTest.
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.compose.ui.test)
            implementation(libs.compose.ui.test.manifest)
            implementation(libs.compose.foundation)
        }
    }
}

tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(libs.versions.robolectric.jvm.get().toInt()) }
}
