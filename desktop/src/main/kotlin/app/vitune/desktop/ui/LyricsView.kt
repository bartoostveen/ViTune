package app.vitune.desktop.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vitune.desktop.model.MusicRepository
import app.vitune.desktop.model.SongLyrics

/**
 * Lyrics for the current song. Synced lyrics highlight and follow the current line,
 * and clicking a line seeks to it.
 */
@Composable
fun LyricsView(modifier: Modifier = Modifier, large: Boolean) {
    val controller = LocalUi.current.controller
    val song = controller.current

    if (song == null) {
        Box(modifier) { EmptyState(AppIcons.Mic, "No song playing", "Play something to see its lyrics.") }
        return
    }

    var lyrics by remember(song.id) { mutableStateOf<SongLyrics?>(null) }
    LaunchedEffect(song.id) {
        lyrics = runCatching { MusicRepository.lyrics(song, song.durationMs.takeIf { it > 0 } ?: controller.durationMs) }
            .getOrDefault(SongLyrics.NotFound)
    }

    val lineStyle = if (large) TextStyle(fontSize = 30.sp, lineHeight = 40.sp, fontWeight = FontWeight.ExtraBold)
    else TextStyle(fontSize = 19.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold)

    when (val l = lyrics) {
        null -> Box(modifier, contentAlignment = Alignment.Center) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = Palette.Violet, modifier = Modifier.size(28.dp))
        }

        SongLyrics.NotFound -> Box(modifier) {
            EmptyState(AppIcons.Mic, "No lyrics found", "We couldn't find lyrics for this song.")
        }

        is SongLyrics.Plain -> Column(
            modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (large) 8.dp else 20.dp, vertical = 12.dp)
        ) {
            Text(l.text, style = lineStyle.copy(fontSize = lineStyle.fontSize * 0.8f), color = Palette.TextPrimary.copy(alpha = 0.9f))
        }

        is SongLyrics.Synced -> SyncedLyrics(l, lineStyle, large, modifier)
    }
}

@Composable
private fun SyncedLyrics(lyrics: SongLyrics.Synced, style: TextStyle, large: Boolean, modifier: Modifier) {
    val controller = LocalUi.current.controller
    val listState = rememberLazyListState()
    // Show a line slightly before it is sung, like most players do
    val position = controller.positionMs + 250
    val activeIndex = lyrics.lines.indexOfLast { it.timeMs <= position }.coerceAtLeast(0)

    LaunchedEffect(activeIndex) {
        val viewport = listState.layoutInfo.viewportSize.height
        listState.animateScrollToItem(activeIndex, scrollOffset = -(viewport / 3))
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = if (large) 0.dp else 12.dp, vertical = if (large) 120.dp else 60.dp)
    ) {
        itemsIndexed(lyrics.lines) { index, line ->
            val isActive = index == activeIndex
            val color by animateColorAsState(
                when {
                    isActive -> Color.White
                    index < activeIndex -> Color.White.copy(alpha = 0.45f)
                    else -> Color.White.copy(alpha = 0.28f)
                },
                label = "lyricColor"
            )
            val scale by animateFloatAsState(if (isActive) 1f else 0.96f, label = "lyricScale")
            Text(
                text = line.text.ifBlank { "♪" },
                style = style,
                color = color,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable { controller.seekTo(line.timeMs) }
                    .padding(horizontal = 8.dp, vertical = if (large) 10.dp else 7.dp)
            )
        }
    }
}
