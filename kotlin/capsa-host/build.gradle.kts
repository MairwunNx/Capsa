import org.jetbrains.kotlin.gradle.ExperimentalKotlinGradlePluginApi
import org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType

plugins {
    id("capsa.library")
}

description = "What every Capsa host shares: the error policy, the logger, saving slices, and the foreground of the app."

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
        all {
            languageSettings.optIn("com.mairwunnx.capsa.InternalCapsaApi")
        }
        commonMain.dependencies {
            api(projects.capsaCore)
            api(libs.kotlinx.serialization.core)
            api(libs.androidx.savedstate)
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
