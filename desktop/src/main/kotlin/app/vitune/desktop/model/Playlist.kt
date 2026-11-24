package app.vitune.desktop.model

import java.time.LocalDateTime
import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String = "",
    val songs: List<Song> = emptyList(),
    val createdAtMs: Long = System.currentTimeMillis(),
    val thumbnailUrl: String? = null
) {
    companion object {
        fun newPlaylist(name: String, description: String = ""): Playlist {
            return Playlist(
                id = java.util.UUID.randomUUID().toString(),
                name = name,
                description = description,
                songs = emptyList()
            )
        }
    }

    fun withSongs(songs: List<Song>) = copy(songs = songs)
    fun withName(name: String) = copy(name = name)
    fun withDescription(description: String) = copy(description = description)
}
