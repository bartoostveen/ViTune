package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun Sidebar(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val screen = ui.screen
    val appState = ui.appState
    val controller = ui.controller

    Column(modifier.fillMaxHeight().width(248.dp)) {
        // Navigation card
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Panel)
                .padding(vertical = 12.dp, horizontal = 8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 14.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(Palette.AccentGradient)
                ) {
                    Icon(AppIcons.MusicNote, null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text("ViTune", style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
            }
            NavRow("Home", AppIcons.Home, screen is Screen.Home) { ui.navigate(Screen.Home) }
            NavRow("Search", AppIcons.Search, screen is Screen.Search) { ui.focusSearch() }
            NavRow("Your Library", AppIcons.Library, screen is Screen.Library) { ui.navigate(Screen.Library) }
        }

        Spacer(Modifier.height(8.dp))

        // Library card
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(Palette.Panel)
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Text(
                    "PLAYLISTS",
                    style = SectionLabelStyle,
                    color = Palette.TextMuted,
                    modifier = Modifier.weight(1f)
                )
                ToolButton(
                    AppIcons.Import,
                    "Import from Spotify",
                    onClick = { ui.importDialogOpen = true },
                    size = 30.dp,
                    iconSize = 18.dp
                )
                ToolButton(
                    AppIcons.Add,
                    "Create playlist",
                    onClick = { ui.requestNewPlaylist() },
                    size = 30.dp,
                    iconSize = 18.dp
                )
            }

            // Import in progress (keeps running while the dialog is closed)
            ui.importProgress?.takeIf { it.running }?.let { p ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Palette.PanelRaised)
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { ui.importDialogOpen = true }
                        .padding(10.dp)
                ) {
                    Text(
                        "Importing ${p.sourceName.takeIf { p.total > 0 } ?: "playlist"}…",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Palette.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(6.dp))
                    if (p.total > 0) {
                        LinearProgressIndicator(
                            progress = { p.done.toFloat() / p.total },
                            color = Color(0xFF1ED760),
                            trackColor = Palette.PanelHover,
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(color = Color(0xFF1ED760), trackColor = Palette.PanelHover, modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            LazyColumn(Modifier.weight(1f)) {
                item {
                    LibraryRow(
                        title = "Liked Songs",
                        subtitle = "Playlist • ${appState.likedSongs.size} songs",
                        selected = screen is Screen.Liked,
                        playing = false,
                        artwork = { LikedArtwork(40.dp, corner = 6.dp) },
                        onClick = { ui.navigate(Screen.Liked) }
                    )
                }
                items(appState.playlists, key = { it.id }) { playlist ->
                    LibraryRow(
                        title = playlist.name,
                        subtitle = "Playlist • ${playlist.songs.size} songs",
                        selected = (screen as? Screen.Playlist)?.id == playlist.id,
                        playing = controller.current != null && playlist.songs.any { it.id == controller.current?.id },
                        artwork = { PlaylistArtwork(playlist, 40.dp, corner = 6.dp) },
                        onClick = { ui.navigate(Screen.Playlist(playlist.id)) }
                    )
                }
                if (appState.playlists.isEmpty()) {
                    item {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Palette.PanelRaised)
                                .padding(16.dp)
                        ) {
                            Text("Create your first playlist", style = MaterialTheme.typography.titleSmall, color = Palette.TextPrimary)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "It's easy, we'll help you.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Palette.TextSecondary
                            )
                            Spacer(Modifier.height(12.dp))
                            PillButton("Create playlist", onClick = { ui.requestNewPlaylist() }, filled = true)
                            Spacer(Modifier.height(8.dp))
                            PillButton("Import from Spotify", onClick = { ui.importDialogOpen = true }, icon = AppIcons.Import)
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { ui.shortcutsOpen = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(AppIcons.Keyboard, null, tint = Palette.TextMuted, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Keyboard shortcuts", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            }
        }
    }
}

@Composable
private fun NavRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = if (selected || hovered) Palette.TextPrimary else Palette.TextSecondary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, null, tint = if (selected) Palette.Violet else color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = color,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold
        )
    }
}

@Composable
private fun LibraryRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    playing: Boolean,
    artwork: @Composable () -> Unit,
    onClick: () -> Unit
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> Color.White.copy(alpha = 0.08f)
                    hovered -> Color.White.copy(alpha = 0.04f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        artwork()
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (playing) Palette.Violet else Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (playing) EqualizerBars(LocalUi.current.controller.isPlaying)
    }
}
