package app.vitune.desktop.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vitune.desktop.model.MusicRepository
import app.vitune.desktop.model.Song
import java.time.LocalTime

/** Genres/moods offered as one-click searches. */
val MoodSearches = listOf(
    "Chill vibes", "Workout", "Focus", "Lo-fi beats", "Party", "Romance",
    "Bollywood hits", "Hip-hop", "Rock classics", "Jazz", "Indie", "Sleep"
)

@Composable
fun HomePanel(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val appState = ui.appState
    val controller = ui.controller
    val recent = appState.recentlyPlayed

    val greeting = remember {
        when (LocalTime.now().hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }

    // Recommendations seeded by what was played recently
    val seedA = recent.firstOrNull()
    val seedB = recent.drop(1).firstOrNull { it.artists != seedA?.artists }
    var becauseA by remember(seedA?.id) { mutableStateOf<List<Song>>(emptyList()) }
    var becauseB by remember(seedB?.id) { mutableStateOf<List<Song>>(emptyList()) }
    LaunchedEffect(seedA?.id) {
        seedA?.let { s -> becauseA = runCatching { MusicRepository.radio(s.id) }.getOrDefault(emptyList()).filter { it.id != s.id } }
    }
    LaunchedEffect(seedB?.id) {
        seedB?.let { s -> becauseB = runCatching { MusicRepository.radio(s.id) }.getOrDefault(emptyList()).filter { it.id != s.id } }
    }

    val accent = Palette.tileColorFor(seedA?.id ?: "home")

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.28f), 0.35f to Color.Transparent)),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Column(Modifier.padding(start = 28.dp, end = 28.dp, top = 28.dp)) {
                Text(greeting, style = MaterialTheme.typography.headlineMedium, color = Palette.TextPrimary)
                Spacer(Modifier.height(16.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(MoodSearches) { mood ->
                        PillButton(mood, onClick = { ui.navigate(Screen.Search(mood)) })
                    }
                }
            }
        }

        if (recent.isEmpty()) {
            item { WelcomeHero(Modifier.padding(28.dp)) }
            item {
                SectionTitle("Browse all", modifier = Modifier.padding(horizontal = 28.dp))
                Spacer(Modifier.height(14.dp))
                GenreGrid(Modifier.padding(horizontal = 28.dp))
            }
            return@LazyColumn
        }

        item {
            QuickPicks(recent, Modifier.padding(start = 28.dp, end = 28.dp, top = 20.dp))
        }

        item {
            CardRow(
                title = "Recently played",
                songs = recent.take(20),
                onPlay = { song -> controller.playRadio(song) },
                actionLabel = "Show all",
                onAction = { ui.navigate(Screen.Library) }
            )
        }

        if (seedA != null && becauseA.isNotEmpty()) {
            item {
                CardRow(
                    title = seedA.title,
                    subtitle = "Because you listened to",
                    songs = becauseA,
                    onPlay = { song -> controller.playCollection(becauseA, becauseA.indexOf(song)) }
                )
            }
        }
        if (seedB != null && becauseB.isNotEmpty()) {
            item {
                CardRow(
                    title = "More like ${seedB.artists.substringBefore(",")}",
                    subtitle = "Recommended for you",
                    songs = becauseB,
                    onPlay = { song -> controller.playCollection(becauseB, becauseB.indexOf(song)) }
                )
            }
        }
    }
}

