package app.vitune.android.ui.components.themed

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Down
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Companion.Up
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import app.vitune.android.R
import app.vitune.android.preferences.AppearancePreferences
import app.vitune.android.preferences.UIStatePreferences
import app.vitune.core.data.enums.NavigationRailPosition
import app.vitune.core.ui.LocalAppearance
import kotlinx.collections.immutable.toImmutableList

@Composable
fun Scaffold(
    key: String,
    topIconButtonId: Int,
    onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    onTabChange: (Int) -> Unit,
    tabColumnContent: TabsBuilder.() -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val (colorPalette) = LocalAppearance.current
    var hiddenTabs by UIStatePreferences.mutableTabStateOf(key)

    val navigationRailPosition = AppearancePreferences.navigationRailPosition

    val navigationRail: @Composable () -> Unit = {
        NavigationRail(
            topIconButtonId = topIconButtonId,
            onTopIconButtonClick = onTopIconButtonClick,
            tabIndex = tabIndex,
            onTabIndexChange = onTabChange,
            hiddenTabs = hiddenTabs,
            setHiddenTabs = { hiddenTabs = it.toImmutableList() },
            tabsEditingTitle = tabsEditingTitle,
            position = navigationRailPosition,
            content = tabColumnContent
        )
    }

    val mainContent: @Composable (Modifier) -> Unit = { contentModifier ->
        AnimatedContent(
            targetState = tabIndex,
            modifier = contentModifier,
            transitionSpec = {
                val slideDirection = if (targetState > initialState) Up else Down
                val animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold
                )

                ContentTransform(
                    targetContentEnter = slideIntoContainer(slideDirection, animationSpec),
                    initialContentExit = slideOutOfContainer(slideDirection, animationSpec),
                    sizeTransform = null
                )
            },
            content = content,
            label = ""
        )
    }

    Row(
        modifier = modifier
            .background(colorPalette.background0)
            .fillMaxSize()
    ) {
        // The rail keeps its intrinsic width; the main content takes the remaining space via
        // weight. Without the weight the content's fillMaxSize would consume the whole row and
        // push the rail off-screen when it's ordered first (i.e. docked on the right).
        // The navigation rail sits on the side selected in appearance settings.
        if (navigationRailPosition == NavigationRailPosition.Right) {
            mainContent(Modifier.weight(1f).fillMaxHeight())
            navigationRail()
        } else {
            navigationRail()
            mainContent(Modifier.weight(1f).fillMaxHeight())
        }
    }
}
