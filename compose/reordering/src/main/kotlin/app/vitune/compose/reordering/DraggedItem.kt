package app.vitune.compose.reordering

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

fun Modifier.draggedItem(
    reorderingState: ReorderingState,
    index: Int,
    draggedElevation: Dp = 4.dp
): Modifier = when (reorderingState.draggingIndex) {
    -1 -> this

    index -> offset {
        when (reorderingState.lazyListState.layoutInfo.orientation) {
            Orientation.Vertical -> IntOffset(0, reorderingState.offset.value)
            Orientation.Horizontal -> IntOffset(reorderingState.offset.value, 0)
        }
    }.zIndex(1f)

    else -> offset {
        val offset = when (index) {
            in reorderingState.indexesToAnimate -> reorderingState.indexesToAnimate.getValue(index).value
            in (reorderingState.draggingIndex + 1)..reorderingState.reachedIndex -> -reorderingState.draggingItemSize
            in reorderingState.reachedIndex..<reorderingState.draggingIndex -> reorderingState.draggingItemSize
            else -> 0
        }
        when (reorderingState.lazyListState.layoutInfo.orientation) {
            Orientation.Vertical -> IntOffset(0, offset)
            Orientation.Horizontal -> IntOffset(offset, 0)
        }
    }
}.composed {
    val container = LocalPinnableContainer.current
    val elevation by animateDpAsState(
        targetValue = if (reorderingState.draggingIndex == index) draggedElevation else 0.dp,
        label = ""
    )

    DisposableEffect(reorderingState.draggingIndex) {
        val handle = if (reorderingState.draggingIndex == index) container?.pin() else null

        onDispose {
            handle?.release()
        }
    }

    this.shadow(elevation = elevation)
}
