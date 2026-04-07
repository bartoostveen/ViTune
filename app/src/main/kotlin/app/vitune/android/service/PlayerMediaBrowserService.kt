package app.vitune.android.service

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ServiceConnection
import android.media.session.MediaSession
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.service.media.MediaBrowserService
import androidx.annotation.DrawableRes
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import app.vitune.android.Database
import app.vitune.android.R
import app.vitune.android.models.Album
import app.vitune.android.models.PlaylistPreview
import app.vitune.android.models.Song
import app.vitune.android.models.SongWithContentLength
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.preferences.OrderPreferences
import app.vitune.android.utils.asMediaItem
import app.vitune.android.utils.forcePlayAtIndex
import app.vitune.android.utils.forceSeekToNext
import app.vitune.android.utils.forceSeekToPrevious
import app.vitune.android.utils.intent
import app.vitune.core.data.utils.CallValidator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.util.stream.IntStream
import android.media.MediaDescription as BrowserMediaDescription
import android.media.browse.MediaBrowser.MediaItem as BrowserMediaItem

class PlayerMediaBrowserService : MediaBrowserService(), ServiceConnection {
    private val coroutineScope = CoroutineScope(Dispatchers.IO)
    private var lastSongs = emptyList<Song>()

    private var bound = false

    private val callValidator by lazy {
        CallValidator(applicationContext, R.xml.allowed_media_browser_callers)
    }

    override fun onDestroy() {
        if (bound) unbindService(this)
        super.onDestroy()
    }

    override fun onServiceConnected(className: ComponentName, service: IBinder) {
        if (service !is PlayerService.Binder) return
        bound = true
        sessionToken = service.mediaSession.sessionToken
        service.mediaSession.setCallback(SessionCallback(service))
    }

