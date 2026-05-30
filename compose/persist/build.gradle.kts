plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.vitune.compose.persist"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.foundation)

    implementation(libs.kotlin.immutable)

    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
