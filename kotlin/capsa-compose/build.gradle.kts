plugins {
    id("capsa.library.compose")
}

description = "Compose Multiplatform helpers for Capsa: effect collection and slots."

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.capsaCore)
            api(projects.capsaViewmodel)
            api(libs.compose.runtime)
            api(libs.compose.ui)
            api(libs.androidx.lifecycle.runtime.compose)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// Android host tests run on a stub android.jar. A composition calls android.os.Trace and, on an error, android.util.Log,
// and both throw "not mocked". So the tests on a real composition run on the JVM and Kotlin/Native only.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter.excludeTestsMatching("com.mairwunnx.capsa.compose.SlotTest")
    filter.excludeTestsMatching("com.mairwunnx.capsa.compose.CollectEffectsCompositionTest")
}