    override fun onServiceDisconnected(name: ComponentName) = Unit

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ) = if (callValidator.canCall(clientPackageName, clientUid)) {
        bindService(intent<PlayerService>(), this, BIND_AUTO_CREATE)
        BrowserRoot(
            MediaId.ROOT.id,
            Bundle().apply { putInt("android.media.browse.CONTENT_STYLE_BROWSABLE_HINT", 1) }
        )
    } else null

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<BrowserMediaItem>>
    ) = runBlocking(Dispatchers.IO) {
        result.sendResult(
            when (MediaId(parentId)) {
                MediaId.ROOT -> mutableListOf(
                    songsBrowserMediaItem,
                    playlistsBrowserMediaItem,
                    albumsBrowserMediaItem
                )

                MediaId.SONGS ->
                    Database
                        .songsByPlayTimeDesc(limit = 30)
                        .first()
                        .also { lastSongs = it }
                        .map { it.asBrowserMediaItem }
                        .toMutableList()
                        .apply {
                            if (isNotEmpty()) add(0, shuffleBrowserMediaItem)
                        }

                MediaId.PLAYLISTS ->
                    Database
                        .playlistPreviewsByDateAddedDesc()
                        .first()
                        .map { it.asBrowserMediaItem }
                        .toMutableList()
                        .apply {
                            add(0, favoritesBrowserMediaItem)
                            add(1, offlineBrowserMediaItem)
                            add(2, topBrowserMediaItem)
                            add(3, localBrowserMediaItem)
                        }

                MediaId.ALBUMS ->
                    Database
                        .albumsByRowIdDesc()
                        .first()
                        .map { it.asBrowserMediaItem }
                        .toMutableList()

                else -> mutableListOf()
            }
        )
    }

    private fun uriFor(@DrawableRes id: Int) = Uri.Builder()
        .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
        .authority(resources.getResourcePackageName(id))
        .appendPath(resources.getResourceTypeName(id))
        .appendPath(resources.getResourceEntryName(id))
        .build()

    private val shuffleBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.SHUFFLE.id)
                .setTitle(getString(R.string.shuffle))
                .setIconUri(uriFor(R.drawable.shuffle))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val songsBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.SONGS.id)
                .setTitle(getString(R.string.songs))
                .setIconUri(uriFor(R.drawable.musical_notes))
                .build(),
            BrowserMediaItem.FLAG_BROWSABLE
        )

    private val playlistsBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.PLAYLISTS.id)
                .setTitle(getString(R.string.playlists))
                .setIconUri(uriFor(R.drawable.playlist))
                .build(),
            BrowserMediaItem.FLAG_BROWSABLE
        )

    private val albumsBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.ALBUMS.id)
                .setTitle(getString(R.string.albums))
                .setIconUri(uriFor(R.drawable.disc))
                .build(),
            BrowserMediaItem.FLAG_BROWSABLE
        )

    private val favoritesBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.FAVORITES.id)
                .setTitle(getString(R.string.favorites))
                .setIconUri(uriFor(R.drawable.heart))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val offlineBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.OFFLINE.id)
                .setTitle(getString(R.string.offline))
                .setIconUri(uriFor(R.drawable.airplane))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val topBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.TOP.id)
                .setTitle(
                    getString(
                        R.string.format_my_top_playlist,
                        DataPreferences.topListLength.toString()
                    )
                )
                .setIconUri(uriFor(R.drawable.trending))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val localBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId(MediaId.LOCAL.id)
                .setTitle(getString(R.string.local))
                .setIconUri(uriFor(R.drawable.download))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val Song.asBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId((MediaId.SONGS / id).id)
                .setTitle(title)
                .setSubtitle(artistsText)
                .setIconUri(thumbnailUrl?.toUri())
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val PlaylistPreview.asBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId((MediaId.PLAYLISTS / playlist.id.toString()).id)
                .setTitle(playlist.name)
                .setSubtitle(
                    resources.getQuantityString(
                        R.plurals.song_count_plural,
                        songCount,
                        songCount
                    )
                )
                .setIconUri(uriFor(R.drawable.playlist))
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private val Album.asBrowserMediaItem
        inline get() = BrowserMediaItem(
            BrowserMediaDescription.Builder()
                .setMediaId((MediaId.ALBUMS / id).id)
                .setTitle(title)
                .setSubtitle(authorsText)
                .setIconUri(thumbnailUrl?.toUri())
                .build(),
            BrowserMediaItem.FLAG_PLAYABLE
        )

    private inner class SessionCallback(
        private val binder: PlayerService.Binder
    ) : MediaSession.Callback() {
        override fun onPlay() = binder.player.play()
        override fun onPause() = binder.player.pause()
        override fun onSkipToPrevious() = binder.player.forceSeekToPrevious()
        override fun onSkipToNext() = binder.player.forceSeekToNext()
        override fun onSeekTo(pos: Long) = binder.player.seekTo(pos)
        override fun onSkipToQueueItem(id: Long) = binder.player.seekToDefaultPosition(id.toInt())
        override fun onPlayFromSearch(query: String?, extras: Bundle?) {
            if (query.isNullOrBlank()) return
            binder.playFromSearch(query)
        }

        @Suppress("CyclomaticComplexMethod")
        @OptIn(UnstableApi::class)
        override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
            val data = mediaId?.split('/') ?: return
            var index = 0

            coroutineScope.launch {
                val mediaItems = when (data.getOrNull(0)?.let { MediaId(it) }) {
                    MediaId.SHUFFLE -> lastSongs

                    MediaId.SONGS -> data.getOrNull(1)?.let { songId ->
                        index = lastSongs.indexOfFirst { it.id == songId }
                        lastSongs
                    }

                    MediaId.FAVORITES ->
                        Database
                            .favorites()
                            .first()
                            .shuffled()

                    MediaId.OFFLINE ->
                        Database
                            .songsWithContentLength()
                            .first()
                            .filter { binder.isCached(it) }
                            .map(SongWithContentLength::song)
                            .shuffled()

                    MediaId.TOP -> {
                        val duration = DataPreferences.topListPeriod.duration
                        val length = DataPreferences.topListLength

                        val flow = if (duration != null) Database.trending(
                            limit = length,
                            period = duration.inWholeMilliseconds
                        ) else Database
                            .songsByPlayTimeDesc(limit = length)
                            .distinctUntilChanged()
                            .cancellable()

                        flow.first()
                    }

                    MediaId.LOCAL ->
                        Database
                            .songs(
                                sortBy = OrderPreferences.localSongSortBy,
                                sortOrder = OrderPreferences.localSongSortOrder,
                                isLocal = true
                            )
                            .map { songs -> songs.filter { it.durationText != "0:00" } }
                            .first()

                    MediaId.PLAYLISTS ->
                        data
                            .getOrNull(1)
                            ?.toLongOrNull()
                            ?.let(Database::playlistWithSongs)
                            ?.first()
                            ?.songs
                            ?.shuffled()

                    MediaId.ALBUMS ->
                        data
                            .getOrNull(1)
                            ?.let(Database::albumSongs)
                            ?.first()

                    else -> emptyList()
                }?.map(Song::asMediaItem) ?: return@launch

                withContext(Dispatchers.Main) {
                    binder.player.forcePlayAtIndex(
                        items = mediaItems,
                        index = index.coerceIn(0, mediaItems.size)
                    )
                }
            }
        }
    }

    @JvmInline
    private value class MediaId(val id: String) : CharSequence by id {
        companion object {
            val ROOT = MediaId("root")
            val SONGS = MediaId("songs")
            val PLAYLISTS = MediaId("playlists")
            val ALBUMS = MediaId("albums")

            val FAVORITES = MediaId("favorites")
            val OFFLINE = MediaId("offline")
            val TOP = MediaId("top")
            val LOCAL = MediaId("local")
            val SHUFFLE = MediaId("shuffle")
        }

        operator fun div(other: String) = MediaId("$id/$other")
        override fun chars(): IntStream = id.chars()
        override fun codePoints(): IntStream = id.codePoints()
    }
}