@Composable
private fun WelcomeHero(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3B1E8C), Palette.Violet, Palette.Pink)))
            .padding(32.dp)
    ) {
        Column {
            Text("WELCOME TO VITUNE", style = SectionLabelStyle, color = Color.White.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            Text(
                "Millions of songs.\nPlay anything, instantly.",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Search for a song, artist or album — we'll line up similar tracks automatically.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(20.dp))
            PillButton("Start searching", onClick = { ui.focusSearch() }, icon = AppIcons.Search, filled = true)
        }
    }
}

@Composable
fun GenreGrid(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = when {
            maxWidth > 1000.dp -> 6
            maxWidth > 700.dp -> 4
            else -> 3
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            MoodSearches.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { mood ->
                        val color = Palette.tileColorFor(mood)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1.6f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.55f))))
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { ui.navigate(Screen.Search(mood)) }
                                .padding(14.dp)
                        ) {
                            Text(mood, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.ExtraBold)
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** Compact horizontal tiles (Liked Songs, playlists and recent songs) at the top of Home. */
@Composable
private fun QuickPicks(recent: List<Song>, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val appState = ui.appState
    val controller = ui.controller

    data class Pick(val title: String, val art: @Composable () -> Unit, val isActive: Boolean, val onPlay: () -> Unit, val onOpen: () -> Unit)

    val picks = buildList {
        if (appState.likedSongs.isNotEmpty()) add(
            Pick("Liked Songs", { LikedArtwork(56.dp, corner = 0.dp) }, false,
                onPlay = { controller.playCollection(appState.likedSongs) },
                onOpen = { ui.navigate(Screen.Liked) })
        )
        appState.playlists.filter { it.songs.isNotEmpty() }.take(3).forEach { p ->
            add(Pick(p.name, { PlaylistArtwork(p, 56.dp, corner = 0.dp) }, false,
                onPlay = { controller.playCollection(p.songs) },
                onOpen = { ui.navigate(Screen.Playlist(p.id)) }))
        }
        recent.take(8 - size).forEach { s ->
            add(Pick(s.title, { Artwork(s.thumbnailSmall, 56.dp, corner = 0.dp, placeholderKey = s.id) },
                controller.current?.id == s.id,
                onPlay = { controller.playRadio(s) },
                onOpen = { controller.playRadio(s) }))
        }
    }

    BoxWithConstraints(modifier.fillMaxWidth()) {
        val columns = if (maxWidth > 760.dp) 4 else 2
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            picks.chunked(columns).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { pick ->
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = if (hovered) 0.16f else 0.08f))
                                .hoverable(interaction)
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable(onClick = pick.onOpen)
                        ) {
                            pick.art()
                            Spacer(Modifier.width(12.dp))
                            Text(
                                pick.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (pick.isActive) Palette.Violet else Palette.TextPrimary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            AnimatedVisibility(hovered, enter = fadeIn(), exit = fadeOut()) {
                                GradientPlayButton(false, onClick = pick.onPlay, size = 36.dp, modifier = Modifier.padding(end = 10.dp))
                            }
                        }
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
fun CardRow(
    title: String,
    songs: List<Song>,
    onPlay: (Song) -> Unit,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(Modifier.padding(top = 28.dp)) {
        SectionTitle(
            title = title,
            subtitle = subtitle,
            actionLabel = actionLabel,
            onAction = onAction,
            modifier = Modifier.padding(horizontal = 28.dp)
        )
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                SongCard(song, onPlay = { onPlay(song) })
            }
        }
    }
}

/** Album-style card with a play button that slides in on hover. */
@Composable
fun SongCard(song: Song, onPlay: () -> Unit, modifier: Modifier = Modifier, artSize: Dp = 164.dp) {
    val ui = LocalUi.current
    val controller = ui.controller
    val isCurrent = controller.current?.id == song.id
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    PrefetchOnHover(song, hovered)

    Column(
        modifier = modifier
            .width(artSize + 16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (hovered) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = { if (isCurrent) controller.togglePlayPause() else onPlay() })
            .padding(8.dp)
    ) {
        Box {
            Artwork(song.thumbnailLarge, artSize, corner = 8.dp, placeholderKey = song.id)
            // Play button fades and slides up into place on hover
            val shown by animateFloatAsState(if (hovered || isCurrent) 1f else 0f, label = "cardPlay")
            if (shown > 0f) {
                GradientPlayButton(
                    isPlaying = isCurrent && controller.isPlaying,
                    loading = isCurrent && controller.isLoading,
                    onClick = { if (isCurrent) controller.togglePlayPause() else onPlay() },
                    size = 44.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .graphicsLayer {
                            alpha = shown
                            translationY = (1f - shown) * 16.dp.toPx()
                        }
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) {
                if (hovered || menuOpen) {
                    ToolButton(
                        AppIcons.MoreHoriz,
                        "More options",
                        onClick = { menuOpen = true },
                        tint = Color.White,
                        size = 30.dp,
                        iconSize = 18.dp,
                        modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.45f))
                    )
                }
                SongMenu(song, expanded = menuOpen, onDismiss = { menuOpen = false })
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCurrent) Palette.Violet else Palette.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            song.artists,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.TextSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
