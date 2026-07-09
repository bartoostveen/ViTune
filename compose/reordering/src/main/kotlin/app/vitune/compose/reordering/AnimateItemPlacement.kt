package app.vitune.compose.reordering

import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

@Suppress("ModifierComposed") // TODO
context(scope: LazyItemScope)
fun Modifier.animateItemPlacement(reorderingState: ReorderingState) = composed {
    var suppress by remember { mutableStateOf(false) }

    LaunchedEffect(reorderingState.draggingIndex) {
        if (reorderingState.draggingIndex == -1) repeat(2) { withFrameNanos {} }
        suppress = reorderingState.draggingIndex != -1
    }

    if (suppress) this else with(scope) {
        animateItem(
            fadeInSpec = null,
            fadeOutSpec = null
        )
    }
}
