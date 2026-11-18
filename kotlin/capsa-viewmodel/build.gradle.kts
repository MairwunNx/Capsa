import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("capsa.library")
}

description = "Hosts a Capsa runtime in an AndroidX ViewModel on every platform."

kotlin {
    // jvmCommonMain holds the code that the JVM and Android share: threads and class names.
    @OptIn(ExperimentalKotlinGradlePluginApi::class)
    applyDefaultHierarchyTemplate {
        common {
            group("jvmCommon") {
                withJvm()
                withCompilations { it.platformType == KotlinPlatformType.androidJvm }
            }
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(projects.capsaCore)
            api(libs.androidx.lifecycle.viewmodel)
            api(libs.androidx.lifecycle.viewmodel.savedstate)
            api(libs.kotlinx.serialization.core)
            implementation(libs.androidx.savedstate)
            implementation(libs.kotlinx.atomicfu)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain.dependencies {
            implementation(libs.androidx.startup)
            implementation(libs.androidx.lifecycle.process)
        }
    }
}

// Android host tests run on a stub android.jar, where Bundle, and with it every saved snapshot, throws "not mocked".
// CapsaViewModelTest saves snapshots in most tests, so it runs on the JVM and Kotlin/Native only.
tasks.withType<Test>().matching { it.name == "testAndroidHostTest" }.configureEach {
    filter.excludeTestsMatching("com.mairwunnx.capsa.viewmodel.CapsaViewModelTest")
}
