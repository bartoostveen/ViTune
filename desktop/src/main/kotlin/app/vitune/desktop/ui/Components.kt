@file:OptIn(ExperimentalComposeUiApi::class)

package app.vitune.desktop.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.vitune.desktop.model.Playlist
import app.vitune.desktop.model.Song
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

// ---------- Artwork ----------

@Composable
fun Artwork(
    url: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    corner: Dp = 6.dp,
    placeholderKey: String = url.orEmpty()
) {
    val base = Palette.tileColorFor(placeholderKey)
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(base.copy(alpha = 0.55f), Palette.PanelRaised))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = AppIcons.MusicNote,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.55f),
            modifier = Modifier.size(size * 0.42f)
        )
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** A 2×2 mosaic of the first songs' artwork, like most music apps use for playlists. */
@Composable
fun PlaylistArtwork(playlist: Playlist, size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    val thumbs = playlist.songs.mapNotNull { it.thumbnailLarge }.distinct()
    if (playlist.thumbnailUrl != null) {
        // Imported playlists keep their original cover
        Artwork(playlist.thumbnailUrl, size, modifier, corner = corner, placeholderKey = playlist.id)
    } else if (thumbs.size < 4) {
        val color = Palette.tileColorFor(playlist.id)
        Box(
            modifier = modifier
                .size(size)
                .clip(RoundedCornerShape(corner))
                .background(Brush.linearGradient(listOf(color, color.copy(alpha = 0.35f), Palette.PanelRaised))),
            contentAlignment = Alignment.Center
        ) {
            if (thumbs.isNotEmpty()) {
                AsyncImage(
                    model = thumbs.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    AppIcons.QueueMusic,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(size * 0.4f)
                )
            }
        }
    } else {
        Column(modifier.size(size).clip(RoundedCornerShape(corner))) {
            for (row in 0..1) {
                Row(Modifier.weight(1f)) {
                    for (col in 0..1) {
                        AsyncImage(
                            model = thumbs[row * 2 + col],
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.weight(1f).fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

/** Liked Songs cover: a gradient tile with a heart. */
@Composable
fun LikedArtwork(size: Dp, modifier: Modifier = Modifier, corner: Dp = 8.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF4F2BD9), Palette.Violet, Color(0xFFB79CFF)))),
        contentAlignment = Alignment.Center
    ) {
        Icon(AppIcons.Favorite, null, tint = Color.White, modifier = Modifier.size(size * 0.42f))
    }
}

// ---------- Small building blocks ----------

/** Three bouncing bars shown next to the song that is currently playing. */
@Composable
fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Palette.Violet) {
    val transition = rememberInfiniteTransition(label = "eq")
    val bars = listOf(520, 380, 640).map { duration ->
        transition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(duration, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "bar"
        )
    }
    Canvas(modifier.size(16.dp)) {
        val barWidth = size.width / 5
        bars.forEachIndexed { i, anim ->
            val fraction = if (playing) anim.value else 0.3f
            val h = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(barWidth * (i * 2), size.height - h),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(barWidth / 2)
            )
        }
    }
}

/** The big round accent-coloured play/pause button. */
@Composable
fun GradientPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    loading: Boolean = false,
    enabled: Boolean = true,
    brush: Brush = Palette.AccentGradient,
    iconTint: Color = Color.White
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered && enabled) 1.06f else 1f, label = "playScale")
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .scale(scale)
            .clip(CircleShape)
            .background(if (enabled) brush else SolidColor(Palette.PanelHover))
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        if (loading) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = iconTint, modifier = Modifier.size(size * 0.42f))
        } else {
            Icon(
                imageVector = if (isPlaying) AppIcons.Pause else AppIcons.Play,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = if (enabled) iconTint else Palette.TextMuted,
                modifier = Modifier.size(size * 0.5f)
            )
        }
    }
}

