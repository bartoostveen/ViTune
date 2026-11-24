package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.vitune.desktop.model.PlaylistImport
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.net.URI

private val SpotifyGreen = Color(0xFF1ED760)

/** "Import playlist" dialog: paste a Spotify link or pick a CSV export, then watch it fill in. */
@Composable
fun ImportPlaylistDialog() {
    val ui = LocalUi.current
    val progress = ui.importProgress
    DisposableEffect(Unit) {
        ui.typing = true
        onDispose { ui.typing = false }
    }

    Dialog(
        onDismissRequest = { ui.importDialogOpen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = Palette.PanelRaised,
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 24.dp,
            modifier = Modifier.width(520.dp)
        ) {
            Column(Modifier.padding(24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(SpotifyGreen, Color(0xFF169C46))))
                    ) {
                        Icon(AppIcons.Import, null, tint = Color.Black, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Import a playlist", style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
                        Text(
                            "Bring your Spotify playlists and albums to ViTune",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary
                        )
                    }
                    ToolButton(AppIcons.Close, "Close", onClick = { ui.importDialogOpen = false }, size = 32.dp, iconSize = 18.dp)
                }
                Spacer(Modifier.height(20.dp))

                when {
                    progress == null || progress.error != null -> ImportForm(progress?.error)
                    progress.running -> ImportRunning(progress)
                    else -> ImportFinished(progress)
                }
            }
        }
    }
}

@Composable
private fun ImportForm(error: String?) {
    val ui = LocalUi.current
    var link by remember { mutableStateOf("") }
    var formError by remember(error) { mutableStateOf(error) }

    fun importLink() {
        val ref = PlaylistImport.parseSpotifyLink(link)
        if (ref == null) {
            formError = "That doesn't look like a Spotify link. Open the playlist in Spotify, choose Share → Copy link, and paste it here."
            return
        }
        ui.startImport("Spotify") { PlaylistImport.fetchSpotify(ref) }
    }

    Text("SPOTIFY LINK", style = SectionLabelStyle, color = Palette.TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .height(44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f))
                .border(1.dp, if (formError != null) MaterialTheme.colorScheme.error else Palette.Outline, RoundedCornerShape(10.dp))
                .padding(horizontal = 14.dp)
        ) {
            if (link.isEmpty()) {
                Text("https://open.spotify.com/playlist/…", style = MaterialTheme.typography.bodyMedium, color = Palette.TextMuted)
            }
            BasicTextField(
                value = link,
                onValueChange = { link = it.trim(); formError = null },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { importLink() }),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(Modifier.width(10.dp))
        PillButton("Import", onClick = { importLink() }, filled = true)
    }
    if (formError != null) {
        Spacer(Modifier.height(8.dp))
        Text(formError!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "Works with public playlists, albums and songs. Links import up to ${PlaylistImport.SPOTIFY_LINK_LIMIT} songs.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextSecondary
    )

    Spacer(Modifier.height(20.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Palette.Outline)
        Text("  OR  ", style = SectionLabelStyle, color = Palette.TextMuted)
        HorizontalDivider(Modifier.weight(1f), color = Palette.Outline)
    }
    Spacer(Modifier.height(20.dp))

    Text("CSV FILE (FOR LONGER PLAYLISTS)", style = SectionLabelStyle, color = Palette.TextMuted)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                "Export any playlist, of any length, as a CSV file from",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary
            )
            Text(
                "exportify.net ↗",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = SpotifyGreen,
                modifier = Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { runCatching { Desktop.getDesktop().browse(URI("https://exportify.net")) } }
            )
        }
        Spacer(Modifier.width(10.dp))
        PillButton("Choose CSV file…", icon = AppIcons.Library, onClick = {
            val file = chooseCsvFile() ?: return@PillButton
            ui.startImport("CSV") {
                PlaylistImport.parseCsv(file.readText(Charsets.UTF_8), file.nameWithoutExtension)
            }
        })
    }
}

@Composable
private fun ColumnScope.ImportRunning(progress: UiActions.ImportProgress) {
    val ui = LocalUi.current
    Text(
        if (progress.total == 0) "Reading playlist…" else progress.sourceName,
        style = MaterialTheme.typography.titleMedium,
        color = Palette.TextPrimary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
    Spacer(Modifier.height(12.dp))
    if (progress.total == 0) {
        LinearProgressIndicator(color = SpotifyGreen, trackColor = Palette.PanelHover, modifier = Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(
            progress = { progress.done.toFloat() / progress.total },
            color = SpotifyGreen,
            trackColor = Palette.PanelHover,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Finding songs on YouTube Music: ${progress.done} of ${progress.total} • ${progress.matched} found",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(
        "You can close this window — the import keeps going, and you can already play the playlist.",
        style = MaterialTheme.typography.bodySmall,
        color = Palette.TextMuted
    )
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
        PillButton("Cancel import", onClick = { ui.cancelImport() })
        if (progress.playlistId != null) {
            PillButton("Open playlist", filled = true, onClick = {
                ui.importDialogOpen = false
                ui.navigate(Screen.Playlist(progress.playlistId))
            })
        } else {
            PillButton("Hide", filled = true, onClick = { ui.importDialogOpen = false })
        }
    }
}

@Composable
private fun ColumnScope.ImportFinished(progress: UiActions.ImportProgress) {
    val ui = LocalUi.current
    val playlist = progress.playlistId?.let { ui.appState.playlist(it) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        if (playlist != null) PlaylistArtwork(playlist, 64.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                progress.sourceName,
                style = MaterialTheme.typography.titleMedium,
                color = Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Imported ${progress.matched} of ${progress.total} songs",
                style = MaterialTheme.typography.bodyMedium,
                color = SpotifyGreen
            )
        }
    }
    if (progress.truncated) {
        Spacer(Modifier.height(12.dp))
        Text(
            "Spotify links only share the first ${PlaylistImport.SPOTIFY_LINK_LIMIT} songs. " +
                "To import the whole playlist, export it as CSV from exportify.net and import that file.",
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary
        )
    }
    if (progress.missing.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        Text("NOT FOUND ON YOUTUBE MUSIC (${progress.missing.size})", style = SectionLabelStyle, color = Palette.TextMuted)
        Spacer(Modifier.height(6.dp))
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 140.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.04f))
                .verticalScroll(rememberScrollState())
                .padding(10.dp)
        ) {
            progress.missing.forEach { t ->
                Text(
                    "${t.title} — ${t.artists}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
        PillButton("Import another", onClick = { ui.clearFinishedImport() })
        if (playlist != null && playlist.songs.isNotEmpty()) {
            PillButton("Open playlist", onClick = {
                ui.importDialogOpen = false
                ui.clearFinishedImport()
                ui.navigate(Screen.Playlist(playlist.id))
            })
            PillButton("Play now", icon = AppIcons.Play, filled = true, onClick = {
                ui.importDialogOpen = false
                ui.clearFinishedImport()
                ui.controller.playCollection(playlist.songs)
                ui.navigate(Screen.Playlist(playlist.id))
            })
        }
    }
}

private fun chooseCsvFile(): File? {
    val dialog = FileDialog(null as Frame?, "Choose a playlist CSV file", FileDialog.LOAD).apply {
        file = "*.csv"
        setFilenameFilter { _, name -> name.endsWith(".csv", ignoreCase = true) }
        isVisible = true
    }
    val name = dialog.file ?: return null
    return File(dialog.directory, name)
}
