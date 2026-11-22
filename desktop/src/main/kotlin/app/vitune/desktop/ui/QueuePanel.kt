package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Right-hand panel: the play queue or the current song's lyrics. */
@Composable
fun SidePanel(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Panel)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)
        ) {
            PillButton("Queue", onClick = { ui.sidePanel = SidePanel.QUEUE }, selected = ui.sidePanel == SidePanel.QUEUE)
            PillButton("Lyrics", onClick = { ui.sidePanel = SidePanel.LYRICS }, selected = ui.sidePanel == SidePanel.LYRICS)
            Spacer(Modifier.weight(1f))
            ToolButton(AppIcons.Close, "Close panel", onClick = {
                ui.appState.updateSettings { it.copy(showSidePanel = false) }
            }, size = 32.dp, iconSize = 18.dp)
        }
        when (ui.sidePanel) {
            SidePanel.QUEUE -> QueueContent(Modifier.weight(1f))
            SidePanel.LYRICS -> LyricsView(Modifier.weight(1f).fillMaxWidth(), large = false)
        }
    }
}

@Composable
private fun QueueContent(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val controller = ui.controller
    val current = controller.current

    if (current == null) {
        Column(modifier.fillMaxSize()) {
            EmptyState(
                AppIcons.QueueMusic,
                "Your queue is empty",
                "Play a song and we'll line up similar tracks here."
            )
        }
        return
    }

    val upcoming = controller.upcoming
    val firstUpcomingIndex = controller.currentIndex + 1

    Column(modifier) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 12.dp)) {
            item {
                Text("NOW PLAYING", style = SectionLabelStyle, color = Palette.TextMuted, modifier = Modifier.padding(8.dp))
                TrackRow(current, index = null, onPlay = {}, compact = true)
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("NEXT UP", style = SectionLabelStyle, color = Palette.TextMuted)
                        val total = upcoming.sumOf { it.durationMs }
                        Text(
                            "${upcoming.size} songs" + if (total > 0) " • ${formatTotalDuration(total)}" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextSecondary
                        )
                    }
                    if (upcoming.isNotEmpty()) {
                        PillButton("Clear", onClick = { controller.clearUpcoming(); ui.toast("Queue cleared") })
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            items(upcoming.withIndex().toList(), key = { it.value.id }) { (offset, song) ->
                val index = firstUpcomingIndex + offset
                TrackRow(
                    song = song,
                    index = null,
                    compact = true,
                    onPlay = { controller.playAt(index) },
                    extraMenu = { dismiss ->
                        HorizontalDivider(color = Palette.Outline, modifier = Modifier.padding(vertical = 4.dp))
                        MenuItem("Move up", AppIcons.ExpandLess, enabled = index > firstUpcomingIndex) {
                            dismiss(); controller.moveInQueue(index, index - 1)
                        }
                        MenuItem("Move down", AppIcons.ExpandMore, enabled = index < controller.queue.lastIndex) {
                            dismiss(); controller.moveInQueue(index, index + 1)
                        }
                        MenuItem("Remove from queue", AppIcons.Close) {
                            dismiss(); controller.removeFromQueue(index)
                        }
                    }
                )
            }
        }
        HorizontalDivider(color = Palette.Outline)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Column(Modifier.weight(1f)) {
                Text("Autoplay", style = MaterialTheme.typography.bodyMedium, color = Palette.TextPrimary)
                Text(
                    "Keep playing similar songs when the queue ends",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary
                )
            }
            Switch(
                checked = controller.autoplay,
                onCheckedChange = { on ->
                    controller.autoplay = on
                    ui.appState.updateSettings { it.copy(autoplay = on) }
                },
                colors = SwitchDefaults.colors(
                    checkedTrackColor = Palette.Violet,
                    checkedThumbColor = Color.White,
                    uncheckedTrackColor = Palette.PanelHover,
                    uncheckedBorderColor = Palette.Outline
                )
            )
        }
    }
}
