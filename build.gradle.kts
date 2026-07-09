import dev.detekt.gradle.Detekt

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.lint) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.chaquo) apply false
    alias(libs.plugins.detekt)
}

tasks.register<Delete>("clean") {
    description = "Clean all build files"

    delete(rootProject.layout.buildDirectory.asFile)
}

val topLevelLibs = libs

allprojects {
    group = "app.vitune"
    version = "1.2.2"

    apply(plugin = "dev.detekt")

    detekt {
        buildUponDefaultConfig = true
        allRules = false
        config.setFrom("$rootDir/detekt.yml")
    }

    tasks.withType<Detekt>().configureEach {
        jvmTarget = "25"
        reports {
            html.required = true
        }
    }

    dependencies {
        detektPlugins(topLevelLibs.detekt.compose)
        detektPlugins(topLevelLibs.detekt.formatting)
    }
}

tasks.named<UpdateDaemonJvm>("updateDaemonJvm") {
    languageVersion = JavaLanguageVersion.of(25)
}
