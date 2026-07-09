plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
