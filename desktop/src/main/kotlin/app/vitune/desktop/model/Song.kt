package app.vitune.desktop.model

import app.vitune.providers.innertube.Innertube
import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artists: String,
    val durationText: String?,
    val thumbnailSmall: String?,
    val thumbnailLarge: String?
) {
    /** Duration in milliseconds parsed from text like "3:45" or "1:02:10", or 0 if unknown. */
    val durationMs: Long
        get() = durationText
            ?.split(':')
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.fold(0L) { acc, part -> acc * 60 + part }
            ?.times(1000)
            ?: 0L
}

/** Repairs artist text saved by older versions, e.g. "A, , , B, & , C" → "A, B, C". */
fun Song.withCleanArtists(): Song = copy(
    artists = artists.split(",")
        .map { it.trim().removePrefix("&").removeSuffix("&").trim() }
        .filter { name -> name.any { it.isLetterOrDigit() } }
        .distinct()
        .joinToString(", ")
)

fun Innertube.SongItem.toSong(): Song? {
    val videoId = info?.endpoint?.videoId ?: return null
    return Song(
        id = videoId,
        title = info?.name ?: "Unknown title",
        // YouTube Music includes separators (", ", " & ") as author runs; keep only real names
        artists = authors
            ?.mapNotNull { it.name?.trim() }
            ?.filter { name -> name.any { it.isLetterOrDigit() } }
            ?.distinct()
            ?.joinToString(", ")
            .orEmpty(),
        durationText = durationText,
        thumbnailSmall = thumbnail?.size(120),
        thumbnailLarge = thumbnail?.size(544)
    )
}
