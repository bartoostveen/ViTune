package app.vitune.desktop.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class Settings(
    val volume: Int = 80,
    val autoplay: Boolean = true,
    val equalizerPreset: String? = null,
    val playbackSpeed: Float = 1f,
    val showSidePanel: Boolean = true
)

@Serializable
data class AppData(
    val playlists: List<Playlist> = emptyList(),
    /** Legacy: only song ids. Kept so older data files still load. */
    val favorites: Set<String> = emptySet(),
    val likedSongs: List<Song> = emptyList(),
    val recentlyPlayed: List<Song> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val settings: Settings = Settings()
)

/**
 * Everything the app remembers between launches. All functions are meant to be called
 * from the UI thread; changes are written to disk shortly afterwards in the background.
 */
class AppState(private val dataDir: File, private val scope: CoroutineScope) {
    private val dataFile = File(dataDir, "app-data.json")
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private var saveJob: Job? = null

    var playlists by mutableStateOf<List<Playlist>>(emptyList())
        private set
    var likedSongs by mutableStateOf<List<Song>>(emptyList())
        private set
    var recentlyPlayed by mutableStateOf<List<Song>>(emptyList())
        private set
    var searchHistory by mutableStateOf<List<String>>(emptyList())
        private set
    var settings by mutableStateOf(Settings())
        private set

    private var legacyFavorites: Set<String> = emptySet()

    suspend fun load() = withContext(Dispatchers.IO) {
        try {
            dataDir.mkdirs()
            if (dataFile.exists()) {
                val data = json.decodeFromString<AppData>(dataFile.readText()).let { d ->
                    d.copy(
                        playlists = d.playlists.map { p -> p.withSongs(p.songs.map { it.withCleanArtists() }) },
                        likedSongs = d.likedSongs.map { it.withCleanArtists() },
                        recentlyPlayed = d.recentlyPlayed.map { it.withCleanArtists() }
                    )
                }
                playlists = data.playlists
                legacyFavorites = data.favorites
                // Recover liked songs saved by older versions, which only stored ids
                val recoverable = data.recentlyPlayed.filter { it.id in data.favorites }
                likedSongs = (data.likedSongs + recoverable).distinctBy { it.id }
                recentlyPlayed = data.recentlyPlayed.distinctBy { it.id }
                searchHistory = data.searchHistory
                settings = data.settings
            }
        } catch (e: Exception) {
            System.err.println("Failed to load app state: ${e.message}")
        }
    }

    private fun snapshot() = AppData(
        playlists = playlists,
        favorites = legacyFavorites,
        likedSongs = likedSongs,
        recentlyPlayed = recentlyPlayed,
        searchHistory = searchHistory,
        settings = settings
    )

    private fun write(data: AppData) {
        try {
            dataDir.mkdirs()
            val tmp = File(dataDir, "app-data.json.tmp")
            tmp.writeText(json.encodeToString(AppData.serializer(), data))
            if (!tmp.renameTo(dataFile)) {
                dataFile.delete()
                tmp.renameTo(dataFile)
            }
        } catch (e: Exception) {
            System.err.println("Failed to save app state: ${e.message}")
        }
    }

    private fun save() {
        val data = snapshot()
        saveJob?.cancel()
        saveJob = scope.launch(Dispatchers.IO) {
            delay(400)
            write(data)
        }
    }

    /** Writes immediately; call before the app exits. */
    fun flush() {
        saveJob?.cancel()
        write(snapshot())
    }

    // Playlists

    fun createPlaylist(name: String, description: String = ""): Playlist {
        val playlist = Playlist.newPlaylist(name.trim(), description.trim())
        playlists = playlists + playlist
        save()
        return playlist
    }

    fun updatePlaylist(playlist: Playlist) {
        playlists = playlists.map { if (it.id == playlist.id) playlist else it }
        save()
    }

    fun deletePlaylist(playlistId: String) {
        playlists = playlists.filter { it.id != playlistId }
        save()
    }

    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    /** Returns false if the song was already in the playlist. */
    fun addToPlaylist(playlistId: String, song: Song): Boolean {
        val playlist = playlist(playlistId) ?: return false
        if (playlist.songs.any { it.id == song.id }) return false
        updatePlaylist(playlist.withSongs(playlist.songs + song))
        return true
    }

    fun removeFromPlaylist(playlistId: String, songId: String) {
        val playlist = playlist(playlistId) ?: return
        updatePlaylist(playlist.withSongs(playlist.songs.filter { it.id != songId }))
    }

    // Liked songs

    fun isLiked(songId: String): Boolean = likedSongs.any { it.id == songId }

    /** Returns true if the song is liked afterwards. */
    fun toggleLike(song: Song): Boolean {
        val nowLiked = !isLiked(song.id)
        likedSongs = if (nowLiked) listOf(song) + likedSongs else likedSongs.filter { it.id != song.id }
        legacyFavorites = legacyFavorites - song.id
        save()
        return nowLiked
    }

    // History

    fun addToRecentlyPlayed(song: Song) {
        recentlyPlayed = (listOf(song) + recentlyPlayed.filter { it.id != song.id }).take(100)
        save()
    }

    fun clearRecentlyPlayed() {
        recentlyPlayed = emptyList()
        save()
    }

    fun addSearch(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        searchHistory = (listOf(q) + searchHistory.filter { !it.equals(q, ignoreCase = true) }).take(12)
        save()
    }

    fun removeSearch(query: String) {
        searchHistory = searchHistory - query
        save()
    }

    fun clearSearchHistory() {
        searchHistory = emptyList()
        save()
    }

    // Settings

    fun updateSettings(transform: (Settings) -> Settings) {
        val updated = transform(settings)
        if (updated != settings) {
            settings = updated
            save()
        }
    }
}
