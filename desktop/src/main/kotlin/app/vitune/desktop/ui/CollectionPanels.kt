package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.desktop.model.Song

/** Shared body for Liked Songs and playlists: header, action row, filter and track list. */
@Composable
private fun CollectionScreen(
    songs: List<Song>,
    header: @Composable (actions: @Composable RowScope.() -> Unit) -> Unit,
    empty: @Composable () -> Unit,
    playlistId: String? = null,
    menu: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    val ui = LocalUi.current
    val controller = ui.controller
    var filter by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    val playingFromHere = controller.current != null && songs.any { it.id == controller.current?.id }
    val shown = if (filter.isBlank()) songs else songs.filter {
        it.title.contains(filter, ignoreCase = true) || it.artists.contains(filter, ignoreCase = true)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item {
            header {
                GradientPlayButton(
                    isPlaying = playingFromHere && controller.isPlaying,
                    enabled = songs.isNotEmpty(),
                    size = 56.dp,
                    onClick = {
                        if (playingFromHere) controller.togglePlayPause() else controller.playCollection(songs)
                    }
                )
                ToolButton(
                    AppIcons.Shuffle, "Shuffle play",
                    onClick = { controller.playCollection(songs, shuffle = true) },
                    enabled = songs.isNotEmpty(),
                    active = playingFromHere && controller.isShuffled,
                    size = 44.dp, iconSize = 26.dp
                )
                ToolButton(
                    AppIcons.QueueMusic, "Add all to queue",
                    onClick = {
                        controller.addAllToQueue(songs)
                        ui.toast("Added ${songs.size} songs to queue")
                    },
                    enabled = songs.isNotEmpty(),
                    size = 44.dp, iconSize = 24.dp
                )
                if (menu != null) {
                    Box {
                        ToolButton(AppIcons.MoreHoriz, "More", onClick = { menuOpen = true }, size = 44.dp, iconSize = 26.dp)
                        androidx.compose.material3.DropdownMenu(
                            expanded = menuOpen,
                            onDismissRequest = { menuOpen = false },
                            containerColor = Palette.PanelRaised
                        ) { menu { menuOpen = false } }
                    }
                }
                Spacer(Modifier.weight(1f))
                if (songs.size > 5) FilterField(filter, onChange = { filter = it })
            }
        }
        if (songs.isEmpty()) {
            item { empty() }
        } else {
            item { TrackListHeader(Modifier.padding(horizontal = 16.dp)) }
            itemsIndexed(shown, key = { _, s -> s.id }) { i, song ->
                TrackRow(
                    song = song,
                    index = i,
                    playlistId = playlistId,
                    onPlay = { controller.playCollection(shown, i) },
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        "No songs match \"$filter\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Palette.TextSecondary,
                        modifier = Modifier.padding(horizontal = 44.dp, vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterField(value: String, onChange: (String) -> Unit) {
    val ui = LocalUi.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .width(220.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.07f))
            .padding(horizontal = 10.dp)
    ) {
        Icon(AppIcons.Search, null, tint = Palette.TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) Text("Find in list", style = MaterialTheme.typography.bodySmall, color = Palette.TextMuted)
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = Palette.TextPrimary),
                cursorBrush = SolidColor(Palette.Violet),
                modifier = Modifier.fillMaxWidth().onFocusChanged { ui.typing = it.isFocused }
            )
        }
        if (value.isNotEmpty()) ToolButton(AppIcons.Close, "Clear", onClick = { onChange("") }, size = 24.dp, iconSize = 14.dp)
    }
}

private fun summary(songs: List<Song>): String {
    val total = songs.sumOf { it.durationMs }
    val count = "${songs.size} song${if (songs.size == 1) "" else "s"}"
    return if (total > 0) "$count • ${formatTotalDuration(total)}" else count
}

@Composable
fun LikedSongsPanel(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val songs = ui.appState.likedSongs
    Box(modifier) {
        CollectionScreen(
            songs = songs,
            header = { actions ->
                CollectionHeader(
                    label = "Playlist",
                    title = "Liked Songs",
                    subtitle = summary(songs),
                    accent = Color(0xFF5B3BE8),
                    artwork = { LikedArtwork(200.dp, corner = 10.dp) },
                    actions = actions
                )
            },
            empty = {
                EmptyState(
                    AppIcons.FavoriteBorder,
                    "Songs you like will appear here",
                    "Save songs by tapping the heart icon.",
                    action = { PillButton("Find songs", onClick = { ui.focusSearch() }, filled = true) }
                )
            }
        )
    }
}

@Composable
fun PlaylistPanel(playlistId: String, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val playlist = ui.appState.playlist(playlistId)
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (playlist == null) {
        EmptyState(AppIcons.QueueMusic, "Playlist not found", "It may have been deleted.", modifier)
        return
    }

    Box(modifier) {
        CollectionScreen(
            songs = playlist.songs,
            playlistId = playlist.id,
            header = { actions ->
                CollectionHeader(
                    label = "Playlist",
                    title = playlist.name,
                    description = playlist.description,
                    subtitle = summary(playlist.songs),
                    accent = Palette.tileColorFor(playlist.id),
                    artwork = { PlaylistArtwork(playlist, 200.dp, corner = 10.dp) },
                    actions = actions
                )
            },
            empty = {
                EmptyState(
                    AppIcons.QueueMusic,
                    "Let's find something for your playlist",
                    "Right-click any song (or use its ⋯ menu) and choose \"Add to playlist\".",
                    action = { PillButton("Search songs", onClick = { ui.focusSearch() }, icon = AppIcons.Search, filled = true) }
                )
            },
            menu = { dismiss ->
                MenuItem("Edit details", AppIcons.Edit) { dismiss(); editing = true }
                MenuItem("Delete playlist", AppIcons.Delete) { dismiss(); confirmDelete = true }
            }
        )
    }

    if (editing) {
        PlaylistEditorDialog(
            title = "Edit details",
            initialName = playlist.name,
            initialDescription = playlist.description,
            confirmLabel = "Save",
            onDismiss = { editing = false },
            onConfirm = { name, description ->
                ui.appState.updatePlaylist(playlist.withName(name).withDescription(description))
                editing = false
            }
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Delete playlist?",
            message = "This will delete \"${playlist.name}\" from Your Library.",
            confirmLabel = "Delete",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                ui.appState.deletePlaylist(playlist.id)
                ui.navigate(Screen.Library)
                ui.toast("Playlist deleted")
            }
        )
    }
}

