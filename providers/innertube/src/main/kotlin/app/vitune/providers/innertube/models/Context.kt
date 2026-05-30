package app.vitune.providers.innertube.models

import io.ktor.client.request.headers
import io.ktor.http.HttpMessageBuilder
import io.ktor.http.parameters
import io.ktor.http.userAgent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import java.util.Locale

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
    val user: User? = User()
) {
    @Serializable
    data class Client(
        @Transient
        val clientId: Int = 0,
        val clientName: String,
        val clientVersion: String,
        val platform: String? = null,
        val hl: String = "en",
        val gl: String = "US",
        @SerialName("visitorData")
        val defaultVisitorData: String = DEFAULT_VISITOR_DATA,
        val androidSdkVersion: Int? = null,
        val userAgent: String? = null,
        val referer: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val osName: String? = null,
        val osVersion: String? = null,
        val acceptHeader: String? = null,
        val timeZone: String? = "UTC",
        val utcOffsetMinutes: Int? = 0,
        @Transient
        val apiKey: String? = null,
        @Transient
        val music: Boolean = false
    ) {
        @Serializable
        data class Configuration(
            @SerialName("PLAYER_JS_URL")
            val playerUrl: String? = null,
            @SerialName("WEB_PLAYER_CONTEXT_CONFIGS")
            val contextConfigs: Map<String, ContextConfig>? = null,
            @SerialName("VISITOR_DATA")
            val visitorData: String? = null,
            @SerialName("INNERTUBE_CONTEXT")
            val innertubeContext: Context
        ) {
            @Serializable
            data class ContextConfig(
                val jsUrl: String? = null
            )
        }

        @Transient
        private var ytcfg: Configuration? = null

        val visitorData
            get() = ytcfg?.visitorData
                ?: ytcfg?.innertubeContext?.client?.defaultVisitorData
                ?: defaultVisitorData

        context(builder: HttpMessageBuilder)
        fun apply() = with(builder) {
            userAgent?.let { userAgent(it) }

            headers {
                referer?.let { set("Referer", it) }
                set("X-Youtube-Bootstrap-Logged-In", "false")
                set("X-YouTube-Client-Name", clientId.toString())
                set("X-YouTube-Client-Version", clientVersion)
                apiKey?.let { set("X-Goog-Api-Key", it) }
                set("X-Goog-Visitor-Id", visitorData)
            }

            parameters {
                apiKey?.let { set("key", it) }
            }
        }
    }

    @Serializable
    data class ThirdParty(
        val embedUrl: String
    )

    @Serializable
    data class User(
        val lockedSafetyMode: Boolean = false
    )

    context(_: HttpMessageBuilder)
    fun apply() = client.apply()

    companion object {
        private val Context.withLang: Context
            get() {
                val locale = Locale.getDefault()

                return copy(
                    client = client.copy(
                        hl = locale
                            .toLanguageTag()
                            .replace("-Hant", "")
                            .takeIf { it in validLanguageCodes } ?: "en",
                        gl = locale
                            .country
                            .takeIf { it in validCountryCodes } ?: "US"
                    )
                )
            }
        const val DEFAULT_VISITOR_DATA = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"

        val DefaultWeb get() = DefaultWebNoLang.withLang

        val DefaultWebNoLang = Context(
            client = Client(
                clientId = 67,
                clientName = "WEB_REMIX",
                clientVersion = "1.20220606.03.00",
                platform = "DESKTOP",
                userAgent = UserAgents.DESKTOP,
                referer = "https://music.youtube.com/",
                music = true
            )
        )

        val DefaultIOS = Context(
            client = Client(
                clientId = 5,
                clientName = "IOS",
                clientVersion = "20.03.02",
                deviceMake = "Apple",
                deviceModel = "iPhone16,2",
                osName = "iPhone",
                osVersion = "18.2.1.22C161",
                acceptHeader = "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                userAgent = UserAgents.IOS,
                apiKey = "AIzaSyB-63vPrdThhKuerbB2N_l7Kwwcxj6yUAc",
                music = false
            )
        )

        val DefaultAndroidMusic = Context(
            client = Client(
                clientId = 21,
                clientName = "ANDROID_MUSIC",
                clientVersion = "7.27.52",
                platform = "MOBILE",
                osVersion = "11",
                androidSdkVersion = 30,
                userAgent = UserAgents.ANDROID_MUSIC,
                apiKey = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
                music = true
            )
        )

        val DefaultTV = Context(
            client = Client(
                clientId = 7,
                clientName = "TVHTML5",
                clientVersion = "7.20241201.18.00",
                platform = "TV",
                userAgent = UserAgents.TV,
                referer = "https://www.youtube.com/",
                music = false
            )
        )
    }
}

// @formatter:off
@Suppress("MaximumLineLength")
val validLanguageCodes =
    listOf("af", "az", "id", "ms", "ca", "cs", "da", "de", "et", "en-GB", "en", "es", "es-419", "eu", "fil", "fr", "fr-CA", "gl", "hr", "zu", "is", "it", "sw", "lt", "hu", "nl", "nl-NL", "no", "or", "uz", "pl", "pt-PT", "pt", "ro", "sq", "sk", "sl", "fi", "sv", "bo", "vi", "tr", "bg", "ky", "kk", "mk", "mn", "ru", "sr", "uk", "el", "hy", "iw", "ur", "ar", "fa", "ne", "mr", "hi", "bn", "pa", "gu", "ta", "te", "kn", "ml", "si", "th", "lo", "my", "ka", "am", "km", "zh-CN", "zh-TW", "zh-HK", "ja", "ko")

@Suppress("MaximumLineLength")
val validCountryCodes =
    listOf("DZ", "AR", "AU", "AT", "AZ", "BH", "BD", "BY", "BE", "BO", "BA", "BR", "BG", "KH", "CA", "CL", "HK", "CO", "CR", "HR", "CY", "CZ", "DK", "DO", "EC", "EG", "SV", "EE", "FI", "FR", "GE", "DE", "GH", "GR", "GT", "HN", "HU", "IS", "IN", "ID", "IQ", "IE", "IL", "IT", "JM", "JP", "JO", "KZ", "KE", "KR", "KW", "LA", "LV", "LB", "LY", "LI", "LT", "LU", "MK", "MY", "MT", "MX", "ME", "MA", "NP", "NL", "NZ", "NI", "NG", "NO", "OM", "PK", "PA", "PG", "PY", "PE", "PH", "PL", "PT", "PR", "QA", "RO", "RU", "SA", "SN", "RS", "SG", "SK", "SI", "ZA", "ES", "LK", "SE", "CH", "TW", "TZ", "TH", "TN", "TR", "UG", "UA", "AE", "GB", "US", "UY", "VE", "VN", "YE", "ZW")
// @formatter:on

@Suppress("MaximumLineLength")
object UserAgents {
    const val DESKTOP =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"
    const val ANDROID_MUSIC =
        "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 11) gzip"
    const val IOS = "com.google.ios.youtube/20.03.02 (iPhone16,2; U; CPU iOS 18_2_1 like Mac OS X;)"
    const val TV = "Mozilla/5.0 (ChromiumStylePlatform) Cobalt/Version"
}
