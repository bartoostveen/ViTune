package app.vitune.desktop.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import app.vitune.desktop.model.AppState
import app.vitune.desktop.model.ExternalPlaylist
import app.vitune.desktop.model.ExternalTrack
import app.vitune.desktop.model.PlaylistImport
import app.vitune.desktop.model.Song
import app.vitune.desktop.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

sealed interface Screen {
    data object Home : Screen
    data class Search(val query: String? = null) : Screen
    data object Library : Screen
    data object Liked : Screen
    data class Playlist(val id: String) : Screen
}

enum class SidePanel { QUEUE, LYRICS }

/** App-wide UI state and actions, available to every screen through [LocalUi]. */
class UiActions(
    val controller: PlayerController,
    val appState: AppState,
    private val scope: CoroutineScope
) {
    // ---------- Playlist import (Spotify link or CSV) ----------

    data class ImportProgress(
        val sourceName: String,
        val playlistId: String? = null,
        val total: Int = 0,
        val done: Int = 0,
        val matched: Int = 0,
        val missing: List<ExternalTrack> = emptyList(),
        val truncated: Boolean = false,
        val finished: Boolean = false,
        val error: String? = null
    ) {
        val running: Boolean get() = !finished && error == null
    }

    var importDialogOpen by mutableStateOf(false)
    var importProgress by mutableStateOf<ImportProgress?>(null)
        private set
    private var importJob: Job? = null

    /** Fetches the track list, creates the playlist, then fills it as songs are found on YouTube Music. */
    fun startImport(label: String, load: suspend () -> ExternalPlaylist) {
        importJob?.cancel()
        importProgress = ImportProgress(sourceName = label)
        importJob = scope.launch {
            val source = try {
                load()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                importProgress = ImportProgress(sourceName = label, error = e.message ?: "Couldn't read that playlist.")
                return@launch
            }

            val playlist = appState.createPlaylist(source.name, source.description)
            source.coverUrl?.let { appState.updatePlaylist(playlist.copy(thumbnailUrl = it)) }
            importProgress = ImportProgress(
                sourceName = source.name,
                playlistId = playlist.id,
                total = source.tracks.size,
                truncated = source.tracks.size >= PlaylistImport.SPOTIFY_LINK_LIMIT && label == "Spotify"
            )

            val found = arrayOfNulls<Song>(source.tracks.size)
            val missing = mutableListOf<ExternalTrack>()
            val gate = Semaphore(4)
            val lock = Mutex()
            coroutineScope {
                source.tracks.forEachIndexed { index, track ->
                    launch {
                        val song = gate.withPermit {
                            runCatching { PlaylistImport.findOnYouTubeMusic(track) }.getOrNull()
                        }
                        lock.withLock {
                            if (song != null) found[index] = song else missing += track
                            val songs = found.filterNotNull().distinctBy { it.id }
                            appState.playlist(playlist.id)?.let { appState.updatePlaylist(it.withSongs(songs)) }
                            importProgress = importProgress?.copy(
                                done = importProgress!!.done + 1,
                                matched = songs.size,
                                missing = missing.toList()
                            )
                        }
                    }
                }
            }
            importProgress = importProgress?.copy(finished = true)
            val p = importProgress!!
            toast("Imported ${p.matched} of ${p.total} songs into \"${source.name}\"")
        }
    }

    fun cancelImport() {
        importJob?.cancel()
        importJob = null
        importProgress = null
    }

    fun clearFinishedImport() {
        if (importProgress?.running != true) importProgress = null
    }

    private val backStack = ArrayDeque<Screen>()

    var screen by mutableStateOf<Screen>(Screen.Home)
        private set
    var toastMessage by mutableStateOf<Pair<String, Long>?>(null)
        private set
    var sidePanel by mutableStateOf(SidePanel.QUEUE)
    var nowPlayingOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)

    /** Non-null while the "new playlist" dialog is open; holds the song to add, if any. */
    var newPlaylistFor by mutableStateOf<NewPlaylistRequest?>(null)
        private set

    /** Bumped to ask the search field to take keyboard focus. */
    var focusSearchRequest by mutableStateOf(0)
        private set

    /** True while a text field has focus, so single-key shortcuts don't fire while typing. */
    var typing by mutableStateOf(false)

    // Search state lives here so it survives navigating away and back
    var searchQuery by mutableStateOf("")
    var searchResults by mutableStateOf<List<Song>>(emptyList())
    var searchedFor by mutableStateOf<String?>(null)

    val canGoBack: Boolean get() = backStack.isNotEmpty()

    fun navigate(target: Screen) {
        nowPlayingOpen = false
        if (target == screen) return
        backStack.addLast(screen)
        if (backStack.size > 30) backStack.removeFirst()
        screen = target
    }

    fun goBack() {
        backStack.removeLastOrNull()?.let { screen = it }
    }

    fun focusSearch() {
        if (screen !is Screen.Search) navigate(Screen.Search())
        nowPlayingOpen = false
        focusSearchRequest++
    }

    fun toast(message: String) {
        toastMessage = message to System.nanoTime()
    }

    fun toggleLike(song: Song) {
        val liked = appState.toggleLike(song)
        toast(if (liked) "Added to Liked Songs" else "Removed from Liked Songs")
    }

    fun requestNewPlaylist(withSong: Song? = null) {
        newPlaylistFor = NewPlaylistRequest(withSong)
    }

    fun dismissNewPlaylist() {
        newPlaylistFor = null
    }

    fun toggleSidePanel(panel: SidePanel) {
        val visible = appState.settings.showSidePanel
        if (visible && sidePanel == panel) {
            appState.updateSettings { it.copy(showSidePanel = false) }
        } else {
            sidePanel = panel
            appState.updateSettings { it.copy(showSidePanel = true) }
        }
    }

    data class NewPlaylistRequest(val song: Song?)
}

val LocalUi = staticCompositionLocalOf<UiActions> { error("UiActions not provided") }
