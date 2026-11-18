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
