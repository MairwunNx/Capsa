// The root project: one SARIF report from every detekt task of every module, for the Security tab on GitHub.
// GitHub takes one run per tool, so the reports of the modules and source sets merge into one file.

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.report.ReportMergeTask

val detektSarif = tasks.register<ReportMergeTask>("detektSarif") {
    group = "verification"
    description = "Merges the SARIF reports of every detekt task into build/reports/detekt/detekt.sarif"
    output = layout.buildDirectory.file("reports/detekt/detekt.sarif")
}

subprojects {
    plugins.withId("dev.detekt") {
        tasks.withType<Detekt>().configureEach {
            // A finalizer runs even when detekt fails on a finding, and that's when the report matters.
            // The report is wired as a plain file, so the merge doesn't depend on the detekt tasks and doesn't run them.
            finalizedBy(detektSarif)
            val sarif = reports.sarif.outputLocation.get().asFile
            detektSarif.configure { input.from(sarif) }
        }
    }
}
