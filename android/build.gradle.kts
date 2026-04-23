plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.ktlint) apply false
}

val vendoredProjects =
    setOf(
        ":pi-ai-core",
        ":pi-agent-core",
        ":pi-coding-agent-core",
    )

configure(subprojects.filter { it.path in vendoredProjects }) {
    pluginManager.withPlugin("org.jlleitschuh.gradle.ktlint") {
        tasks.configureEach {
            if (name == "loadKtlintReporters" || name.startsWith("ktlint") || name.startsWith("runKtlint")) {
                enabled = false
            }
        }
    }
}

tasks.register("qualityCheck") {
    group = "verification"
    description = "Runs the prototype code quality gates."
    dependsOn(":app:ktlintCheck", ":app:lintDebug")
}

tasks.register("formatCode") {
    group = "formatting"
    description = "Formats Kotlin sources with ktlint."
    dependsOn(":app:ktlintFormat")
}
