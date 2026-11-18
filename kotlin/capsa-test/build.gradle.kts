plugins {
    id("capsa.library")
}

description = "A test harness for Capsa capabilities, with no Android or iOS needed."

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.capsaCore)
            api(libs.kotlinx.coroutines.test)
        }
    }
}
