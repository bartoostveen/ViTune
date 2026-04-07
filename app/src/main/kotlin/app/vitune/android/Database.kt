package app.vitune.android

import android.content.ContentValues
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase.CONFLICT_IGNORE
import android.os.Parcel
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.database.getFloatOrNull
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaLibraryInfo
import androidx.media3.common.util.UnstableApi
import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.DeleteColumn
import androidx.room.DeleteTable
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RenameColumn
import androidx.room.RenameTable
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.Upsert
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import app.vitune.android.DatabaseInitializer.From10To11Migration
import app.vitune.android.DatabaseInitializer.From14To15Migration
import app.vitune.android.DatabaseInitializer.From22To23Migration
import app.vitune.android.DatabaseInitializer.From23To24Migration
import app.vitune.android.DatabaseInitializer.From8To9Migration
import app.vitune.android.models.Album
import app.vitune.android.models.Artist
import app.vitune.android.models.Event
import app.vitune.android.models.EventWithSong
import app.vitune.android.models.Format
import app.vitune.android.models.Info
import app.vitune.android.models.Lyrics
import app.vitune.android.models.PipedSession
import app.vitune.android.models.Playlist
import app.vitune.android.models.PlaylistPreview
import app.vitune.android.models.PlaylistWithSongs
import app.vitune.android.models.QueuedMediaItem
import app.vitune.android.models.SearchQuery
import app.vitune.android.models.Song
import app.vitune.android.models.SongAlbumMap
import app.vitune.android.models.SongArtistMap
import app.vitune.android.models.SongPlaylistMap
import app.vitune.android.models.SongWithContentLength
import app.vitune.android.models.SortedSongPlaylistMap
import app.vitune.android.service.LOCAL_KEY_PREFIX
import app.vitune.core.data.enums.AlbumSortBy
import app.vitune.core.data.enums.ArtistSortBy
import app.vitune.core.data.enums.PlaylistSortBy
import app.vitune.core.data.enums.SongSortBy
import app.vitune.core.data.enums.SortOrder
import app.vitune.core.ui.utils.songBundle
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object DatabaseDependency {
    private var _instance by mutableStateOf(buildDatabase())
    private val mutex = Mutex()
    val instance get() = _instance

    private fun buildDatabase() = Room
        .databaseBuilder(
            context = Dependencies.application.applicationContext,
            klass = DatabaseInitializer::class.java,
            name = "data.db"
        )
        .addMigrations(
            From8To9Migration(),
            From10To11Migration(),
            From14To15Migration(),
            From22To23Migration(),
            From23To24Migration()
        )
        .build()

    // Unfortunately, this HAS to block with the current architecture
    fun reload() = runBlocking {
        mutex.withLock {
            _instance = buildDatabase()
        }
    }
}

@Dao // WHY WHY WHY WHY
interface Database {
    companion object : DatabaseAccessor by DatabaseDependency.instance.database
}

