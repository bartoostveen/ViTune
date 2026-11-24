package app.vitune.core.ui

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.material.ripple.RippleAlpha
import androidx.compose.material.ripple.createRippleModifierNode
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

private data class AppearanceRipple(
    val color: Color,
    val alpha: RippleAlpha,
    val bounded: Boolean = true,
    val radius: Dp = Dp.Unspecified
) : IndicationNodeFactory {
    override fun create(
        interactionSource: InteractionSource
    ) = createRippleModifierNode(
        interactionSource = interactionSource,
        bounded = bounded,
        radius = radius,
        color = { color },
        rippleAlpha = { alpha }
    )
}

@Composable
fun rippleConfiguration(
    appearance: Appearance = LocalAppearance.current
): IndicationNodeFactory {
    val (colorPalette) = appearance

    val rippleColor = remember(colorPalette.text, colorPalette.isDark) {
        if (colorPalette.isDark && colorPalette.text.luminance() < 0.5f) Color.White
        else colorPalette.text
    }

    val rippleAlpha = remember(colorPalette.text) {
        when {
            colorPalette.isDark -> DarkThemeRippleAlpha
            colorPalette.text.luminance() > 0.5f -> LightThemeHighContrastRippleAlpha
            else -> LightThemeLowContrastRippleAlpha
        }
    }

    return remember(rippleColor, rippleAlpha) {
        AppearanceRipple(
            color = rippleColor,
            alpha = rippleAlpha
        )
    }
}

private val LightThemeHighContrastRippleAlpha = RippleAlpha(
    pressedAlpha = 0.24f,
    focusedAlpha = 0.24f,
    draggedAlpha = 0.16f,
    hoveredAlpha = 0.08f
)

private val LightThemeLowContrastRippleAlpha = RippleAlpha(
    pressedAlpha = 0.12f,
    focusedAlpha = 0.12f,
    draggedAlpha = 0.08f,
    hoveredAlpha = 0.04f
)

private val DarkThemeRippleAlpha = RippleAlpha(
    pressedAlpha = 0.10f,
    focusedAlpha = 0.12f,
    draggedAlpha = 0.08f,
    hoveredAlpha = 0.04f
)
