plugins {
    id("capsa.library")
}

description = "Runs a Capsa view model in a test on virtual time."

kotlin {
    sourceSets {
        all {
            languageSettings.optIn("com.mairwunnx.capsa.InternalCapsaApi")
        }
        commonMain.dependencies {
            api(projects.capsaViewmodel)
            api(libs.kotlinx.coroutines.test)
        }
    }
}
