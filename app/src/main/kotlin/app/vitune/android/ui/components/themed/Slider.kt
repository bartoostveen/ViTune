package app.vitune.android.ui.components.themed

import androidx.annotation.IntRange
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.surface
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun Slider(
    state: Float,
    setState: (Float) -> Unit,
    onSlideComplete: () -> Unit,
    range: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier,
    @IntRange(from = 0) steps: Int = 0,
    showTicks: Boolean = steps != 0
) {
    val (colorPalette) = LocalAppearance.current
    val sliderState = key(steps, range) {
        rememberSliderState(
            value = state,
            steps = steps,
            trackRange = range
        )
    }

    LaunchedEffect(state) {
        sliderState.value = state
    }

    androidx.compose.material3.Slider(
        state = sliderState,
        onValueChange = setState,
        onValueChangeFinished = onSlideComplete,
        modifier = modifier,
        colors = SliderDefaults.colors(
            thumbColor = colorPalette.onAccent,
            activeTrackColor = colorPalette.accent,
            inactiveTrackColor = colorPalette.surface.copy(alpha = 0.75f),
            activeTickColor = if (showTicks) colorPalette.surface else Color.Transparent,
            inactiveTickColor = if (showTicks) colorPalette.accent else Color.Transparent
        )
    )
}
