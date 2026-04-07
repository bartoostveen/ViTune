package app.vitune.providers.github.models

import app.vitune.providers.utils.SerializableIso8601Date
import app.vitune.providers.utils.SerializableUrl
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Release(
    val id: Int,
    @SerialName("node_id")
    val nodeId: String,
    val url: SerializableUrl,
    @SerialName("html_url")
    val frontendUrl: SerializableUrl,
    @SerialName("assets_url")
    val assetsUrl: SerializableUrl,
    @SerialName("tag_name")
    val tag: String,
    val name: String? = null,
    @SerialName("body")
    val markdown: String? = null,
    val draft: Boolean,
    @SerialName("prerelease")
    val preRelease: Boolean,
    @SerialName("created_at")
    val createdAt: SerializableIso8601Date,
    @SerialName("published_at")
    val publishedAt: SerializableIso8601Date? = null,
    val author: SimpleUser,
    val assets: List<Asset> = emptyList(),
    @SerialName("body_html")
    val html: String? = null,
    @SerialName("body_text")
    val text: String? = null,
    @SerialName("discussion_url")
    val discussionUrl: SerializableUrl? = null,
    val reactions: Reactions? = null
) {
    @Serializable
    data class Asset(
        val url: SerializableUrl,
        @SerialName("browser_download_url")
        val downloadUrl: SerializableUrl,
        val id: Int,
        @SerialName("node_id")
        val nodeId: String,
        val name: String,
        val label: String? = null,
        val state: State,
        @SerialName("content_type")
        val contentType: String,
        val size: Long,
        @SerialName("download_count")
        val downloads: Int,
        @SerialName("created_at")
        val createdAt: SerializableIso8601Date,
        @SerialName("updated_at")
        val updatedAt: SerializableIso8601Date,
        val uploader: SimpleUser? = null
    ) {
        @Suppress("unused")
        @Serializable
        enum class State {
            @SerialName("uploaded")
            Uploaded,

            @SerialName("open")
            Open
        }
    }
}
