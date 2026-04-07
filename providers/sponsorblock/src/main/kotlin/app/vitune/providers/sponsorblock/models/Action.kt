package app.vitune.providers.sponsorblock.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Suppress("unused")
@Serializable
enum class Action(val serialName: String) {
    @SerialName("skip")
    Skip("skip"),

    @SerialName("mute")
    Mute("mute"),

    @SerialName("full")
    Full("full"),

    @SerialName("poi")
    POI("poi"),

    @SerialName("chapter")
    Chapter("chapter")
}
