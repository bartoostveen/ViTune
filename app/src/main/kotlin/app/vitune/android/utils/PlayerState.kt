package app.vitune.android.utils

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.audiofx.AudioEffect
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SnapshotMutationPolicy
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import app.vitune.android.LocalPlayerServiceBinder
import app.vitune.android.R
import app.vitune.android.service.PlayerService
import app.vitune.core.ui.utils.EqualizerIntentBundleAccessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import androidx.compose.ui.res.stringResource

@JvmInline
value class PlayerScope internal constructor(val player: Player)

@Composable
fun Player?.DisposableListener(
    key: Any? = Unit,
    listenerProvider: PlayerScope.() -> Player.Listener
) {
    val currentListenerProvider by rememberUpdatedState(listenerProvider)

    DisposableEffect(key, currentListenerProvider, this) {
        this@DisposableListener?.run {
            val listener = PlayerScope(this).currentListenerProvider()

            addListener(listener)
            listener.onMediaItemTransition(
                /* mediaItem = */ currentMediaItem,
                /* reason = */ Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
            )
            onDispose { removeListener(listener) }
        } ?: onDispose { }
    }
}

@Composable
fun Player?.positionAndDurationState(
    delay: Duration = 500.milliseconds
): Pair<Long, Long> {
    var state by remember {
        mutableStateOf(this?.let { currentPosition to duration } ?: (0L to 1L))
    }

    DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                state = player.currentPosition to state.second
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                state = player.currentPosition to player.duration
            }
        }
    }

    LaunchedEffect(this) {
        while (isActive) {
            delay(delay)
            this@positionAndDurationState?.run {
                state = currentPosition to duration
            }
        }
    }

    return state
}

typealias WindowState = Pair<Timeline.Window?, PlaybackException?>

@Composable
fun windowState(
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
): WindowState {
    var window by remember { mutableStateOf(binder?.player?.currentWindow) }
    var error by remember { mutableStateOf<PlaybackException?>(binder?.player?.playerError) }
    val state by remember {
        derivedStateOf(
            policy = object : SnapshotMutationPolicy<WindowState> {
                override fun equivalent(a: WindowState, b: WindowState) =
                    a.first === b.first && a.second == b.second
            }
        ) {
            window to error
        }
    }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                window = player.currentWindow
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                error = player.playerError
            }

            override fun onPlayerError(playbackException: PlaybackException) {
                error = playbackException
            }
        }
    }

    return state
}

@Composable
fun playingSong(
    binder: PlayerService.Binder? = LocalPlayerServiceBinder.current
): Pair<String?, Boolean> {
    var playing by remember { mutableStateOf(binder?.player?.playWhenReady == true) }
    var id: String? by remember { mutableStateOf(binder?.player?.currentMediaItem?.mediaId) }

    binder?.player.DisposableListener {
        object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                super.onPlayWhenReadyChanged(playWhenReady, reason)

                playing = playWhenReady
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                super.onMediaItemTransition(mediaItem, reason)

                id = mediaItem?.mediaId
            }
        }
    }

    return id to playing
}

@Composable
fun rememberEqualizerLauncher(
    audioSessionId: () -> Int?,
    contentType: Int = AudioEffect.CONTENT_TYPE_MUSIC
): State<() -> Unit> {
    val context = LocalContext.current
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {}
    val errorMsg = stringResource(R.string.no_equalizer_installed)

    return rememberUpdatedState {
        try {
            launcher.launch(
                Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).replaceExtras(
                    EqualizerIntentBundleAccessor.bundle {
                        audioSessionId()?.let { audioSession = it }
                        packageName = context.packageName
                        this.contentType = contentType
                    }
                )
            )
        } catch (_: ActivityNotFoundException) {
            context.toast(errorMsg)
        }
    }
}
