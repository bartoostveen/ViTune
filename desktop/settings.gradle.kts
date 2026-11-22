rootProject.name = "vitune-desktop"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        // ViTune's own Maven repo (hosts the Brotli decoder build it uses)
        maven("https://git.bartoostveen.nl/api/packages/bart/maven")
    }

    // Reuse ViTune's version catalog so library versions stay in sync with the Android app
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}
