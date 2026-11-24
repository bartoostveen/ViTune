import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.compose") version "1.12.2"
}

kotlin {
    compilerOptions {
        // ViTune's provider code uses Kotlin context parameters
        freeCompilerArgs.add("-Xcontext-parameters")
    }

    // Compile ViTune's platform-independent modules straight from the parent repo,
    // so upstream fixes to the YouTube Music code arrive with a simple `git pull`.
    sourceSets.named("main") {
        kotlin.srcDirs(
            "src/main/kotlin",
            "../providers/common/src/main/kotlin",
            "../providers/innertube/src/main/kotlin",
            "../providers/lrclib/src/main/kotlin",
            "../ktor-client-brotli/src/main/kotlin"
        )
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    implementation(libs.kotlin.coroutines)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:${libs.kotlin.coroutines.get().version}")
    implementation(libs.kotlin.datetime)

    // Same networking stack the Android app uses for its providers
    implementation(libs.ktor.http)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.cio) // used by the LrcLib lyrics provider
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.client.logging)
    implementation(libs.ktor.client.encoding)
    implementation(libs.ktor.client.serialization)
    implementation(libs.ktor.serialization.json)
    implementation(libs.brotli)
    implementation(libs.log4j)
    implementation("org.slf4j:slf4j-nop:2.0.17")

    // Album art
    implementation(libs.coil.compose)
    implementation(libs.coil.ktor)

    // Audio playback through the user's VLC installation
    implementation("uk.co.caprica:vlcj:4.8.3")

    // JSON serialization (for persistence)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
}

// ---------------------------------------------------------------------------------------------
// Bundled VLC
//
// The installer ships its own trimmed, audio-only copy of 64-bit VLC so users don't have to
// install VLC themselves. Point -PvlcDir at an extracted VLC 3.x win64 zip
// (https://get.videolan.org/vlc/) — it defaults to ../vlc-runtime. VLC itself is never
// committed to git. Without it, the build still works and the app falls back to an installed VLC.
// ---------------------------------------------------------------------------------------------

val vlcSourceDir: File = providers.gradleProperty("vlcDir")
    .map { file(it) }
    .getOrElse(rootDir.resolve("../vlc-runtime"))
    .normalize()

val bundledResourcesDir = layout.buildDirectory.dir("bundled-resources")

// Only what's needed to play music: stream/file access, audio decoders and containers,
// TLS for HTTPS, and audio output. Video, GUI, Lua, streaming output and the like are left out.
val vlcPlugins = listOf(
    "access/libfilesystem_plugin.dll",
    "access/libhttp_plugin.dll",
    "access/libhttps_plugin.dll",
    "access/libtcp_plugin.dll",
    "audio_filter/*.dll",
    "audio_mixer/*.dll",
    "audio_output/*.dll",
    "codec/libadpcm_plugin.dll",
    "codec/libaes3_plugin.dll",
    "codec/libaraw_plugin.dll",
    "codec/libavcodec_plugin.dll",
    "codec/libfaad_plugin.dll",
    "codec/libflac_plugin.dll",
    "codec/libg711_plugin.dll",
    "codec/liblpcm_plugin.dll",
    "codec/libmpg123_plugin.dll",
    "codec/libopus_plugin.dll",
    "codec/libspdif_plugin.dll",
    "codec/libspeex_plugin.dll",
    "codec/libvorbis_plugin.dll",
    "demux/libadaptive_plugin.dll",
    "demux/libaiff_plugin.dll",
    "demux/libasf_plugin.dll",
    "demux/libau_plugin.dll",
    "demux/libcaf_plugin.dll",
    "demux/libes_plugin.dll",
    "demux/libflacsys_plugin.dll",
    "demux/libmkv_plugin.dll",
    "demux/libmp4_plugin.dll",
    "demux/libmpc_plugin.dll",
    "demux/libnoseek_plugin.dll",
    "demux/libogg_plugin.dll",
    "demux/librawaud_plugin.dll",
    "demux/libts_plugin.dll",
    "demux/libtta_plugin.dll",
    "demux/libvoc_plugin.dll",
    "demux/libwav_plugin.dll",
    "demux/libxa_plugin.dll",
    "keystore/*.dll",
    "logger/*.dll",
    "meta_engine/*.dll",
    "misc/libgnutls_plugin.dll",
    "misc/liblogger_plugin.dll",
    "misc/libxml_plugin.dll",
    "packetizer/*.dll",
    "stream_filter/*.dll"
)

val prepareBundledVlc = tasks.register<Sync>("prepareBundledVlc") {
    description = "Copies a trimmed, audio-only VLC into the app resources."
    inputs.property("vlcSourceDir", vlcSourceDir.absolutePath)

    // Compose Desktop picks resources for this platform from the "windows-x64" folder
    // and exposes them at runtime through the compose.application.resources.dir property.
    into(bundledResourcesDir.map { it.dir("windows-x64/vlc") })

    if (vlcSourceDir.resolve("libvlc.dll").isFile) {
        from(vlcSourceDir) {
            include("libvlc.dll", "libvlccore.dll", "COPYING.txt", "AUTHORS.txt", "README.txt")
            include(vlcPlugins.map { "plugins/$it" })
        }
    } else {
        doFirst {
            logger.warn(
                "No VLC found at $vlcSourceDir: the app will need an installed VLC. " +
                    "Pass -PvlcDir=<extracted VLC win64 folder> to bundle one."
            )
        }
    }
}

compose.desktop {
    application {
        mainClass = "app.vitune.desktop.MainKt"

        nativeDistributions {
            appResourcesRootDir.set(bundledResourcesDir)

            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "ViTune"
            packageVersion = "1.0.0"
            description = "Modern music player for streaming and local files"
            modules("java.net.http", "java.naming", "jdk.unsupported")

            windows {
                menuGroup = "ViTune"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "6f1d2c3e-8b7a-4e3f-9a51-2c0d7e9b4a18"
            }
        }
    }
}

// Compose copies appResourcesRootDir in this task; make sure VLC is in place first.
tasks.matching { it.name == "prepareAppResources" }.configureEach {
    dependsOn(prepareBundledVlc)
}
