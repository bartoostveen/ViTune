package app.vitune.desktop.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.io.File

/**
 * Plays audio through VLC (via vlcj). This replaces ExoPlayer/Media3 from the Android app.
 *
 * Listener callbacks arrive on a VLC thread. Never call back into the player from
 * inside a callback; hop to another thread first (PlayerController does this).
 */
class AudioEngine {
    interface Listener {
        fun onPlaying()
        fun onPaused()
        fun onFinished()
        fun onError()
        fun onTime(ms: Long)
        fun onLength(ms: Long)
    }

    private val factory = MediaPlayerFactory("--no-video", "--quiet")
    private val player: MediaPlayer = factory.mediaPlayers().newMediaPlayer()

    @Volatile
    var listener: Listener? = null

    init {
        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun playing(mediaPlayer: MediaPlayer) { listener?.onPlaying() }
            override fun paused(mediaPlayer: MediaPlayer) { listener?.onPaused() }
            override fun finished(mediaPlayer: MediaPlayer) { listener?.onFinished() }
            override fun error(mediaPlayer: MediaPlayer) { listener?.onError() }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) { listener?.onTime(newTime) }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { listener?.onLength(newLength) }
        })
    }

    fun play(url: String) {
        player.media().play(url)
    }

    fun pause() = player.controls().setPause(true)
    fun resume() = player.controls().setPause(false)
    fun stop() = player.controls().stop()
    fun seek(ms: Long) = player.controls().setTime(ms)

    /** 0–100. VLC only accepts this once playback has started, so the controller re-applies it. */
    fun setVolume(volume: Int) {
        player.audio().setVolume(volume)
    }

    /** Playback speed, 1.0 = normal. */
    fun setRate(rate: Float) {
        runCatching { player.controls().setRate(rate) }
    }

    /** Names of VLC's built-in equalizer presets ("Rock", "Pop", "Classical", …). */
    val equalizerPresets: List<String> by lazy {
        runCatching { factory.equalizer().presets() }.getOrDefault(emptyList())
    }

    /** Applies a preset by name, or turns the equalizer off for null. */
    fun setEqualizerPreset(name: String?) {
        runCatching {
            val equalizer = name
                ?.takeIf { it in equalizerPresets }
                ?.let { factory.equalizer().newEqualizer(it) }
            player.audio().setEqualizer(equalizer)
        }
    }

    fun release() {
        listener = null
        player.release()
        factory.release()
    }

    companion object {
        /**
         * The VLC copy packaged inside the installer (see `prepareBundledVlc` in build.gradle.kts),
         * or null when this build doesn't include one.
         */
        private val bundledVlcDir: File? by lazy {
            System.getProperty("compose.application.resources.dir")
                ?.let { File(it, "vlc") }
                ?.takeIf { File(it, "libvlc.dll").isFile }
        }

        /**
         * Finds VLC: the bundled copy first, then an installed VLC (normally under Program Files).
         * vlcj checks `jna.library.path` before the install directory, and sets VLC_PLUGIN_PATH
         * from whichever directory it picks.
         */
        fun isVlcAvailable(): Boolean = runCatching {
            bundledVlcDir?.let { System.setProperty("jna.library.path", it.absolutePath) }
            NativeDiscovery().discover()
        }.getOrDefault(false)
    }
}
