package app.vitune.providers.innertube

import app.vitune.providers.innertube.models.PlayerResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerResponseTest {

    @Test
    fun `highestQualityFormat selects opus or m4a audio format with url`() {
        val format1 = PlayerResponse.StreamingData.AdaptiveFormat(
            itag = 140,
            mimeType = "audio/mp4",
            bitrate = 128000,
            averageBitrate = 128000,
            contentLength = 3000000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = 200000,
            lastModified = 100000L,
            loudnessDb = null,
            audioSampleRate = 44100,
            url = "https://googlevideo.com/videoplayback?itag=140",
            signatureCipher = null
        )
        val format2 = PlayerResponse.StreamingData.AdaptiveFormat(
            itag = 251,
            mimeType = "audio/webm",
            bitrate = 160000,
            averageBitrate = 160000,
            contentLength = 4000000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = 200000,
            lastModified = 100000L,
            loudnessDb = null,
            audioSampleRate = 48000,
            url = "https://googlevideo.com/videoplayback?itag=251",
            signatureCipher = null
        )

        val streamingData = PlayerResponse.StreamingData(
            adaptiveFormats = listOf(format1, format2),
            expiresInSeconds = 21600
        )

        val selected = streamingData.highestQualityFormat
        assertNotNull(selected)
        assertEquals(251, selected?.itag)
        assertEquals("https://googlevideo.com/videoplayback?itag=251", selected?.url)
    }

    @Test
    fun `highestQualityFormat handles empty formats gracefully without exception`() {
        val streamingData = PlayerResponse.StreamingData(
            adaptiveFormats = emptyList(),
            expiresInSeconds = 21600
        )

        val selected = streamingData.highestQualityFormat
        assertNull(selected)
    }

    @Test
    fun `highestQualityFormat falls back to formats list if adaptiveFormats is null`() {
        val format = PlayerResponse.StreamingData.AdaptiveFormat(
            itag = 18,
            mimeType = "video/mp4",
            bitrate = 500000,
            averageBitrate = 500000,
            contentLength = 10000000,
            audioQuality = "AUDIO_QUALITY_MEDIUM",
            approxDurationMs = 200000,
            lastModified = 100000L,
            loudnessDb = null,
            audioSampleRate = 44100,
            url = "https://googlevideo.com/videoplayback?itag=18",
            signatureCipher = null
        )

        val streamingData = PlayerResponse.StreamingData(
            formats = listOf(format),
            adaptiveFormats = null,
            expiresInSeconds = 21600
        )

        val selected = streamingData.highestQualityFormat
        assertNotNull(selected)
        assertEquals(18, selected?.itag)
    }
}
