plugins {
    id("capsa.library")
}

description = "Hosts a Capsa runtime in an AndroidX ViewModel on every platform."

kotlin {
    android {
        // Tests on a phone or an emulator: `./gradlew :capsa-viewmodel:connectedAndroidDeviceTest`.
        withDeviceTest {
            instrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        }
    }

    sourceSets {
        all {
            languageSettings.optIn("com.mairwunnx.capsa.InternalCapsaApi")
        }
        commonMain.dependencies {
            api(projects.capsaHost)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        getByName("androidDeviceTest").dependencies {
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.test.ext.junit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Android host tests run on a stub android.jar, where Bundle, and with it every saved snapshot, throws "not mocked".
// CapsaViewModelTest saves snapshots in most tests, so it runs on the JVM and Kotlin/Native only.
// SavedStateOnDeviceTest checks the snapshots on Android, through a real Bundle and a Parcel.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter.excludeTestsMatching("com.mairwunnx.capsa.viewmodel.CapsaViewModelTest")
}
