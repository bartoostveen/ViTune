plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.android.lint)
}

dependencies {
    implementation(libs.kotlin.coroutines)
    api(libs.kotlin.datetime)

    implementation(libs.ktor.http)
    implementation(libs.ktor.serialization.json)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