@Dao
@Suppress("TooManyFunctions")
interface DatabaseAccessor {
    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%' ORDER BY ROWID ASC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%' ORDER BY ROWID DESC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByRowIdDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%' ORDER BY title COLLATE NOCASE ASC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%' ORDER BY title COLLATE NOCASE DESC")
    @RewriteQueriesToDropUnusedColumns
    fun songsByTitleDesc(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%'
        ORDER BY totalPlayTimeMs ASC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun songsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        WHERE id NOT LIKE '$LOCAL_KEY_PREFIX%'
        ORDER BY totalPlayTimeMs DESC
        LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun songsByPlayTimeDesc(limit: Int = -1): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY ROWID ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByRowIdAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY ROWID DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByRowIdDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY title COLLATE NOCASE ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY title COLLATE NOCASE DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByTitleDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY totalPlayTimeMs ASC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE id LIKE '$LOCAL_KEY_PREFIX%' ORDER BY totalPlayTimeMs DESC")
    @RewriteQueriesToDropUnusedColumns
    fun localSongsByPlayTimeDesc(): Flow<List<Song>>

    @Suppress("CyclomaticComplexMethod")
    fun songs(sortBy: SongSortBy, sortOrder: SortOrder, isLocal: Boolean = false) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByPlayTimeAsc() else songsByPlayTimeAsc()
            SortOrder.Descending -> if (isLocal) localSongsByPlayTimeDesc() else songsByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByTitleAsc() else songsByTitleAsc()
            SortOrder.Descending -> if (isLocal) localSongsByTitleDesc() else songsByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> if (isLocal) localSongsByRowIdAsc() else songsByRowIdAsc()
            SortOrder.Descending -> if (isLocal) localSongsByRowIdDesc() else songsByRowIdDesc()
        }
    }

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY totalPlayTimeMs ASC")
    fun favoritesByPlayTimeAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY totalPlayTimeMs DESC")
    fun favoritesByPlayTimeDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY likedAt ASC")
    fun favoritesByLikedAtAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY likedAt DESC")
    fun favoritesByLikedAtDesc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun favoritesByTitleAsc(): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Song WHERE likedAt IS NOT NULL ORDER BY title COLLATE NOCASE DESC")
    fun favoritesByTitleDesc(): Flow<List<Song>>

    fun favorites(
        sortBy: SongSortBy = SongSortBy.DateAdded,
        sortOrder: SortOrder = SortOrder.Descending
    ) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByPlayTimeAsc()
            SortOrder.Descending -> favoritesByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByTitleAsc()
            SortOrder.Descending -> favoritesByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> favoritesByLikedAtAsc()
            SortOrder.Descending -> favoritesByLikedAtDesc()
        }
    }

    @Query("SELECT * FROM QueuedMediaItem")
    fun queue(): List<QueuedMediaItem>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = Event.songId
        WHERE Event.ROWID in (
	        SELECT max(Event.ROWID)
	        FROM Event
	        GROUP BY songId
        )
        ORDER BY timestamp DESC
        LIMIT :size
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun history(size: Int = 100): Flow<List<Song>>

    @Query("DELETE FROM QueuedMediaItem")
    fun clearQueue()

    @Query("SELECT * FROM SearchQuery WHERE `query` LIKE :query ORDER BY id DESC")
    fun queries(query: String): Flow<List<SearchQuery>>

    @Query("SELECT COUNT (*) FROM SearchQuery")
    fun queriesCount(): Flow<Int>

    @Query("DELETE FROM SearchQuery")
    fun clearQueries()

    @Query("SELECT * FROM Song WHERE id = :id")
    fun song(id: String): Flow<Song?>

    @Query("SELECT likedAt FROM Song WHERE id = :songId")
    fun likedAt(songId: String): Flow<Long?>

    @Query("UPDATE Song SET likedAt = :likedAt WHERE id = :songId")
    fun like(songId: String, likedAt: Long?): Int

    @Query("UPDATE Song SET durationText = :durationText WHERE id = :songId")
    fun updateDurationText(songId: String, durationText: String): Int

    @Query("SELECT * FROM Lyrics WHERE songId = :songId")
    fun lyrics(songId: String): Flow<Lyrics?>

    @Query("SELECT * FROM Artist WHERE id = :id")
    fun artist(id: String): Flow<Artist?>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY name COLLATE NOCASE DESC")
    fun artistsByNameDesc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY name COLLATE NOCASE ASC")
    fun artistsByNameAsc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun artistsByRowIdDesc(): Flow<List<Artist>>

    @Query("SELECT * FROM Artist WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt ASC")
    fun artistsByRowIdAsc(): Flow<List<Artist>>

    fun artists(sortBy: ArtistSortBy, sortOrder: SortOrder) = when (sortBy) {
        ArtistSortBy.Name -> when (sortOrder) {
            SortOrder.Ascending -> artistsByNameAsc()
            SortOrder.Descending -> artistsByNameDesc()
        }

        ArtistSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> artistsByRowIdAsc()
            SortOrder.Descending -> artistsByRowIdDesc()
        }
    }

    @Query("SELECT * FROM Album WHERE id = :id")
    fun album(id: String): Flow<Album?>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        JOIN SongAlbumMap ON Song.id = SongAlbumMap.songId
        WHERE SongAlbumMap.albumId = :albumId AND
        position IS NOT NULL
        ORDER BY position
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun albumSongs(albumId: String): Flow<List<Song>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE ASC")
    fun albumsByTitleAsc(): Flow<List<Album>>

    // authorsText as fallback for when YouTube showed the year in the artist field
    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY year ASC, authorsText COLLATE NOCASE ASC")
    fun albumsByYearAsc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt ASC")
    fun albumsByRowIdAsc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY title COLLATE NOCASE DESC")
    fun albumsByTitleDesc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY year DESC, authorsText COLLATE NOCASE DESC")
    fun albumsByYearDesc(): Flow<List<Album>>

    @Query("SELECT * FROM Album WHERE bookmarkedAt IS NOT NULL ORDER BY bookmarkedAt DESC")
    fun albumsByRowIdDesc(): Flow<List<Album>>

    fun albums(sortBy: AlbumSortBy, sortOrder: SortOrder) = when (sortBy) {
        AlbumSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> albumsByTitleAsc()
            SortOrder.Descending -> albumsByTitleDesc()
        }

        AlbumSortBy.Year -> when (sortOrder) {
            SortOrder.Ascending -> albumsByYearAsc()
            SortOrder.Descending -> albumsByYearDesc()
        }

        AlbumSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> albumsByRowIdAsc()
            SortOrder.Descending -> albumsByRowIdDesc()
        }
    }

    @Query("UPDATE Song SET totalPlayTimeMs = totalPlayTimeMs + :addition WHERE id = :id")
    fun incrementTotalPlayTimeMs(id: String, addition: Long)

    @Query("SELECT * FROM PipedSession")
    fun pipedSessions(): Flow<List<PipedSession>>

    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun playlist(id: Long): Flow<Playlist?>

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query(
        """
        SELECT * FROM SortedSongPlaylistMap
        INNER JOIN Song on Song.id = SortedSongPlaylistMap.songId
        WHERE playlistId = :id
        ORDER BY SortedSongPlaylistMap.position
        """
    )
    fun playlistSongs(id: Long): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Playlist WHERE id = :id")
    fun playlistWithSongs(id: Long): Flow<PlaylistWithSongs?>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY name COLLATE NOCASE ASC
        """
    )
    fun playlistPreviewsByNameAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY ROWID ASC
        """
    )
    fun playlistPreviewsByDateAddedAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY songCount ASC
        """
    )
    fun playlistPreviewsByDateSongCountAsc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY name COLLATE NOCASE DESC
        """
    )
    fun playlistPreviewsByNameDesc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY ROWID DESC
        """
    )
    fun playlistPreviewsByDateAddedDesc(): Flow<List<PlaylistPreview>>

    @Transaction
    @Query(
        """
        SELECT id, name, (SELECT COUNT(*) FROM SongPlaylistMap WHERE playlistId = id) as songCount, thumbnail FROM Playlist
        ORDER BY songCount DESC
        """
    )
    fun playlistPreviewsByDateSongCountDesc(): Flow<List<PlaylistPreview>>

    fun playlistPreviews(
        sortBy: PlaylistSortBy,
        sortOrder: SortOrder
    ) = when (sortBy) {
        PlaylistSortBy.Name -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByNameAsc()
            SortOrder.Descending -> playlistPreviewsByNameDesc()
        }

        PlaylistSortBy.SongCount -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByDateSongCountAsc()
            SortOrder.Descending -> playlistPreviewsByDateSongCountDesc()
        }

        PlaylistSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> playlistPreviewsByDateAddedAsc()
            SortOrder.Descending -> playlistPreviewsByDateAddedDesc()
        }
    }

    @Query(
        """
        SELECT thumbnailUrl FROM Song
        JOIN SongPlaylistMap ON id = songId
        WHERE playlistId = :id
        ORDER BY position
        LIMIT 4
        """
    )
    fun playlistThumbnailUrls(id: Long): Flow<List<String>>

    @Transaction
    @Query(
        """
        SELECT * FROM Song
        JOIN SongArtistMap ON Song.id = SongArtistMap.songId
        WHERE SongArtistMap.artistId = :artistId AND
        totalPlayTimeMs > 0
        ORDER BY Song.ROWID DESC
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun artistSongs(artistId: String): Flow<List<Song>>

    @Query("SELECT * FROM Format WHERE songId = :songId")
    fun format(songId: String): Flow<Format?>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.totalPlayTimeMs ASC
        """
    )
    fun songsWithContentLengthByPlayTimeAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.totalPlayTimeMs DESC
        """
    )
    fun songsWithContentLengthByPlayTimeDesc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.ROWID ASC
        """
    )
    fun songsWithContentLengthByRowIdAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.ROWID DESC
        """
    )
    fun songsWithContentLengthByRowIdDesc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.title COLLATE NOCASE ASC
        """
    )
    fun songsWithContentLengthByTitleAsc(): Flow<List<SongWithContentLength>>

    @Transaction
    @Query(
        """
        SELECT Song.*, contentLength FROM Song
        JOIN Format ON id = songId
        WHERE contentLength IS NOT NULL
        ORDER BY Song.title COLLATE NOCASE DESC
        """
    )
    fun songsWithContentLengthByTitleDesc(): Flow<List<SongWithContentLength>>

    fun songsWithContentLength(
        sortBy: SongSortBy = SongSortBy.DateAdded,
        sortOrder: SortOrder = SortOrder.Descending
    ) = when (sortBy) {
        SongSortBy.PlayTime -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByPlayTimeAsc()
            SortOrder.Descending -> songsWithContentLengthByPlayTimeDesc()
        }

        SongSortBy.Title -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByTitleAsc()
            SortOrder.Descending -> songsWithContentLengthByTitleDesc()
        }

        SongSortBy.DateAdded -> when (sortOrder) {
            SortOrder.Ascending -> songsWithContentLengthByRowIdAsc()
            SortOrder.Descending -> songsWithContentLengthByRowIdDesc()
        }
    }

    @Query("SELECT id FROM Song WHERE blacklisted")
    suspend fun blacklistedIds(): List<String>

    @Query("SELECT blacklisted FROM Song WHERE id = :songId")
    fun blacklisted(songId: String): Flow<Boolean>

    @Query("SELECT COUNT (*) FROM Song where blacklisted")
    fun blacklistLength(): Flow<Int>

    @Transaction
    @Query("UPDATE Song SET blacklisted = NOT blacklisted WHERE blacklisted")
    fun resetBlacklist()

    @Transaction
    @Query("UPDATE Song SET blacklisted = NOT blacklisted WHERE id = :songId")
    fun toggleBlacklist(songId: String)

    suspend fun filterBlacklistedSongs(songs: List<MediaItem>): List<MediaItem> {
        val blacklistedIds = blacklistedIds()
        return songs.filter { it.mediaId !in blacklistedIds }
    }

    @Transaction
    @Query(
        """
        UPDATE SongPlaylistMap SET position =
          CASE
            WHEN position < :fromPosition THEN position + 1
            WHEN position > :fromPosition THEN position - 1
            ELSE :toPosition
          END
        WHERE playlistId = :playlistId AND position BETWEEN MIN(:fromPosition,:toPosition) and MAX(:fromPosition,:toPosition)
        """
    )
    fun move(playlistId: Long, fromPosition: Int, toPosition: Int)

    @Query("DELETE FROM SongPlaylistMap WHERE playlistId = :id")
    fun clearPlaylist(id: Long)

    @Query("DELETE FROM SongAlbumMap WHERE albumId = :id")
    fun clearAlbum(id: String)

    @Query("SELECT loudnessDb FROM Format WHERE songId = :songId")
    fun loudnessDb(songId: String): Flow<Float?>

    @Query("SELECT Song.loudnessBoost FROM Song WHERE id = :songId")
    fun loudnessBoost(songId: String): Flow<Float?>

    @Query("UPDATE Song SET loudnessBoost = :loudnessBoost WHERE id = :songId")
    fun setLoudnessBoost(songId: String, loudnessBoost: Float?)

    @Query("SELECT * FROM Song WHERE title LIKE :query OR artistsText LIKE :query")
    fun search(query: String): Flow<List<Song>>

    @Query("SELECT albumId AS id, NULL AS name FROM SongAlbumMap WHERE songId = :songId")
    suspend fun songAlbumInfo(songId: String): Info?

    @Query("SELECT id, name FROM Artist LEFT JOIN SongArtistMap ON id = artistId WHERE songId = :songId")
    suspend fun songArtistInfo(songId: String): List<Info>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = songId
        WHERE Song.id NOT LIKE '$LOCAL_KEY_PREFIX%'
        GROUP BY songId
        ORDER BY SUM(playTime)
        DESC LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun trending(limit: Int = 3): Flow<List<Song>>

    @Transaction
    @Query(
        """
        SELECT Song.* FROM Event
        JOIN Song ON Song.id = songId
        WHERE (:now - Event.timestamp) <= :period AND
        Song.id NOT LIKE '$LOCAL_KEY_PREFIX%'
        GROUP BY songId
        ORDER BY SUM(playTime) DESC
        LIMIT :limit
        """
    )
    @RewriteQueriesToDropUnusedColumns
    fun trending(
        limit: Int = 3,
        now: Long = System.currentTimeMillis(),
        period: Long
    ): Flow<List<Song>>

    @Transaction
    @Query("SELECT * FROM Event ORDER BY timestamp DESC")
    fun events(): Flow<List<EventWithSong>>

    @Query("SELECT COUNT (*) FROM Event")
    fun eventsCount(): Flow<Int>

    @Query("DELETE FROM Event")
    fun clearEvents()

    @Query("DELETE FROM Event WHERE songId = :songId")
    fun clearEventsFor(songId: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    @Throws(SQLException::class)
    fun insert(event: Event)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(format: Format)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(searchQuery: SearchQuery)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(playlist: Playlist): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(songPlaylistMap: SongPlaylistMap): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(songArtistMap: SongArtistMap): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(song: Song): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(queuedMediaItems: List<QueuedMediaItem>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertSongPlaylistMaps(songPlaylistMaps: List<SongPlaylistMap>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(album: Album, songAlbumMap: SongAlbumMap)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(artists: List<Artist>, songArtistMaps: List<SongArtistMap>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(pipedSession: PipedSession)

    @Transaction
    fun insert(mediaItem: MediaItem, block: (Song) -> Song = { it }) {
        val extras = mediaItem.mediaMetadata.extras?.songBundle
        val song = Song(
            id = mediaItem.mediaId,
            title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artistsText = mediaItem.mediaMetadata.artist?.toString(),
            durationText = extras?.durationText,
            thumbnailUrl = mediaItem.mediaMetadata.artworkUri?.toString(),
            explicit = extras?.explicit == true
        ).let(block).also { song ->
            if (insert(song) == -1L) return
        }

        extras?.albumId?.let { albumId ->
            insert(
                Album(id = albumId, title = mediaItem.mediaMetadata.albumTitle?.toString()),
                SongAlbumMap(songId = song.id, albumId = albumId, position = null)
            )
        }

        extras?.artistNames?.let { artistNames ->
            extras.artistIds?.let { artistIds ->
                if (artistNames.size == artistIds.size) insert(
                    artistNames.mapIndexed { index, artistName ->
                        Artist(
                            id = artistIds[index],
                            name = artistName
                        )
                    },
                    artistIds.map { artistId ->
                        SongArtistMap(
                            songId = song.id,
                            artistId = artistId
                        )
                    }
                )
            }
        }
    }

    @Update
    fun update(artist: Artist)

    @Update
    fun update(album: Album)

    @Update
    fun update(playlist: Playlist)

    @Upsert
    fun upsert(lyrics: Lyrics)

    @Upsert
    fun upsert(album: Album, songAlbumMaps: List<SongAlbumMap>)

    @Upsert
    fun upsert(artist: Artist)

    @Delete
    fun delete(song: Song)

    @Delete
    fun delete(searchQuery: SearchQuery)

    @Delete
    fun delete(playlist: Playlist)

    @Delete
    fun delete(songPlaylistMap: SongPlaylistMap)

    @Delete
    fun delete(pipedSession: PipedSession)

    @RawQuery
    fun raw(supportSQLiteQuery: SupportSQLiteQuery): Int

    fun checkpoint() {
        raw(SimpleSQLiteQuery("PRAGMA wal_checkpoint(FULL)"))
    }
}

@androidx.room.Database(
    entities = [
        Song::class,
        SongPlaylistMap::class,
        Playlist::class,
        Artist::class,
        SongArtistMap::class,
        Album::class,
        SongAlbumMap::class,
        SearchQuery::class,
        QueuedMediaItem::class,
        Format::class,
        Event::class,
        Lyrics::class,
        PipedSession::class
    ],
    views = [SortedSongPlaylistMap::class],
    version = 30,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4, spec = DatabaseInitializer.From3To4Migration::class),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8, spec = DatabaseInitializer.From7To8Migration::class),
        AutoMigration(from = 9, to = 10),
        AutoMigration(from = 11, to = 12, spec = DatabaseInitializer.From11To12Migration::class),
        AutoMigration(from = 12, to = 13),
        AutoMigration(from = 13, to = 14),
        AutoMigration(from = 15, to = 16),
        AutoMigration(from = 16, to = 17),
        AutoMigration(from = 17, to = 18),
        AutoMigration(from = 18, to = 19),
        AutoMigration(from = 19, to = 20),
        AutoMigration(from = 20, to = 21, spec = DatabaseInitializer.From20To21Migration::class),
        AutoMigration(from = 21, to = 22, spec = DatabaseInitializer.From21To22Migration::class),
        AutoMigration(from = 23, to = 24),
        AutoMigration(from = 24, to = 25),
        AutoMigration(from = 25, to = 26),
        AutoMigration(from = 26, to = 27),
        AutoMigration(from = 27, to = 28),
        AutoMigration(from = 28, to = 29),
        AutoMigration(from = 29, to = 30)
    ]
)
@TypeConverters(Converters::class)
abstract class DatabaseInitializer protected constructor() : RoomDatabase() {
    abstract val database: DatabaseAccessor

    @DeleteTable.Entries(DeleteTable(tableName = "QueuedMediaItem"))
    class From3To4Migration : AutoMigrationSpec

    @RenameColumn.Entries(RenameColumn("Song", "albumInfoId", "albumId"))
    class From7To8Migration : AutoMigrationSpec

    class From8To9Migration : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.query(
                SimpleSQLiteQuery(
                    query = "SELECT DISTINCT browseId, text, Info.id FROM Info JOIN Song ON Info.id = Song.albumId;"
                )
            ).use { cursor ->
                val albumValues = ContentValues(2)
                while (cursor.moveToNext()) {
                    albumValues.put("id", cursor.getString(0))
                    albumValues.put("title", cursor.getString(1))
                    db.insert("Album", CONFLICT_IGNORE, albumValues)

                    db.execSQL(
                        "UPDATE Song SET albumId = '${cursor.getString(0)}' WHERE albumId = ${
                            cursor.getLong(
                                2
                            )
                        }"
                    )
                }
            }

            db.query(
                SimpleSQLiteQuery(
                    query = """
                        SELECT GROUP_CONCAT(text, ''), SongWithAuthors.songId FROM Info
                        JOIN SongWithAuthors ON Info.id = SongWithAuthors.authorInfoId
                        GROUP BY songId;
                    """.trimIndent()
                )
            ).use { cursor ->
                val songValues = ContentValues(1)
                while (cursor.moveToNext()) {
                    songValues.put("artistsText", cursor.getString(0))
                    db.update(
                        table = "Song",
                        conflictAlgorithm = CONFLICT_IGNORE,
                        values = songValues,
                        whereClause = "id = ?",
                        whereArgs = arrayOf(cursor.getString(1))
                    )
                }
            }

            db.query(
                SimpleSQLiteQuery(
                    query = """
                        SELECT browseId, text, Info.id FROM Info
                        JOIN SongWithAuthors ON Info.id = SongWithAuthors.authorInfoId
                        WHERE browseId NOT NULL;
                    """.trimIndent()
                )
            ).use { cursor ->
                val artistValues = ContentValues(2)
                while (cursor.moveToNext()) {
                    artistValues.put("id", cursor.getString(0))
                    artistValues.put("name", cursor.getString(1))
                    db.insert("Artist", CONFLICT_IGNORE, artistValues)

                    db.execSQL(
                        "UPDATE SongWithAuthors SET authorInfoId = '${cursor.getString(0)}' WHERE authorInfoId = ${
                            cursor.getLong(2)
                        }"
                    )
                }
            }

            db.execSQL("INSERT INTO SongArtistMap(songId, artistId) SELECT songId, authorInfoId FROM SongWithAuthors")

            db.execSQL("DROP TABLE Info;")
            db.execSQL("DROP TABLE SongWithAuthors;")
        }
    }

    class From10To11Migration : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.query(SimpleSQLiteQuery("SELECT id, albumId FROM Song;")).use { cursor ->
                val songAlbumMapValues = ContentValues(2)
                while (cursor.moveToNext()) {
                    songAlbumMapValues.put("songId", cursor.getString(0))
                    songAlbumMapValues.put("albumId", cursor.getString(1))
                    db.insert("SongAlbumMap", CONFLICT_IGNORE, songAlbumMapValues)
                }
            }

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `Song_new` (
                    `id` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `artistsText` TEXT,
                    `durationText` TEXT NOT NULL,
                    `thumbnailUrl` TEXT, `lyrics` TEXT,
                    `likedAt` INTEGER,
                    `totalPlayTimeMs` INTEGER NOT NULL,
                    `loudnessDb` REAL,
                    `contentLength` INTEGER,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                    INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, lyrics,
                    likedAt, totalPlayTimeMs, loudnessDb, contentLength) SELECT id, title, artistsText,
                    durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs, loudnessDb, contentLength
                    FROM Song;
                """.trimIndent()
            )
            db.execSQL("DROP TABLE Song;")
            db.execSQL("ALTER TABLE Song_new RENAME TO Song;")
        }
    }

    @RenameTable("SongInPlaylist", "SongPlaylistMap")
    @RenameTable("SortedSongInPlaylist", "SortedSongPlaylistMap")
    class From11To12Migration : AutoMigrationSpec

    class From14To15Migration : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.query(SimpleSQLiteQuery("SELECT id, loudnessDb, contentLength FROM Song;"))
                .use { cursor ->
                    val formatValues = ContentValues(3)
                    while (cursor.moveToNext()) {
                        formatValues.put("songId", cursor.getString(0))
                        formatValues.put("loudnessDb", cursor.getFloatOrNull(1))
                        formatValues.put("contentLength", cursor.getFloatOrNull(2))
                        db.insert("Format", CONFLICT_IGNORE, formatValues)
                    }
                }

            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `Song_new` (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artistsText` TEXT,
                        `durationText` TEXT NOT NULL,
                        `thumbnailUrl` TEXT,
                        `lyrics` TEXT,
                        `likedAt` INTEGER,
                        `totalPlayTimeMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent()
            )

            db.execSQL(
                """
                    INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs)
                    SELECT id, title, artistsText, durationText, thumbnailUrl, lyrics, likedAt, totalPlayTimeMs
                    FROM Song;
                """.trimIndent()
            )
            db.execSQL("DROP TABLE Song;")
            db.execSQL("ALTER TABLE Song_new RENAME TO Song;")
        }
    }

    @DeleteColumn.Entries(
        DeleteColumn("Artist", "shuffleVideoId"),
        DeleteColumn("Artist", "shufflePlaylistId"),
        DeleteColumn("Artist", "radioVideoId"),
        DeleteColumn("Artist", "radioPlaylistId")
    )
    class From20To21Migration : AutoMigrationSpec

    @DeleteColumn.Entries(DeleteColumn("Artist", "info"))
    class From21To22Migration : AutoMigrationSpec

    class From22To23Migration : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS Lyrics (
                        `songId` TEXT NOT NULL,
                        `fixed` TEXT,
                        `synced` TEXT,
                        PRIMARY KEY(`songId`),
                        FOREIGN KEY(`songId`) REFERENCES `Song`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                """.trimIndent()
            )

            db.query(SimpleSQLiteQuery("SELECT id, lyrics, synchronizedLyrics FROM Song;"))
                .use { cursor ->
                    val lyricsValues = ContentValues(3)
                    while (cursor.moveToNext()) {
                        lyricsValues.put("songId", cursor.getString(0))
                        lyricsValues.put("fixed", cursor.getString(1))
                        lyricsValues.put("synced", cursor.getString(2))
                        db.insert("Lyrics", CONFLICT_IGNORE, lyricsValues)
                    }
                }

            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS Song_new (
                        `id` TEXT NOT NULL,
                        `title` TEXT NOT NULL,
                        `artistsText` TEXT,
                        `durationText` TEXT,
                        `thumbnailUrl` TEXT,
                        `likedAt` INTEGER,
                        `totalPlayTimeMs` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent()
            )
            db.execSQL(
                """
                    INSERT INTO Song_new(id, title, artistsText, durationText, thumbnailUrl, likedAt, totalPlayTimeMs)
                    SELECT id, title, artistsText, durationText, thumbnailUrl, likedAt, totalPlayTimeMs
                    FROM Song;
                """.trimIndent()
            )
            db.execSQL("DROP TABLE Song;")
            db.execSQL("ALTER TABLE Song_new RENAME TO Song;")
        }
    }

    class From23To24Migration : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) =
            db.execSQL("ALTER TABLE Song ADD COLUMN loudnessBoost REAL")
    }
}

@TypeConverters
object Converters {
    @TypeConverter
    @OptIn(UnstableApi::class)
    fun mediaItemFromByteArray(value: ByteArray?): MediaItem? = value?.let { byteArray ->
        runCatching {
            val parcel = Parcel.obtain()
            parcel.unmarshall(byteArray, 0, byteArray.size)
            parcel.setDataPosition(0)
            val bundle = parcel.readBundle(MediaItem::class.java.classLoader)
            parcel.recycle()

            bundle?.let { MediaItem.fromBundle(it, MediaLibraryInfo.INTERFACE_VERSION) }
        }.getOrNull()
    }

    @TypeConverter
    @OptIn(UnstableApi::class)
    fun mediaItemToByteArray(mediaItem: MediaItem?): ByteArray? = mediaItem
        ?.toBundle(MediaLibraryInfo.INTERFACE_VERSION)
        ?.let {
            val parcel = Parcel.obtain()
            parcel.writeBundle(it)
            val bytes = parcel.marshall()
            parcel.recycle()

            bytes
        }

    @TypeConverter
    fun urlToString(url: Url) = url.toString()

    @TypeConverter
    fun stringToUrl(string: String) = Url(string)
}

@Suppress("UnusedReceiverParameter")
val DatabaseAccessor.internal: RoomDatabase
    get() = DatabaseDependency.instance

fun query(block: () -> Unit) = DatabaseDependency.instance.queryExecutor.execute(block)

fun transaction(block: () -> Unit) = with(DatabaseDependency.instance) {
    transactionExecutor.execute {
        runInTransaction(block)
    }
}
