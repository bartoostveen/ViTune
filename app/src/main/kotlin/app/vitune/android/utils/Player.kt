package app.vitune.android.utils

import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.vitune.android.preferences.AppearancePreferences
import app.vitune.core.ui.utils.songBundle
import kotlin.time.Duration

val Player.currentWindow: Timeline.Window?
    get() = if (mediaItemCount == 0) null else currentTimeline[currentMediaItemIndex]

inline val Timeline.windows: List<Timeline.Window>
    get() = List(windowCount) { this[it] }

inline val Timeline.mediaItems: List<MediaItem>
    get() = windows.map { it.mediaItem }

val Player.shouldBePlaying: Boolean
    get() = !(playbackState == Player.STATE_ENDED || !playWhenReady)

fun Player.removeMediaItems(range: IntRange) = removeMediaItems(range.first, range.last + 1)

fun Player.safeClearQueue() {
    if (currentMediaItemIndex > 0) removeMediaItems(0 until currentMediaItemIndex)
    if (currentMediaItemIndex < mediaItemCount - 1)
        removeMediaItems(currentMediaItemIndex + 1 until mediaItemCount)
}

fun Player.seamlessPlay(mediaItem: MediaItem) =
    if (mediaItem.mediaId == currentMediaItem?.mediaId) safeClearQueue() else forcePlay(mediaItem)

fun Player.shuffleQueue() {
    val mediaItems = currentTimeline
        .mediaItems
        .toMutableList()
        .apply { removeAt(currentMediaItemIndex) }
        .shuffled()

    safeClearQueue()
    addMediaItems(mediaItems)
}

fun Player.forcePlay(mediaItem: MediaItem) {
    setMediaItem(mediaItem, true)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayAtIndex(
    items: List<MediaItem>,
    index: Int
) {
    if (items.isEmpty()) return

    setMediaItems(items, index, C.TIME_UNSET)
    playWhenReady = true
    prepare()
}

fun Player.forcePlayFromBeginning(items: List<MediaItem>) = forcePlayAtIndex(items, 0)

fun Player.forceSeekToPrevious(
    hideExplicit: Boolean = AppearancePreferences.hideExplicit,
    seekToStart: Boolean = true
): Unit = when {
    seekToStart && currentPosition > maxSeekToPreviousPosition -> seekToPrevious()
    hideExplicit -> if (mediaItemCount <= 1) forceSeekToPrevious(hideExplicit = false)
    else {
        var i = currentMediaItemIndex - 1
        while (
            i !in (0 until mediaItemCount) ||
            getMediaItemAt(i).mediaMetadata.extras?.songBundle?.explicit == true
        ) {
            if (i <= 0) i = mediaItemCount - 1 else i--
        }
        seekTo(i, C.TIME_UNSET)
    }
    // fall back to default behavior if there is only a single song

    hasPreviousMediaItem() -> seekToPreviousMediaItem()
    mediaItemCount > 0 -> seekTo(mediaItemCount - 1, C.TIME_UNSET)
    else -> {}
}

fun Player.forceSeekToNext() =
    if (hasNextMediaItem()) seekToNext() else seekTo(0, C.TIME_UNSET)

fun Player.addNext(mediaItem: MediaItem) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlay(mediaItem)
    else -> addMediaItem(currentMediaItemIndex + 1, mediaItem)
}

fun Player.enqueue(mediaItem: MediaItem) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlay(mediaItem)
    else -> addMediaItem(mediaItemCount, mediaItem)
}

fun Player.enqueue(mediaItems: List<MediaItem>) = when (playbackState) {
    Player.STATE_IDLE, Player.STATE_ENDED -> forcePlayFromBeginning(mediaItems)
    else -> addMediaItems(mediaItemCount, mediaItems)
}

fun Player.findNextMediaItemById(mediaId: String): MediaItem? = runCatching {
    for (i in currentMediaItemIndex until mediaItemCount) {
        if (getMediaItemAt(i).mediaId == mediaId) return getMediaItemAt(i)
    }
    return null
}.getOrNull()

fun Player.setPlaybackPitch(pitch: Float) {
    playbackParameters = PlaybackParameters(playbackParameters.speed, pitch)
}

operator fun Timeline.get(
    index: Int,
    window: Timeline.Window = Timeline.Window(),
    positionProjection: Duration = Duration.ZERO
): Timeline.Window = getWindow(index, window, positionProjection.inWholeMicroseconds)
