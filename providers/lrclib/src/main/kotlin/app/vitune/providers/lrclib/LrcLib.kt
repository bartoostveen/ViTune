package app.vitune.providers.lrclib

import app.vitune.providers.lrclib.models.Track
import app.vitune.providers.lrclib.models.bestMatchingFor
import app.vitune.providers.utils.runCatchingCancellable
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private const val AGENT = "ViTune (https://github.com/25huizengek1/ViTune)"

object LrcLib {
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    }
                )
            }

            defaultRequest {
                url("https://lrclib.net")
                header("Lrclib-Client", AGENT)
            }

            install(UserAgent) {
                agent = AGENT
            }

            expectSuccess = true
        }
    }

    private suspend fun queryLyrics(
        artist: String,
        title: String,
        album: String? = null
    ) = client.get("/api/search") {
        parameter("track_name", title)
        parameter("artist_name", artist)
        if (album != null) parameter("album_name", album)
    }.body<List<Track>>()

    private suspend fun queryLyrics(query: String) = client.get("/api/search") {
        parameter("q", query)
    }.body<List<Track>>()

    suspend fun lyrics(
        artist: String,
        title: String,
        album: String? = null,
        synced: Boolean = true
    ) = runCatchingCancellable {
        queryLyrics(
            artist = artist,
            title = title,
            album = album
        ).let { list ->
            list.filter { if (synced) it.syncedLyrics != null else it.plainLyrics != null }
        }
    }

    suspend fun lyrics(
        query: String,
        synced: Boolean = true
    ) = runCatchingCancellable {
        queryLyrics(query = query).let { list ->
            list.filter { if (synced) it.syncedLyrics != null else it.plainLyrics != null }
        }
    }

    suspend fun bestLyrics(
        artist: String,
        title: String,
        duration: Duration,
        album: String? = null,
        synced: Boolean = true
    ) = lyrics(
        artist = artist,
        title = title,
        album = album,
        synced = synced
    )?.mapCatching { tracks ->
        tracks.bestMatchingFor(title, duration)
            ?.let { if (synced) it.syncedLyrics else it.plainLyrics }
            ?.let {
                Lyrics(
                    text = it,
                    synced = synced
                )
            }
    }

    data class Lyrics(
        val text: String,
        val synced: Boolean
    )
}

object LrcParser {
    private val lyricRegex = "^\\[(\\d{2,}):(\\d{2}).(\\d{2,3})](.*)$".toRegex()
    private val metadataRegex = "^\\[(.+?):(.*?)]$".toRegex()

    sealed interface Line {
        val raw: String?

        data object Invalid : Line {
            override val raw: String? = null
        }

        data class Metadata(
            val key: String,
            val value: String,
            override val raw: String
        ) : Line

        data class Lyric(
            val timestamp: Long,
            val line: String,
            override val raw: String
        ) : Line
    }

    private fun <T> Result<T>.handleError(logging: Boolean) = onFailure {
        when {
            it is CancellationException -> throw it
            logging -> it.printStackTrace()
        }
    }

    fun parse(
        raw: String,
        logging: Boolean = false
    ) = raw.lines().mapNotNull { line ->
        line.substringBefore('#').trim().takeIf { it.isNotBlank() }
    }.map { line ->
        runCatching {
            val results = lyricRegex.find(line)?.groups ?: error("Invalid lyric")
            val (minutes, seconds, millis, lyric) = results.drop(1).take(4).mapNotNull { it?.value }
            val duration = minutes.toInt().minutes +
                seconds.toInt().seconds +
                millis.padEnd(length = 3, padChar = '0').toInt().milliseconds

            Line.Lyric(
                timestamp = duration.inWholeMilliseconds,
                line = lyric.trim(),
                raw = line
            )
        }.handleError(logging).recoverCatching {
            val results = metadataRegex.find(line)?.groups ?: error("Invalid metadata")
            val (key, value) = results.drop(1).take(2).mapNotNull { it?.value }

            Line.Metadata(
                key = key.trim(),
                value = value.trim(),
                raw = line
            )
        }.handleError(logging).getOrDefault(Line.Invalid)
    }.takeIf { lrc -> lrc.isNotEmpty() && !lrc.all { it == Line.Invalid } }

    @Suppress("unused")
    data class LrcFile(
        val metadata: Map<String, String>,
        val lines: Map<Long, String>,
        val errors: Int
    ) {
        val title get() = metadata["ti"]
        val artist get() = metadata["ar"]
        val album get() = metadata["al"]
        val author get() = metadata["au"]
        val duration
            get() = metadata["length"]?.runCatching {
                val (minutes, seconds) = split(":", limit = 2)
                minutes.toInt().minutes + seconds.toInt().seconds
            }?.getOrNull()
        val fileAuthor get() = metadata["by"]
        val offset get() = metadata["offset"]?.removePrefix("+")?.toIntOrNull()?.milliseconds
        val tool get() = metadata["re"] ?: metadata["tool"]
        val version get() = metadata["ve"]
    }
}

fun List<LrcParser.Line>.toLrcFile(): LrcParser.LrcFile {
    val metadata = mutableMapOf<String, String>()
    val lines = mutableMapOf(0L to "")
    var errors = 0

    forEach {
        when (it) {
            LrcParser.Line.Invalid -> errors++
            is LrcParser.Line.Lyric -> lines += it.timestamp to it.line
            is LrcParser.Line.Metadata -> metadata += it.key to it.value
        }
    }

    return LrcParser.LrcFile(
        metadata = metadata,
        lines = lines,
        errors = errors
    )
}