@Composable
fun LibraryPanel(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val appState = ui.appState
    val controller = ui.controller
    var tab by remember { mutableStateOf(0) }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 28.dp, bottom = 32.dp)) {
        item {
            Text("Your Library", style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Playlists", onClick = { tab = 0 }, selected = tab == 0)
                PillButton("Recently played", onClick = { tab = 1 }, selected = tab == 1)
            }
            Spacer(Modifier.height(20.dp))
        }

        if (tab == 0) {
            item {
                data class Tile(val title: String, val subtitle: String, val art: @Composable () -> Unit, val onClick: () -> Unit, val onPlay: (() -> Unit)?)

                val tiles = buildList {
                    add(Tile("Liked Songs", "${appState.likedSongs.size} songs", { LikedArtwork(160.dp) },
                        { ui.navigate(Screen.Liked) },
                        appState.likedSongs.takeIf { it.isNotEmpty() }?.let { s -> { controller.playCollection(s) } }))
                    appState.playlists.forEach { p ->
                        add(Tile(p.name, "${p.songs.size} songs", { PlaylistArtwork(p, 160.dp) },
                            { ui.navigate(Screen.Playlist(p.id)) },
                            p.songs.takeIf { it.isNotEmpty() }?.let { s -> { controller.playCollection(s) } }))
                    }
                    add(Tile("New playlist", "Create", {
                        Box(
                            Modifier.size(160.dp).clip(RoundedCornerShape(8.dp)).background(Palette.PanelRaised),
                            contentAlignment = Alignment.Center
                        ) { Icon(AppIcons.Add, null, tint = Palette.TextSecondary, modifier = Modifier.size(48.dp)) }
                    }, { ui.requestNewPlaylist() }, null))
                    add(Tile("Import from Spotify", "Playlist, album or CSV", {
                        Box(
                            Modifier.size(160.dp).clip(RoundedCornerShape(8.dp))
                                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF1ED760), Color(0xFF0E5E2B)))),
                            contentAlignment = Alignment.Center
                        ) { Icon(AppIcons.Import, null, tint = Color.Black, modifier = Modifier.size(48.dp)) }
                    }, { ui.importDialogOpen = true }, null))
                }

                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val columns = (maxWidth / 196.dp).toInt().coerceAtLeast(2)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tiles.chunked(columns).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { t ->
                                    LibraryTile(t.title, t.subtitle, t.art, t.onClick, t.onPlay, Modifier.weight(1f))
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        } else {
            val recent = appState.recentlyPlayed
            if (recent.isEmpty()) {
                item { EmptyState(AppIcons.History, "Nothing here yet", "Songs you play will show up here.") }
            } else {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${recent.size} songs",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Palette.TextSecondary,
                            modifier = Modifier.weight(1f)
                        )
                        PillButton("Play all", onClick = { controller.playCollection(recent) }, icon = AppIcons.Play)
                        Spacer(Modifier.width(8.dp))
                        PillButton("Clear history", onClick = { appState.clearRecentlyPlayed() }, icon = AppIcons.Delete)
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = Palette.Outline)
                    Spacer(Modifier.height(8.dp))
                }
                itemsIndexed(recent, key = { _, s -> s.id }) { i, song ->
                    TrackRow(song, index = i, onPlay = { controller.playRadio(song) })
                }
            }
        }
    }
}

@Composable
private fun LibraryTile(
    title: String,
    subtitle: String,
    art: @Composable () -> Unit,
    onClick: () -> Unit,
    onPlay: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
            art()
            if (hovered && onPlay != null) {
                GradientPlayButton(false, onClick = onPlay, size = 44.dp, modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp))
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Palette.TextSecondary, maxLines = 1)
    }
}
