plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.kotlin.compose.gradle.plugin)
    implementation(libs.kotlin.allopen.gradle.plugin)
    implementation(libs.android.gradle.plugin)
    implementation(libs.maven.publish.gradle.plugin)
    implementation(libs.binary.compatibility.validator.gradle.plugin)
    implementation(libs.kover.gradle.plugin)
    implementation(libs.ktlint.gradle.plugin)
    implementation(libs.detekt.gradle.plugin)
    implementation(libs.kotlinx.benchmark.gradle.plugin)
}
