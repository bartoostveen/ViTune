plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(projects.providers.common)

    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.json)
    api(libs.ktor.http)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
