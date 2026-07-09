package app.vitune.android.utils

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.util.fastForEach

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width

fun interface PositionInLayout {
    companion object {
        val Default = PositionInLayout { layoutSize, itemSize -> layoutSize / 2f - itemSize / 2f }
    }

    fun calculate(layoutSize: Float, itemSize: Float): Float
}

class GridSnapLayoutInfoProvider(
    private val lazyGridState: LazyGridState,
    private val positionInLayout: PositionInLayout = PositionInLayout.Default
) : SnapLayoutInfoProvider {
    override fun calculateApproachOffset(
        velocity: Float,
        decayOffset: Float
    ) = 0f

    override fun calculateSnapOffset(velocity: Float): Float {
        var lowerBoundOffset = Float.NEGATIVE_INFINITY
        var upperBoundOffset = Float.POSITIVE_INFINITY

        val layoutInfo = lazyGridState.layoutInfo

        layoutInfo.visibleItemsInfo.fastForEach { item ->
            val offset = calculateDistanceToDesiredSnapPosition(
                layoutInfo = layoutInfo,
                item = item,
                positionInLayout = positionInLayout
            )

            // Find item that is closest to the center
            if (offset <= 0 && offset > lowerBoundOffset) lowerBoundOffset = offset
            // Find item that is closest to center, but after it
            if (offset >= 0 && offset < upperBoundOffset) upperBoundOffset = offset
        }

        return if (lowerBoundOffset * -1f > upperBoundOffset) upperBoundOffset else lowerBoundOffset
    }
}

private fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: PositionInLayout
): Float {
    val containerSize = with(layoutInfo) {
        singleAxisViewportSize - beforeContentPadding - afterContentPadding
    }

    val desiredDistance = positionInLayout.calculate(
        layoutSize = containerSize.toFloat(),
        itemSize = item.size.width.toFloat()
    )
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

@Composable
fun rememberSnapLayoutInfo(
    lazyGridState: LazyGridState,
    positionInLayout: Density.(layoutSize: Float, itemSize: Float) -> Float =
        { layoutSize, itemSize -> PositionInLayout.Default.calculate(layoutSize, itemSize) }
): SnapLayoutInfoProvider {
    val density = LocalDensity.current

    return remember(lazyGridState, density) {
        GridSnapLayoutInfoProvider(
            lazyGridState = lazyGridState,
            positionInLayout = { layoutSize, itemSize ->
                density.positionInLayout(layoutSize, itemSize)
            }
        )
    }
}
