package app.vitune.desktop.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.net.URI

@Composable
fun App(ui: UiActions, setupStatus: String?, setupFailed: Boolean) {
    val controller = ui.controller

    CompositionLocalProvider(LocalUi provides ui) {
        Box(Modifier.fillMaxSize().background(Palette.Background)) {
            Column(Modifier.fillMaxSize()) {
                if (setupStatus != null) {
                    Banner(text = setupStatus, isError = setupFailed, showSpinner = !setupFailed)
                }

                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Sidebar()

                    // Main content
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Palette.Panel)
                    ) {
                        TopBar()
                        AnimatedContent(
                            targetState = ui.screen,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(120)) },
                            label = "screen",
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        ) { screen ->
                            when (screen) {
                                Screen.Home -> HomePanel()
                                is Screen.Search -> SearchPanel(screen.query)
                                Screen.Library -> LibraryPanel()
                                Screen.Liked -> LikedSongsPanel(Modifier.fillMaxSize())
                                is Screen.Playlist -> PlaylistPanel(screen.id, Modifier.fillMaxSize())
                            }
                        }
                    }

                    if (ui.appState.settings.showSidePanel) {
                        SidePanel(Modifier.width(340.dp).fillMaxHeight())
                    }
                }

                controller.error?.let { message ->
                    Banner(text = message, isError = true, onDismiss = { controller.error = null })
                }

                PlayerBar()
            }

            // Full-window Now Playing view
            AnimatedVisibility(
                visible = ui.nowPlayingOpen && controller.current != null,
                enter = slideInVertically(tween(320)) { it } + fadeIn(),
                exit = slideOutVertically(tween(260)) { it } + fadeOut()
            ) {
                NowPlayingScreen()
            }

            ToastHost(Modifier.align(Alignment.BottomCenter).padding(bottom = 104.dp))
        }

        ui.newPlaylistFor?.let { request ->
            PlaylistEditorDialog(
                title = "Create playlist",
                confirmLabel = "Create",
                onDismiss = ui::dismissNewPlaylist,
                onConfirm = { name, description ->
                    val playlist = ui.appState.createPlaylist(name, description)
                    request.song?.let { ui.appState.addToPlaylist(playlist.id, it) }
                    ui.dismissNewPlaylist()
                    ui.toast(if (request.song != null) "Added to ${playlist.name}" else "Playlist created")
                    if (request.song == null) ui.navigate(Screen.Playlist(playlist.id))
                }
            )
        }
        if (ui.shortcutsOpen) ShortcutsDialog(onDismiss = { ui.shortcutsOpen = false })
        if (ui.importDialogOpen) ImportPlaylistDialog()
    }
}

/** Back button and the current screen's quick actions. */
@Composable
private fun TopBar() {
    val ui = LocalUi.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp)
    ) {
        ToolButton(
            AppIcons.ArrowBack, "Go back",
            onClick = ui::goBack,
            enabled = ui.canGoBack,
            showDot = false,
            size = 34.dp, iconSize = 20.dp,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.3f))
        )
        Spacer(Modifier.weight(1f))
        if (ui.screen !is Screen.Search) {
            PillButton("Search", onClick = { ui.focusSearch() }, icon = AppIcons.Search)
        }
    }
}

@Composable
private fun ToastHost(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    var visible by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    LaunchedEffect(ui.toastMessage) {
        val message = ui.toastMessage ?: return@LaunchedEffect
        text = message.first
        visible = true
        delay(2200)
        visible = false
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + slideOutVertically { it },
        modifier = modifier
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.Black,
            modifier = Modifier
                .shadow(12.dp, RoundedCornerShape(10.dp))
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 18.dp, vertical = 11.dp)
        )
    }
}

@Composable
private fun Banner(
    text: String,
    isError: Boolean,
    showSpinner: Boolean = false,
    onDismiss: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 8.dp, top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (isError) MaterialTheme.colorScheme.errorContainer else Palette.PanelRaised)
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        if (showSpinner) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = Palette.Violet, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else Palette.TextSecondary,
            modifier = Modifier.weight(1f).padding(vertical = 8.dp)
        )
        if (onDismiss != null) {
            ToolButton(AppIcons.Close, "Dismiss", onClick = onDismiss, size = 30.dp, iconSize = 16.dp)
        }
    }
}

@Composable
fun MissingVlcScreen() {
    Surface(color = Palette.Background, modifier = Modifier.fillMaxSize()) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 520.dp).padding(32.dp)
            ) {
                Text(
                    text = "VLC media player is needed",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "ViTune plays audio through VLC. Install the 64-bit version of VLC, " +
                        "then restart this app.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Palette.TextSecondary
                )
                Spacer(Modifier.height(24.dp))
                Button(onClick = {
                    runCatching { Desktop.getDesktop().browse(URI("https://www.videolan.org/vlc/")) }
                }) {
                    Text("Download VLC")
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Or run:  winget install VideoLAN.VLC",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextMuted
                )
            }
        }
    }
}
