plugins {
    id("com.android.application") version "8.7.3" apply false
    id("com.android.library") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("app.cash.paparazzi") version "1.3.5" apply false
    id("io.gitlab.arturbosch.detekt") version "1.23.8"
    id("org.jlleitschuh.gradle.ktlint") version "12.2.0"
}

// A single AST analysis covers both modules, including JVM and Android tests.
// It does not compile Android variants or require an emulator.
detekt {
    toolVersion = "1.23.8"
    source.setFrom(files("app/src", "core/src"))
    config.setFrom(files("config/detekt/detekt.yml"))
    baseline = file("config/detekt/baseline.xml")
    basePath = projectDir.absolutePath
    ignoreFailures = false
}

allprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.5.0")
        android.set(true)
        ignoreFailures.set(false)
        baseline.set(rootProject.file("config/ktlint/${project.name}.xml"))
        filter {
            exclude("**/build/**", "**/.tmp/**", "**/.gradle/**")
        }
    }
}

tasks.named("ktlintCheck") {
    dependsOn(":app:ktlintCheck", ":core:ktlintCheck")
}
