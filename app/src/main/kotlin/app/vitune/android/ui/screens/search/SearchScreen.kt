package app.vitune.android.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.vitune.android.R
import app.vitune.android.ui.components.themed.Scaffold
import app.vitune.android.ui.screens.GlobalRoutes
import app.vitune.android.ui.screens.Route
import app.vitune.android.utils.secondary
import app.vitune.compose.persist.PersistMapCleanup
import app.vitune.compose.routing.RouteHandler
import app.vitune.core.ui.LocalAppearance

@Route
@Composable
fun SearchScreen(
    initialText: String,
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit
) {
    val saveableStateHolder = rememberSaveableStateHolder()

    val [tabIndex, onTabChanged] = rememberSaveable { mutableIntStateOf(0) }

    val state = rememberTextFieldState(
        initialText = initialText
    )

    PersistMapCleanup(prefix = "search/")

    RouteHandler {
        GlobalRoutes()

        Content {
            val decorator = TextFieldDecorator { innerTextField ->
                Box {
                    AnimatedVisibility(
                        visible = state.text.isEmpty(),
                        enter = fadeIn(tween(300)),
                        exit = fadeOut(tween(300)),
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        BasicText(
                            text = stringResource(R.string.search_placeholder),
                            maxLines = 1,
                            style = LocalAppearance.current.typography.xxl.secondary
                        )
                    }

                    innerTextField()
                }
            }

            Scaffold(
                key = "search",
                topIconButtonId = R.drawable.chevron_back,
                onTopIconButtonClick = pop,
                tabIndex = tabIndex,
                onTabChange = onTabChanged,
                tabColumnContent = {
                    tab(0, R.string.online, R.drawable.globe, canHide = false)
                    tab(1, R.string.library, R.drawable.library)
                }
            ) { currentTabIndex ->
                saveableStateHolder.SaveableStateProvider(currentTabIndex) {
                    when (currentTabIndex) {
                        0 -> OnlineSearch(
                            state = state,
                            onSearch = onSearch,
                            onViewPlaylist = onViewPlaylist,
                            decorator = decorator,
                            focused = child == null
                        )

                        1 -> LocalSongSearch(
                            state = state,
                            decorator = decorator
                        )
                    }
                }
            }
        }
    }
}
