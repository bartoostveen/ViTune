package app.vitune.desktop

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.vitune.desktop.model.AppState
import app.vitune.desktop.player.AudioEngine
import app.vitune.desktop.player.PlayerController
import app.vitune.desktop.player.YtDlp
import app.vitune.desktop.ui.App
import app.vitune.desktop.ui.AppTheme
import app.vitune.desktop.ui.MissingVlcScreen
import app.vitune.desktop.ui.UiActions
import kotlinx.coroutines.CancellationException
import java.awt.Dimension
import java.nio.file.Path
import kotlin.io.path.Path

/** %LOCALAPPDATA%\ViTuneDesktop on Windows, ~/.vitune-desktop elsewhere. */
private val dataDir: Path =
    System.getenv("LOCALAPPDATA")?.let { Path(it, "ViTuneDesktop") }
        ?: Path(System.getProperty("user.home"), ".vitune-desktop")

fun main() {
    val vlcAvailable = AudioEngine.isVlcAvailable()

    application {
        val windowState = rememberWindowState(width = 1320.dp, height = 840.dp)

        if (!vlcAvailable) {
            Window(onCloseRequest = ::exitApplication, title = "ViTune", state = windowState) {
                AppTheme { MissingVlcScreen() }
            }
            return@application
        }

        val scope = rememberCoroutineScope()
        val ytDlp = remember { YtDlp(dataDir) }
        val engine = remember { AudioEngine() }
        val appState = remember { AppState(dataDir.toFile(), scope) }
        val controller = remember { PlayerController(scope, engine, ytDlp) }
        val ui = remember { UiActions(controller, appState, scope) }

        remember {
            controller.onSongStarted = { song -> appState.addToRecentlyPlayed(song) }
        }

        var setupStatus by remember { mutableStateOf<String?>("Getting ready…") }
        var setupFailed by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            appState.load()
            // Restore the listener's preferences
            appState.settings.let { s ->
                controller.changeVolume(s.volume)
                controller.autoplay = s.autoplay
                controller.changePlaybackSpeed(s.playbackSpeed)
                controller.changeEqualizerPreset(s.equalizerPreset)
            }
            try {
                ytDlp.prepare { status -> setupStatus = status }
                setupStatus = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setupFailed = true
                setupStatus = "Couldn't set up yt-dlp: ${e.message}. Check your connection and restart the app."
            }
        }

        DisposableEffect(Unit) {
            onDispose { engine.release() }
        }

        Window(
            onCloseRequest = {
                appState.updateSettings { it.copy(volume = controller.volume) }
                appState.flush()
                exitApplication()
            },
            title = controller.current?.let { "${it.title} • ${it.artists} – ViTune" } ?: "ViTune",
            state = windowState,
            onKeyEvent = { event -> handleShortcut(event, ui) }
        ) {
            LaunchedEffect(Unit) { window.minimumSize = Dimension(980, 640) }
            AppTheme {
                App(ui, setupStatus, setupFailed)
            }
        }
    }
}

/** Global keyboard shortcuts. Returns true if the key was handled. */
private fun handleShortcut(event: KeyEvent, ui: UiActions): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val c = ui.controller
    val action: (() -> Unit)? = when {
        event.isCtrlPressed -> when (event.key) {
            Key.P -> c::togglePlayPause
            Key.DirectionRight -> c::next
            Key.DirectionLeft -> c::previous
            Key.DirectionUp -> { { c.changeVolume(c.volume + 5) } }
            Key.DirectionDown -> { { c.changeVolume(c.volume - 5) } }
            Key.L -> c.current?.let { song -> { ui.toggleLike(song) } }
            Key.S -> { { c.toggleShuffle(); ui.toast(if (c.isShuffled) "Shuffle on" else "Shuffle off") } }
            Key.R -> { { c.cycleRepeatMode(); ui.toast("Repeat: ${c.repeatMode.name.lowercase()}") } }
            Key.F, Key.K -> ui::focusSearch
            else -> null
        }
        event.isAltPressed -> when (event.key) {
            Key.DirectionLeft -> ui::goBack
            else -> null
        }
        event.isShiftPressed -> when (event.key) {
            Key.DirectionRight -> { { c.seekBy(10_000) } }
            Key.DirectionLeft -> { { c.seekBy(-10_000) } }
            else -> null
        }
        event.key == Key.Escape && ui.nowPlayingOpen -> { { ui.nowPlayingOpen = false } }
        ui.typing -> null
        else -> when (event.key) {
            Key.Spacebar -> c::togglePlayPause
            Key.M -> c::toggleMute
            Key.F -> if (c.current != null) { { ui.nowPlayingOpen = !ui.nowPlayingOpen } } else null
            else -> null
        }
    }
    action?.invoke()
    return action != null
}
