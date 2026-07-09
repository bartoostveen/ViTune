plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.vitune.core.data"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xnon-local-break-continue",
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

dependencies {
    implementation(libs.core.ktx)

    api(libs.kotlin.datetime)
}