/** A compact icon button with a hover highlight and an optional "active" accent dot. */
@Composable
fun ToolButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    enabled: Boolean = true,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    tint: Color? = null,
    showDot: Boolean = active
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val color = when {
        tint != null -> tint
        !enabled -> Palette.TextMuted.copy(alpha = 0.5f)
        active -> Palette.Violet
        hovered -> Palette.TextPrimary
        else -> Palette.TextSecondary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(if (hovered && enabled) Color.White.copy(alpha = 0.07f) else Color.Transparent)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Icon(icon, contentDescription, tint = color, modifier = Modifier.size(iconSize))
        if (showDot) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 2.dp)
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(Palette.Violet)
            )
        }
    }
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
    filled: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background = when {
        filled -> SolidColor(Palette.TextPrimary)
        selected -> Palette.AccentGradient
        hovered -> SolidColor(Palette.PanelHover)
        else -> SolidColor(Palette.PanelRaised)
    }
    val contentColor = if (filled) Palette.Background else Palette.TextPrimary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(background)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (icon != null) {
            Icon(icon, null, tint = contentColor, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = contentColor,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

/**
 * A slim slider that thickens and shows a knob on hover, like modern players.
 * [value] is 0..1.
 */
@Composable
fun SlimSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: () -> Unit = {},
    enabled: Boolean = true,
    activeBrush: Brush = Palette.AccentGradient,
    alwaysShowKnob: Boolean = false
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var dragging by remember { mutableStateOf(false) }
    val emphasised = enabled && (hovered || dragging)
    val trackHeight by animateFloatAsState(if (emphasised) 6f else 4f, label = "track")
    val onChange by rememberUpdatedState(onValueChange)
    val onFinished by rememberUpdatedState(onValueChangeFinished)

    BoxWithConstraints(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .height(20.dp)
            .hoverable(interaction)
            .pointerHoverIcon(if (enabled) PointerIcon.Hand else PointerIcon.Default)
            .then(
                if (!enabled) Modifier else Modifier
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            onChange((offset.x / size.width).coerceIn(0f, 1f))
                            onFinished()
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                dragging = true
                                onChange((offset.x / size.width).coerceIn(0f, 1f))
                            },
                            onDragEnd = { dragging = false; onFinished() },
                            onDragCancel = { dragging = false; onFinished() }
                        ) { change, _ ->
                            change.consume()
                            onChange((change.position.x / size.width).coerceIn(0f, 1f))
                        }
                    }
            )
    ) {
        val fraction = value.coerceIn(0f, 1f)
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val h = trackHeight.dp.toPx()
            val top = (size.height - h) / 2
            drawRoundRect(
                color = Color.White.copy(alpha = 0.14f),
                topLeft = Offset(0f, top),
                size = Size(size.width, h),
                cornerRadius = CornerRadius(h / 2)
            )
            if (fraction > 0f) {
                drawRoundRect(
                    brush = if (enabled) activeBrush else SolidColor(Palette.TextMuted),
                    topLeft = Offset(0f, top),
                    size = Size(size.width * fraction, h),
                    cornerRadius = CornerRadius(h / 2)
                )
            }
            if (emphasised || (alwaysShowKnob && enabled)) {
                drawCircle(
                    color = Color.White,
                    radius = 6.5.dp.toPx(),
                    center = Offset(size.width * fraction, size.height / 2)
                )
            }
        }
    }
}

@Composable
fun SectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(verticalAlignment = Alignment.Bottom, modifier = modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle.uppercase(), style = SectionLabelStyle, color = Palette.TextMuted)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.titleLarge, color = Palette.TextPrimary)
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = MaterialTheme.typography.labelLarge,
                color = Palette.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.fillMaxWidth().padding(32.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(Palette.PanelRaised)
        ) {
            Icon(icon, null, tint = Palette.Violet, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = Palette.TextPrimary)
        Spacer(Modifier.height(6.dp))
        Text(
            message,
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}

// ---------- Collection header (playlist / liked songs) ----------

@Composable
fun CollectionHeader(
    label: String,
    title: String,
    subtitle: String,
    accent: Color,
    artwork: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    actions: @Composable RowScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.55f), accent.copy(alpha = 0.12f), Color.Transparent)))
            .padding(start = 32.dp, end = 32.dp, top = 40.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
            ) { artwork() }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(1f)) {
                Text(label.uppercase(), style = SectionLabelStyle, color = Color.White.copy(alpha = 0.85f))
                Spacer(Modifier.height(8.dp))
                Text(
                    title,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!description.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
            }
        }
        Spacer(Modifier.height(24.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = actions
        )
    }
}

// ---------- Track rows & song menu ----------

/** Resolves a song's stream once the pointer rests on it, so clicking it starts almost instantly. */
@Composable
fun PrefetchOnHover(song: Song, hovered: Boolean) {
    val controller = LocalUi.current.controller
    LaunchedEffect(hovered, song.id) {
        if (hovered) {
            delay(300)
            controller.prefetch(song)
        }
    }
}

/** Column headings for a track list. */
@Composable
fun TrackListHeader(modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("#", style = SectionLabelStyle, color = Palette.TextMuted, modifier = Modifier.width(36.dp))
            Text("TITLE", style = SectionLabelStyle, color = Palette.TextMuted, modifier = Modifier.weight(1f))
            Icon(AppIcons.Timer, null, tint = Palette.TextMuted, modifier = Modifier.padding(end = 52.dp).size(16.dp))
        }
        HorizontalDivider(color = Palette.Outline)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * One song in a list: index (or play icon on hover, or animated bars while playing),
 * artwork, title/artist, like button, duration and a "more" menu. Right-click opens the menu too.
 */
