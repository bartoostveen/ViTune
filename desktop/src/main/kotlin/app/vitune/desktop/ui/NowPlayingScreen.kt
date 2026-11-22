package app.vitune.desktop.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/** Full-window "Now Playing" view with blurred album-art backdrop and live lyrics. */
@Composable
fun NowPlayingScreen(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val controller = ui.controller
    val song = controller.current ?: run {
        ui.nowPlayingOpen = false
        return
    }
    val accent = Palette.tileColorFor(song.id)

    Box(
        modifier
            .fillMaxSize()
            .background(Palette.Background)
            // Swallow clicks so they don't reach the screen underneath
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
    ) {
        // Backdrop
        Crossfade(song.thumbnailLarge, label = "backdrop") { url ->
            Box(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(accent.copy(alpha = 0.6f), Palette.Background)))) {
                if (url != null) {
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(90.dp).alpha(0.65f)
                    )
                }
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f), Color.Black.copy(alpha = 0.85f)))
            )
        )

        Column(Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp)) {
            // Top bar
            Row(verticalAlignment = Alignment.CenterVertically) {
                ToolButton(AppIcons.ExpandMore, "Close Now Playing", onClick = { ui.nowPlayingOpen = false }, tint = Color.White, size = 40.dp, iconSize = 26.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("NOW PLAYING", style = SectionLabelStyle, color = Color.White.copy(alpha = 0.7f))
                    val next = controller.upcoming.firstOrNull()
                    if (next != null) {
                        Text(
                            "Up next: ${next.title} · ${next.artists}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val showLyrics = maxWidth > 900.dp
                val artSize = (if (showLyrics) minOf(maxHeight - 230.dp, maxWidth * 0.4f) else minOf(maxHeight - 230.dp, maxWidth * 0.8f))
                    .coerceIn(180.dp, 460.dp)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(56.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.then(if (showLyrics) Modifier.width(artSize + 40.dp) else Modifier.fillMaxWidth())
                    ) {
                        Box(Modifier.shadow(32.dp, RoundedCornerShape(14.dp))) {
                            Artwork(song.thumbnailLarge, artSize, corner = 14.dp, placeholderKey = song.id)
                        }
                        Spacer(Modifier.height(28.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.widthIn(max = artSize + 40.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artists,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            val liked = ui.appState.isLiked(song.id)
                            ToolButton(
                                if (liked) AppIcons.Favorite else AppIcons.FavoriteBorder,
                                if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                                onClick = { ui.toggleLike(song) },
                                tint = if (liked) Palette.Pink else Color.White,
                                showDot = false,
                                size = 44.dp, iconSize = 26.dp
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        SeekBar(controller, Modifier.widthIn(max = artSize + 80.dp))
                        Spacer(Modifier.height(8.dp))
                        TransportControls(controller, playSize = 60.dp)
                    }
                    if (showLyrics) {
                        LyricsView(Modifier.weight(1f).fillMaxHeight(), large = true)
                    }
                }
            }
        }
    }
}
