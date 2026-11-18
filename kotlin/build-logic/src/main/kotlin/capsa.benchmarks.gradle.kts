// The benchmarks module: JVM microbenchmarks through kotlinx-benchmark and JMH. Never published.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
    id("capsa.lint")
}

kotlin {
    jvmToolchain(libs.version("jvm-toolchain").toInt())
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-benchmark-runtime").get())
        }
    }
}

// JMH subclasses the benchmark classes, so they must be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    targets {
        register("jvm")
    }
}
