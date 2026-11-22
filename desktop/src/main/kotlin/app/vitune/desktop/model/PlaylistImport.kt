package app.vitune.desktop.model

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.math.abs

/** A track as listed by Spotify (or a CSV export), before it is found on YouTube Music. */
data class ExternalTrack(val title: String, val artists: String, val durationMs: Long = 0)

data class ExternalPlaylist(
    val name: String,
    val description: String,
    val coverUrl: String?,
    val tracks: List<ExternalTrack>
)

/**
 * Brings Spotify playlists and albums into the app. Spotify's public embed page lists up to
 * 100 tracks without needing an account or API key; longer playlists can be imported from a
 * CSV export (e.g. made with exportify.net).
 */
object PlaylistImport {
    /** Largest playlist Spotify's embed page returns; longer ones are cut off there. */
    const val SPOTIFY_LINK_LIMIT = 100

    private val json = Json { ignoreUnknownKeys = true }
    private val http by lazy {
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build()
    }

    data class SpotifyRef(val type: String, val id: String)

    /**
     * Understands links like `https://open.spotify.com/playlist/ID?si=…`,
     * `https://open.spotify.com/intl-en/album/ID` and `spotify:playlist:ID`.
     */
    fun parseSpotifyLink(input: String): SpotifyRef? {
        val text = input.trim()
        Regex("""spotify:(playlist|album|track):([A-Za-z0-9]+)""").find(text)?.let {
            return SpotifyRef(it.groupValues[1], it.groupValues[2])
        }
        Regex("""open\.spotify\.com/(?:[a-z-]+/)?(?:embed/)?(playlist|album|track)/([A-Za-z0-9]+)""").find(text)?.let {
            return SpotifyRef(it.groupValues[1], it.groupValues[2])
        }
        return null
    }

