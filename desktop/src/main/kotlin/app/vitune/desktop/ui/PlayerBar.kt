@file:OptIn(ExperimentalComposeUiApi::class)

package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.desktop.player.PlayerController
import app.vitune.desktop.player.RepeatMode
import app.vitune.desktop.player.SleepTimer
import kotlinx.coroutines.delay

@Composable
fun PlayerBar(modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val controller = ui.controller
    val song = controller.current

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .height(88.dp)
            .padding(horizontal = 16.dp)
    ) {
        // Now playing
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            val interaction = remember { MutableInteractionSource() }
            val hovered by interaction.collectIsHoveredAsState()
            Box(
                Modifier
                    .hoverable(interaction)
                    .pointerHoverIcon(if (song != null) PointerIcon.Hand else PointerIcon.Default)
                    .clickable(enabled = song != null) { ui.nowPlayingOpen = true }
            ) {
                Artwork(song?.thumbnailLarge, 58.dp, corner = 8.dp, placeholderKey = song?.id.orEmpty())
                if (hovered && song != null) {
                    Box(
                        Modifier
                            .size(58.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(AppIcons.ExpandLess, "Open Now Playing", tint = Color.White, modifier = Modifier.size(28.dp))
                    }
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f, fill = false)) {
                Text(
                    text = song?.title ?: "Nothing playing",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Palette.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .pointerHoverIcon(if (song != null) PointerIcon.Hand else PointerIcon.Default)
                        .clickable(enabled = song != null) { ui.nowPlayingOpen = true }
                )
                Text(
                    text = song?.artists?.ifEmpty { null } ?: "Pick something to play",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (song != null) {
                Spacer(Modifier.width(8.dp))
                val liked = ui.appState.isLiked(song.id)
                ToolButton(
                    if (liked) AppIcons.Favorite else AppIcons.FavoriteBorder,
                    if (liked) "Remove from Liked Songs" else "Save to Liked Songs",
                    onClick = { ui.toggleLike(song) },
                    tint = if (liked) Palette.Pink else null,
                    showDot = false,
                    size = 34.dp, iconSize = 18.dp
                )
            }
        }

        // Transport and seek bar
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.weight(1.3f).widthIn(max = 720.dp)
        ) {
            TransportControls(controller, playSize = 40.dp)
            Spacer(Modifier.height(4.dp))
            SeekBar(controller)
        }

        // Extras and volume
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.weight(1f)
        ) {
            val panelVisible = ui.appState.settings.showSidePanel
            ToolButton(
                AppIcons.Mic, "Lyrics",
                onClick = { ui.toggleSidePanel(SidePanel.LYRICS) },
                active = panelVisible && ui.sidePanel == SidePanel.LYRICS,
                size = 34.dp, iconSize = 18.dp
            )
            ToolButton(
                AppIcons.QueueMusic, "Queue",
                onClick = { ui.toggleSidePanel(SidePanel.QUEUE) },
                active = panelVisible && ui.sidePanel == SidePanel.QUEUE,
                size = 34.dp, iconSize = 18.dp
            )
            SleepTimerButton(controller)
            SpeedButton(controller)
            EqualizerButton(controller)
            Spacer(Modifier.width(4.dp))
            VolumeControl(controller)
            ToolButton(
                AppIcons.Fullscreen, "Now Playing view",
                onClick = { ui.nowPlayingOpen = !ui.nowPlayingOpen },
                enabled = song != null,
                active = ui.nowPlayingOpen,
                size = 34.dp, iconSize = 18.dp
            )
        }
    }
}

@Composable
fun TransportControls(controller: PlayerController, playSize: androidx.compose.ui.unit.Dp, light: Boolean = false) {
    val song = controller.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolButton(
            AppIcons.Shuffle, if (controller.isShuffled) "Shuffle: on" else "Shuffle: off",
            onClick = controller::toggleShuffle,
            active = controller.isShuffled,
            enabled = song != null,
            size = playSize * 0.9f, iconSize = playSize * 0.48f
        )
        ToolButton(
            AppIcons.SkipPrevious, "Previous",
            onClick = controller::previous,
            enabled = song != null,
            tint = if (song != null) Palette.TextPrimary else null,
            showDot = false,
            size = playSize * 0.9f, iconSize = playSize * 0.65f
        )
        GradientPlayButton(
            isPlaying = controller.isPlaying,
            loading = controller.isLoading,
            enabled = song != null,
            onClick = controller::togglePlayPause,
            size = playSize,
            brush = SolidColor(Color.White),
            iconTint = Color.Black
        )
        ToolButton(
            AppIcons.SkipNext, "Next",
            onClick = controller::next,
            enabled = controller.hasNext,
            tint = if (controller.hasNext) Palette.TextPrimary else null,
            showDot = false,
            size = playSize * 0.9f, iconSize = playSize * 0.65f
        )
        ToolButton(
            if (controller.repeatMode == RepeatMode.ONE) AppIcons.RepeatOne else AppIcons.Repeat,
            when (controller.repeatMode) {
                RepeatMode.OFF -> "Repeat: off"
                RepeatMode.ALL -> "Repeat: all"
                RepeatMode.ONE -> "Repeat: one"
            },
            onClick = controller::cycleRepeatMode,
            active = controller.repeatMode != RepeatMode.OFF,
            enabled = song != null,
            size = playSize * 0.9f, iconSize = playSize * 0.48f
        )
    }
}

