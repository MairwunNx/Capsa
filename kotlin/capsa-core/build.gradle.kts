plugins {
    id("capsa.library")
}

description = "The Capsa engine: capabilities, slices, works, and effects on coroutines."

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
