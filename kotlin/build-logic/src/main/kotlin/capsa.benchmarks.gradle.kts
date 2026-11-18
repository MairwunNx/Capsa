// The benchmarks module: microbenchmarks through kotlinx-benchmark, JMH on the JVM, plus a native run on macosArm64. Never published.

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.allopen")
    id("org.jetbrains.kotlinx.benchmark")
    id("capsa.lint")
}

kotlin {
    jvmToolchain(libs.version("jvm-toolchain").toInt())
    jvm()
    macosArm64()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.findLibrary("kotlinx-benchmark-runtime").get())
        }
    }
}

// kotlinx-benchmark adds a source set with the native runner it generates into build/. That code follows its style, not ours,
// and the lint tasks of that source set would read it before the generator writes it, so they stay off.
tasks.matching { it.name.contains("MacosArm64BenchmarkSourceSet") }.configureEach {
    enabled = false
}

// JMH subclasses the benchmark classes, so they must be open.
allOpen {
    annotation("org.openjdk.jmh.annotations.State")
}

benchmark {
    configurations {
        // The full run: `./gradlew :capsa-benchmarks:benchmark`. The release and the autopilot compare its JVM numbers with the baseline.
        named("main") {
            warmups = 3
            iterations = 5
            iterationTime = 1
            iterationTimeUnit = "s"
            mode = "thrpt"
            outputTimeUnit = "ms"
            advanced("jvmForks", 1)
        }
        // One short iteration of every benchmark on every push: it proves that the benchmarks still run, the numbers mean nothing.
        register("smoke") {
            // The native runner calibrates the iteration in the warmup and reports zero without one.
            warmups = 1
            iterations = 1
            iterationTime = 200
            iterationTimeUnit = "ms"
            mode = "thrpt"
            outputTimeUnit = "ms"
            advanced("jvmForks", 1)
        }
    }
    targets {
        register("jvm")
        register("macosArm64")
    }
}

// kotlinx-benchmark 0.5.0 has no option for JMH profilers, so this task runs the JMH jar it builds with `-prof gc`.
// The settings match the main configuration. The report is the input of tools/bench_compare.py and the source of the baseline.
// The plugin creates jvmBenchmarkJar after the project is evaluated, so the jar is wired by its path and the task name.
val jmhJar = files(layout.buildDirectory.file("benchmarks/jvm/jars/${project.name}-jvm-jmh-JMH.jar")).builtBy("jvmBenchmarkJar")
tasks.register<JavaExec>("jvmGcBenchmark") {
    group = "benchmark"
    description = "Runs the JVM benchmarks with the JMH GC profiler: throughput and allocations per operation"
    val report = layout.buildDirectory.file("reports/benchmarks/gc/jvm.json")
    inputs.files(jmhJar)
    outputs.file(report)
    outputs.upToDateWhen { false }
    classpath(jmhJar)
    mainClass = "org.openjdk.jmh.Main"
    javaLauncher = javaToolchains.launcherFor { languageVersion = JavaLanguageVersion.of(libs.version("jvm-toolchain").toInt()) }
    argumentProviders += CommandLineArgumentProvider {
        listOf("-wi", "3", "-w", "1s", "-i", "5", "-r", "1s", "-f", "1", "-bm", "thrpt", "-tu", "ms", "-prof", "gc") +
            listOf("-rf", "json", "-rff", report.get().asFile.absolutePath)
    }
    doFirst { report.get().asFile.parentFile.mkdirs() }
}
