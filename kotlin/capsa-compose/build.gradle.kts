plugins {
    id("capsa.library.compose")
}

description = "Compose Multiplatform helpers for Capsa: effect collection and slots."

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.capsaCore)
            implementation(libs.compose.runtime)
        }
    }
}
