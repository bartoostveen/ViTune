package app.vitune.desktop.model

import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.NextBody
import app.vitune.providers.innertube.models.bodies.SearchBody
import app.vitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.vitune.providers.innertube.requests.nextPage
import app.vitune.providers.innertube.requests.searchPage
import app.vitune.providers.innertube.requests.searchSuggestions
import app.vitune.providers.innertube.utils.from
import app.vitune.providers.lrclib.LrcLib
import app.vitune.providers.lrclib.LrcParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/** Lyrics for a song: either time-stamped lines or plain text. */
sealed interface SongLyrics {
    data class Synced(val lines: List<Line>) : SongLyrics {
        data class Line(val timeMs: Long, val text: String)
    }

    data class Plain(val text: String) : SongLyrics
    data object NotFound : SongLyrics
}

/** Thin wrapper around ViTune's YouTube Music (Innertube) and LrcLib providers. */
object MusicRepository {
    private val lyricsCache = ConcurrentHashMap<String, SongLyrics>()
    private val radioCache = ConcurrentHashMap<String, List<Song>>()

    suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        Innertube.searchPage(
            body = SearchBody(query = query, params = Innertube.SearchFilter.Song.value),
            fromMusicShelfRendererContent = Innertube.SongItem.Companion::from
        )
            ?.map { page -> page?.items.orEmpty().mapNotNull { it.toSong() }.distinctBy { it.id } }
            ?: Result.success(emptyList())
    }

    suspend fun suggestions(input: String): List<String> = withContext(Dispatchers.IO) {
        Innertube.searchSuggestions(SearchSuggestionsBody(input = input))
            ?.getOrNull()
            .orEmpty()
            .take(6)
    }

    /** Songs YouTube Music would play after [videoId] (its "radio"/autoplay list). */
    suspend fun radio(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        radioCache[videoId]?.let { return@withContext it }
        Innertube.nextPage(NextBody(videoId = videoId))
            ?.getOrNull()
            ?.itemsPage
            ?.items
            .orEmpty()
            .mapNotNull { it.toSong() }
            .distinctBy { it.id }
            .also { if (it.isNotEmpty()) radioCache[videoId] = it }
    }

    suspend fun lyrics(song: Song, durationMs: Long): SongLyrics = withContext(Dispatchers.IO) {
        lyricsCache[song.id]?.let { return@withContext it }

        val artist = song.artists.split(",", "&", " feat.", " ft.").first().trim()
        val title = song.title
            .replace(Regex("""\s*[(\[](official|lyric|audio|video|visualizer|hd|4k)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .trim()
        val duration = durationMs.coerceAtLeast(1).milliseconds

        val synced = LrcLib.bestLyrics(artist = artist, title = title, duration = duration, synced = true)
            ?.getOrNull()
        val result = if (synced != null) {
            parseSynced(synced.text)
        } else {
            LrcLib.bestLyrics(artist = artist, title = title, duration = duration, synced = false)
                ?.getOrNull()
                ?.text
                ?.takeIf { it.isNotBlank() }
                ?.let { SongLyrics.Plain(it.trim()) }
                ?: SongLyrics.NotFound
        }
        result.also { lyricsCache[song.id] = it }
    }

    private fun parseSynced(raw: String): SongLyrics {
        val lines = LrcParser.parse(raw)
            ?.filterIsInstance<LrcParser.Line.Lyric>()
            ?.map { SongLyrics.Synced.Line(it.timestamp, it.line) }
            ?.sortedBy { it.timeMs }
            .orEmpty()
        return if (lines.isEmpty()) SongLyrics.Plain(raw) else SongLyrics.Synced(lines)
    }
}