@Composable
fun TrackRow(
    song: Song,
    index: Int?,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
    playlistId: String? = null,
    compact: Boolean = false,
    extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    val ui = LocalUi.current
    val controller = ui.controller
    val isCurrent = controller.current?.id == song.id
    val liked = ui.appState.isLiked(song.id)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuOpen by remember { mutableStateOf(false) }
    PrefetchOnHover(song, hovered)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    menuOpen || hovered -> Color.White.copy(alpha = 0.06f)
                    isCurrent -> Palette.Violet.copy(alpha = 0.10f)
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.buttons.isSecondaryPressed) menuOpen = true
            }
            .clickable(onClick = { if (isCurrent) controller.togglePlayPause() else onPlay() })
            .padding(horizontal = if (compact) 8.dp else 12.dp, vertical = if (compact) 5.dp else 6.dp)
    ) {
        if (index != null) {
            Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) {
                when {
                    hovered -> Icon(
                        if (isCurrent && controller.isPlaying) AppIcons.Pause else AppIcons.Play,
                        null,
                        tint = Palette.TextPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    isCurrent -> EqualizerBars(controller.isPlaying)
                    else -> Text("${index + 1}", style = MaterialTheme.typography.bodyMedium, color = Palette.TextSecondary)
                }
            }
        }
        Box {
            Artwork(song.thumbnailSmall, if (compact) 40.dp else 44.dp, placeholderKey = song.id)
            if (index == null && (hovered || isCurrent)) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (compact) 40.dp else 44.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                ) {
                    if (hovered) {
                        Icon(
                            if (isCurrent && controller.isPlaying) AppIcons.Pause else AppIcons.Play,
                            null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    } else {
                        EqualizerBars(controller.isPlaying, color = Color.White)
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) Palette.Violet else Palette.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artists.isNotEmpty()) {
                Text(
                    text = song.artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (!compact || hovered || liked) {
            ToolButton(
                icon = if (liked) AppIcons.Favorite else AppIcons.FavoriteBorder,
                contentDescription = if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                onClick = { ui.toggleLike(song) },
                tint = if (liked) Palette.Pink else if (hovered) Palette.TextSecondary else Color.Transparent,
                showDot = false,
                size = 32.dp,
                iconSize = 18.dp
            )
        }
        if (!compact) {
            Text(
                text = song.durationText ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextSecondary,
                modifier = Modifier.width(52.dp).padding(start = 8.dp)
            )
        }
        Box {
            ToolButton(
                icon = AppIcons.MoreHoriz,
                contentDescription = "More options",
                onClick = { menuOpen = true },
                tint = if (hovered || menuOpen) Palette.TextPrimary else Color.Transparent,
                size = 32.dp,
                iconSize = 18.dp
            )
            SongMenu(
                song = song,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                playlistId = playlistId,
                extra = extraMenu
            )
        }
    }
}

@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    playlistId: String? = null,
    extra: (@Composable (dismiss: () -> Unit) -> Unit)? = null
) {
    val ui = LocalUi.current
    var showPlaylists by remember(expanded) { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = Palette.PanelRaised
    ) {
        if (!showPlaylists) {
            MenuItem("Play", AppIcons.Play) { onDismiss(); ui.controller.playRadio(song) }
            MenuItem("Play next", AppIcons.QueueMusic) {
                onDismiss(); ui.controller.playNext(song); ui.toast("Playing next: ${song.title}")
            }
            MenuItem("Add to queue", AppIcons.Add) {
                onDismiss(); ui.controller.addToQueue(song); ui.toast("Added to queue")
            }
            MenuItem("Start song radio", AppIcons.Radio) { onDismiss(); ui.controller.playRadio(song) }
            HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 4.dp))
            val liked = ui.appState.isLiked(song.id)
            MenuItem(
                if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                if (liked) AppIcons.Favorite else AppIcons.FavoriteBorder
            ) { onDismiss(); ui.toggleLike(song) }
            MenuItem("Add to playlist…", AppIcons.Library) { showPlaylists = true }
            if (playlistId != null) {
                MenuItem("Remove from this playlist", AppIcons.Delete) {
                    onDismiss()
                    ui.appState.removeFromPlaylist(playlistId, song.id)
                    ui.toast("Removed from playlist")
                }
            }
            extra?.invoke(onDismiss)
            HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 4.dp))
            MenuItem("Copy song link", AppIcons.Link) {
                onDismiss()
                copyToClipboard(songUrl(song))
                ui.toast("Link copied to clipboard")
            }
            MenuItem("Open in YouTube Music", AppIcons.OpenInNew) {
                onDismiss()
                runCatching { Desktop.getDesktop().browse(URI(songUrl(song))) }
            }
        } else {
            MenuItem("Back", AppIcons.ArrowBack) { showPlaylists = false }
            HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 4.dp))
            MenuItem("New playlist", AppIcons.Add) {
                onDismiss()
                ui.requestNewPlaylist(song)
            }
            ui.appState.playlists.forEach { playlist ->
                val contains = playlist.songs.any { it.id == song.id }
                MenuItem(playlist.name, if (contains) AppIcons.Check else AppIcons.QueueMusic) {
                    onDismiss()
                    if (ui.appState.addToPlaylist(playlist.id, song)) ui.toast("Added to ${playlist.name}")
                    else ui.toast("Already in ${playlist.name}")
                }
            }
        }
    }
}

@Composable
fun MenuItem(text: String, icon: ImageVector? = null, enabled: Boolean = true, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        onClick = onClick,
        enabled = enabled,
        leadingIcon = icon?.let { { Icon(it, null, modifier = Modifier.size(18.dp)) } },
        modifier = Modifier.height(40.dp)
    )
}

fun songUrl(song: Song) = "https://music.youtube.com/watch?v=${song.id}"

fun copyToClipboard(text: String) {
    runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
}
