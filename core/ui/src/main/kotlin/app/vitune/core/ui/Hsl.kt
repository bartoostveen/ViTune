package app.vitune.core.ui

import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

@Suppress("NOTHING_TO_INLINE")
@JvmInline
value class Hsl(@PublishedApi internal val raw: FloatArray) {
    @Suppress("unused") // not unused
    object Saver : androidx.compose.runtime.saveable.Saver<Hsl, FloatArray> {
        override fun restore(value: FloatArray) = value.hsl
        override fun SaverScope.save(value: Hsl) = value.raw
    }

    init {
        assert(raw.size == 3) { "Invalid Hsl value! Expected size: 3, actual size: ${raw.size}" }
    }

    inline val hue get() = raw[0]
    inline val saturation get() = raw[1]
    inline val lightness get() = raw[2]

    inline val color
        get() = Color.hsl(
            hue = hue,
            saturation = saturation,
            lightness = lightness
        )

    inline operator fun component1() = hue
    inline operator fun component2() = saturation
    inline operator fun component3() = lightness
}

val FloatArray.hsl get() = Hsl(raw = this)
val Color.hsl
    get() = FloatArray(3)
        .apply { ColorUtils.colorToHSL(toArgb(), this) }
        .hsl
