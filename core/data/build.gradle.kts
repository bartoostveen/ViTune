plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "app.vitune.core.data"
    compileSdk = 36

    defaultConfig {
        minSdk = 21
    }
}

kotlin {
    jvmToolchain(libs.versions.jvm.get().toInt())

    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xcontext-parameters",
            "-Xnon-local-break-continue",
            "-Xconsistent-data-class-copy-visibility"
        )
    }
}

dependencies {
    implementation(libs.core.ktx)

    api(libs.kotlin.datetime)

    detektPlugins(libs.detekt.compose)
    detektPlugins(libs.detekt.formatting)
}
