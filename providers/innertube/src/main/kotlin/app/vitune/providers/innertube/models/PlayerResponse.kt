package app.vitune.providers.innertube.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient

@Serializable
data class PlayerResponse(
    val playabilityStatus: PlayabilityStatus?,
    val playerConfig: PlayerConfig?,
    val streamingData: StreamingData?,
    val videoDetails: VideoDetails?,
    @Transient
    val context: Context? = null,
    @Transient
    val cpn: String? = null
) {
    @Serializable
    data class PlayabilityStatus(
        val status: String? = null,
        val reason: String? = null,
        val errorScreen: ErrorScreen? = null
    )

    @Serializable
    data class PlayerConfig(
        val audioConfig: AudioConfig?
    ) {
        @Serializable
        data class AudioConfig(
            internal val loudnessDb: Double?,
            internal val perceptualLoudnessDb: Double?
        ) {
            // For music clients only
            val normalizedLoudnessDb: Float?
                get() = (loudnessDb ?: perceptualLoudnessDb?.plus(7))?.plus(7)?.toFloat()
        }
    }

    @Serializable
    data class StreamingData(
        val formats: List<AdaptiveFormat>? = null,
        val adaptiveFormats: List<AdaptiveFormat>? = null,
        val expiresInSeconds: Long? = null
    ) {
        val highestQualityFormat: AdaptiveFormat?
            get() = (adaptiveFormats ?: formats)?.filter { it.url != null || it.signatureCipher != null }
                ?.let { formatsList ->
                    formatsList.findLast { it.itag == 251 || it.itag == 140 }
                        ?: formatsList.maxByOrNull { it.bitrate ?: 0L }
                }

        @Serializable
        data class AdaptiveFormat(
            val itag: Int,
            val mimeType: String,
            val bitrate: Long?,
            val averageBitrate: Long?,
            val contentLength: Long?,
            val audioQuality: String?,
            val approxDurationMs: Long?,
            val lastModified: Long?,
            val loudnessDb: Double?,
            val audioSampleRate: Int?,
            val url: String?,
            val signatureCipher: String?
        )
    }

    @Serializable
    data class VideoDetails(
        val videoId: String?
    )
}

@Serializable
data class ErrorScreen(
    val playerErrorMessageRenderer: PlayerErrorMessageRenderer? = null
) {
    @Serializable
    data class PlayerErrorMessageRenderer(
        val subreason: Runs? = null
    )
}
