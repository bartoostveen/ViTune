plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.google.android.material"
    compileSdk { version = release(37) { minorApiLevel = 2 } }

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
