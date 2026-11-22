package app.vitune.desktop.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Brand palette: near-black surfaces with a violet → pink accent. */
object Palette {
    val Violet = Color(0xFF8B5CF6)
    val Pink = Color(0xFFEC4899)
    val Cyan = Color(0xFF22D3EE)

    val Background = Color(0xFF07070A)
    val Panel = Color(0xFF111116)
    val PanelRaised = Color(0xFF1A1A22)
    val PanelHover = Color(0xFF23232D)
    val Outline = Color(0xFF2A2A35)

    val TextPrimary = Color(0xFFF4F4F6)
    val TextSecondary = Color(0xFFA0A0AE)
    val TextMuted = Color(0xFF6B6B7A)

    val AccentGradient = Brush.linearGradient(listOf(Violet, Pink))

    /** Colourful tiles for "browse" genres and playlist placeholders. */
    val TileColors = listOf(
        Color(0xFF8B5CF6), Color(0xFFEC4899), Color(0xFFF97316), Color(0xFF10B981),
        Color(0xFF0EA5E9), Color(0xFFE11D48), Color(0xFF6366F1), Color(0xFFEAB308),
        Color(0xFF14B8A6), Color(0xFFA855F7), Color(0xFF3B82F6), Color(0xFFF43F5E)
    )

    fun tileColorFor(key: String): Color = TileColors[(key.hashCode() and 0x7fffffff) % TileColors.size]
}

private val ColorScheme = darkColorScheme(
    primary = Palette.Violet,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1F5E),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Palette.Pink,
    onSecondary = Color.White,
    tertiary = Palette.Cyan,
    background = Palette.Background,
    onBackground = Palette.TextPrimary,
    surface = Palette.Panel,
    onSurface = Palette.TextPrimary,
    surfaceVariant = Palette.PanelRaised,
    onSurfaceVariant = Palette.TextSecondary,
    surfaceContainerLowest = Palette.Background,
    surfaceContainerLow = Palette.Panel,
    surfaceContainer = Palette.Panel,
    surfaceContainerHigh = Palette.PanelRaised,
    surfaceContainerHighest = Palette.PanelHover,
    outline = Palette.Outline,
    outlineVariant = Palette.Outline,
    error = Color(0xFFFF6B81),
    errorContainer = Color(0xFF4A1622),
    onErrorContainer = Color(0xFFFFD9DE)
)

private val AppTypography = Typography().let { base ->
    base.copy(
        displaySmall = base.displaySmall.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
        headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(letterSpacing = 1.sp)
    )
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ColorScheme, typography = AppTypography, content = content)
}

val SectionLabelStyle = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)

fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

/** "1 hr 12 min" style total length for a list of songs. */
fun formatTotalDuration(totalMs: Long): String {
    val minutes = totalMs / 60_000
    return when {
        minutes >= 60 -> "${minutes / 60} hr ${minutes % 60} min"
        minutes > 0 -> "$minutes min"
        else -> "${(totalMs / 1000).coerceAtLeast(0)} sec"
    }
}