    suspend fun fetchSpotify(ref: SpotifyRef): ExternalPlaylist = withContext(Dispatchers.IO) {
        val request = HttpRequest.newBuilder(URI.create("https://open.spotify.com/embed/${ref.type}/${ref.id}"))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .header("Accept-Language", "en")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        when (response.statusCode()) {
            200 -> Unit
            404 -> error("Spotify couldn't find that ${ref.type}. Is it public?")
            else -> error("Spotify answered with HTTP ${response.statusCode()}")
        }

        val data = Regex("""<script id="__NEXT_DATA__"[^>]*>(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(response.body())?.groupValues?.get(1)
            ?: error("Couldn't read the Spotify page. Spotify may have changed it.")
        val entity = json.parseToJsonElement(data)
            .path("props", "pageProps", "state", "data", "entity") as? JsonObject
            ?: error("This ${ref.type} is private or unavailable.")

        val name = entity.string("name") ?: entity.string("title") ?: "Imported from Spotify"
        // Playlists keep the cover in coverArt.sources, albums and tracks in visualIdentity.image
        val cover = listOf(entity.path("coverArt", "sources"), entity.path("visualIdentity", "image"))
            .flatMap { (it as? JsonArray).orEmpty() }
            .mapNotNull { it as? JsonObject }
            .filter { it.string("url") != null }
            .maxByOrNull { img ->
                (img["maxWidth"] ?: img["width"])?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull } ?: 0L
            }
            ?.string("url")
        val entityArtists = (entity["artists"] as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.string("name") }
            ?.joinToString(", ")

        val tracks = (entity["trackList"] as? JsonArray).orEmpty().mapNotNull { item ->
            val track = item as? JsonObject ?: return@mapNotNull null
            val title = track.string("title")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ExternalTrack(
                title = title,
                // Spotify separates artists with ", "
                artists = track.string("subtitle").orEmpty().replace(' ', ' '),
                durationMs = track["duration"]?.jsonPrimitive?.longOrNull ?: 0
            )
        }.ifEmpty {
            // A single-track link: the entity itself is the track
            if (ref.type == "track") listOf(
                ExternalTrack(
                    name,
                    entityArtists ?: entity.string("subtitle").orEmpty().replace(' ', ' '),
                    entity["duration"]?.jsonPrimitive?.longOrNull ?: 0
                )
            ) else emptyList()
        }
        if (tracks.isEmpty()) error("That ${ref.type} has no tracks we can read.")

        val by = (entity.string("subtitle") ?: entityArtists)?.replace(' ', ' ')
        ExternalPlaylist(
            name = name,
            description = buildString {
                append("Imported from Spotify")
                if (!by.isNullOrBlank()) append(" • ").append(if (ref.type == "playlist") "by $by" else by)
            },
            coverUrl = cover,
            tracks = tracks
        )
    }

    /**
     * Reads a CSV export. Works with Exportify ("Track Name", "Artist Name(s)", "Duration (ms)")
     * and other exports that have title/artist columns.
     */
    fun parseCsv(text: String, fallbackName: String): ExternalPlaylist {
        val rows = parseCsvRows(text.removePrefix("﻿")).filter { row -> row.any { it.isNotBlank() } }
        if (rows.size < 2) error("The CSV file has no songs in it.")
        val header = rows.first().map { it.trim().lowercase() }

        fun column(vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 } }
            ?: header.indexOfFirst { h -> names.any { h.contains(it) } }.takeIf { it >= 0 }

        val titleCol = column("track name", "title", "song", "name", "track")
            ?: error("Couldn't find a song title column in the CSV.")
        val artistCol = column("artist name(s)", "artist names", "artists", "artist")
        val durationCol = column("duration (ms)", "duration_ms", "track duration (ms)")

        val tracks = rows.drop(1).mapNotNull { row ->
            val title = row.getOrNull(titleCol)?.trim().orEmpty()
            if (title.isEmpty()) return@mapNotNull null
            ExternalTrack(
                title = title,
                artists = artistCol?.let { row.getOrNull(it) }.orEmpty().replace(";", ", ").trim(),
                durationMs = durationCol?.let { row.getOrNull(it)?.trim()?.toLongOrNull() } ?: 0
            )
        }
        if (tracks.isEmpty()) error("The CSV file has no songs in it.")
        return ExternalPlaylist(fallbackName, "Imported from CSV", null, tracks)
    }

    /** Finds the YouTube Music song that best matches a Spotify track, or null if nothing fits. */
    suspend fun findOnYouTubeMusic(track: ExternalTrack): Song? {
        val mainArtist = track.artists.split(",", "&").first().trim()
        val cleanTitle = track.title
            .replace(Regex("""\s*[(\[](feat|ft|with)\.?\s[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(remaster(ed)?|\d{4} remaster).*$""", RegexOption.IGNORE_CASE), "")
            .trim()
        val results = MusicRepository.searchSongs("$cleanTitle $mainArtist".trim()).getOrNull().orEmpty().take(8)
        if (results.isEmpty()) return null

        fun normalize(s: String) = s.lowercase().replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        val wantTitle = normalize(cleanTitle)
        val wantArtist = normalize(mainArtist)

        return results.maxByOrNull { song ->
            var score = 0.0
            val title = normalize(song.title)
            val artists = normalize(song.artists)
            if (title == wantTitle) score += 4 else if (title.contains(wantTitle) || wantTitle.contains(title)) score += 2.5
            if (wantArtist.isNotEmpty() && artists.contains(wantArtist)) score += 3
            if (track.durationMs > 0 && song.durationMs > 0) {
                val diff = abs(track.durationMs - song.durationMs)
                score += when {
                    diff < 3_000 -> 2.0
                    diff < 10_000 -> 1.0
                    diff > 60_000 -> -2.0
                    else -> 0.0
                }
            }
            // Earlier search results are usually the better match
            score - results.indexOf(song) * 0.15
        }
    }

    private fun JsonElement.path(vararg keys: String): JsonElement? =
        keys.fold(this as JsonElement?) { node, key -> (node as? JsonObject)?.get(key) }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.takeIf { it.isString }?.content

    /** Minimal RFC 4180 CSV reader (quoted fields, escaped quotes, newlines inside quotes). */
    private fun parseCsvRows(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row.add(field.toString()); field.clear() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row.add(field.toString()); field.clear()
                    rows.add(row); row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row.add(field.toString())
            rows.add(row)
        }
        return rows
    }
}
