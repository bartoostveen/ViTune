package app.vitune.providers.sponsorblock.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class Category(val serialName: String) {
    @SerialName("sponsor")
    Sponsor("sponsor"),

    @SerialName("selfpromo")
    SelfPromotion("selfpromo"),

    @SerialName("interaction")
    Interaction("interaction"),

    @SerialName("intro")
    Intro("intro"),

    @SerialName("outro")
    Outro("outro"),

    @SerialName("preview")
    Preview("preview"),

    @SerialName("music_offtopic")
    OfftopicMusic("music_offtopic"),

    @SerialName("filler")
    Filler("filler"),

    @SerialName("poi_highlight")
    PoiHighlight("poi_highlight")
}
