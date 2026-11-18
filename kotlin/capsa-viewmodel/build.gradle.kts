plugins {
    id("capsa.library")
}

description = "Hosts a Capsa runtime in an AndroidX ViewModel on every platform."

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.capsaCore)
        }
    }
}
