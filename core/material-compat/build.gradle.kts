plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.google.android.material"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }
}

dependencies {
    implementation(projects.core.ui)
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())
}
