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
            implementation(libs.kotlinx.atomicfu)
        }
        androidMain.dependencies {
            implementation(libs.androidx.startup)
        }
    }
}