@Composable
fun SeekBar(controller: PlayerController, modifier: Modifier = Modifier) {
    // While dragging, show the drag position instead of the live position
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    val duration = controller.durationMs
    val liveFraction = if (duration > 0) controller.positionMs.toFloat() / duration else 0f
    val fraction = dragFraction ?: liveFraction
    val shownPosition = if (dragFraction != null) (dragFraction!! * duration).toLong() else controller.positionMs

    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        TimeLabel(formatTime(shownPosition), TextAlign.End)
        SlimSlider(
            value = fraction,
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                dragFraction?.let { controller.seekTo((it * duration).toLong()) }
                dragFraction = null
            },
            enabled = duration > 0 && !controller.isLoading,
            modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
        )
        TimeLabel(if (duration > 0) formatTime(duration) else "-:--", TextAlign.Start)
    }
}

@Composable
private fun TimeLabel(text: String, align: TextAlign) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing),
        color = Palette.TextSecondary,
        textAlign = align,
        modifier = Modifier.width(44.dp)
    )
}

@Composable
private fun VolumeControl(controller: PlayerController) {
    val ui = LocalUi.current
    val effective = if (controller.isMuted) 0 else controller.volume
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.onPointerEvent(PointerEventType.Scroll) { event ->
            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
            if (delta != 0f) controller.changeVolume(controller.volume - (delta * 5).toInt())
        }
    ) {
        ToolButton(
            when {
                effective == 0 -> AppIcons.VolumeOff
                effective < 50 -> AppIcons.VolumeDown
                else -> AppIcons.VolumeUp
            },
            if (controller.isMuted) "Unmute" else "Mute",
            onClick = controller::toggleMute,
            showDot = false,
            size = 34.dp, iconSize = 18.dp
        )
        SlimSlider(
            value = effective / 100f,
            onValueChange = { controller.changeVolume((it * 100).toInt()) },
            onValueChangeFinished = { ui.appState.updateSettings { s -> s.copy(volume = controller.volume) } },
            activeBrush = SolidColor(Color.White),
            modifier = Modifier.width(96.dp)
        )
    }
}

@Composable
private fun SleepTimerButton(controller: PlayerController) {
    var open by remember { mutableStateOf(false) }
    val timer = controller.sleepTimer
    val remainingMin by produceState<Long?>(null, timer) {
        while (timer is SleepTimer.At) {
            value = ((timer.endsAtMs - System.currentTimeMillis()) / 60_000 + 1).coerceAtLeast(1)
            delay(5_000)
        }
        value = null
    }
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(AppIcons.Timer, "Sleep timer", onClick = { open = true }, active = timer != null, size = 34.dp, iconSize = 18.dp)
            if (remainingMin != null) {
                Text("${remainingMin}m", style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.PanelRaised) {
            MenuHeader("Sleep timer")
            listOf(5, 15, 30, 45, 60, 90).forEach { minutes ->
                MenuItem(if (minutes < 60) "$minutes minutes" else "${minutes / 60}${if (minutes % 60 == 30) ".5" else ""} hour${if (minutes > 60) "s" else ""}") {
                    open = false; controller.setSleepTimer(minutes)
                }
            }
            MenuItem("End of track", AppIcons.MusicNote) { open = false; controller.sleepAtEndOfTrack() }
            if (timer != null) {
                HorizontalDivider(color = Palette.Outline)
                MenuItem("Turn off timer", AppIcons.Close) { open = false; controller.cancelSleepTimer() }
            }
        }
    }
}

@Composable
private fun SpeedButton(controller: PlayerController) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    val speed = controller.playbackSpeed
    Box {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ToolButton(AppIcons.Speed, "Playback speed", onClick = { open = true }, active = speed != 1f, size = 34.dp, iconSize = 18.dp)
            if (speed != 1f) Text(formatSpeed(speed), style = MaterialTheme.typography.labelSmall, color = Palette.Violet)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.PanelRaised) {
            MenuHeader("Playback speed")
            listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                MenuItem(if (s == 1f) "Normal" else formatSpeed(s), if (s == speed) AppIcons.Check else null) {
                    open = false
                    controller.changePlaybackSpeed(s)
                    ui.appState.updateSettings { it.copy(playbackSpeed = s) }
                }
            }
        }
    }
}

private fun formatSpeed(s: Float) = (if (s % 1f == 0f) "${s.toInt()}" else "$s") + "×"

@Composable
private fun EqualizerButton(controller: PlayerController) {
    val ui = LocalUi.current
    var open by remember { mutableStateOf(false) }
    val presets = controller.equalizerPresets
    if (presets.isEmpty()) return
    Box {
        ToolButton(AppIcons.Tune, "Equalizer", onClick = { open = true }, active = controller.equalizerPreset != null, size = 34.dp, iconSize = 18.dp)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = Palette.PanelRaised) {
            MenuHeader("Equalizer")
            MenuItem("Off", if (controller.equalizerPreset == null) AppIcons.Check else null) {
                open = false
                controller.changeEqualizerPreset(null)
                ui.appState.updateSettings { it.copy(equalizerPreset = null) }
            }
            presets.forEach { preset ->
                MenuItem(preset, if (controller.equalizerPreset == preset) AppIcons.Check else null) {
                    open = false
                    controller.changeEqualizerPreset(preset)
                    ui.appState.updateSettings { it.copy(equalizerPreset = preset) }
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(
        text.uppercase(),
        style = SectionLabelStyle,
        color = Palette.TextMuted,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}
