plugins {
    id("capsa.benchmarks")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.capsaCore)
        }
    }
}
