package app.vitune.android.ui.screens.search

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldDecorator
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.paint
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LocalPinnableContainer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.vitune.android.Database
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.models.SearchQuery
import app.vitune.android.preferences.DataPreferences
import app.vitune.android.query
import app.vitune.android.ui.components.themed.FloatingActionsContainerWithScrollToTop
import app.vitune.android.ui.components.themed.Header
import app.vitune.android.ui.components.themed.SecondaryTextButton
import app.vitune.android.utils.align
import app.vitune.android.utils.center
import app.vitune.android.utils.disabled
import app.vitune.android.utils.medium
import app.vitune.android.utils.secondary
import app.vitune.compose.persist.persist
import app.vitune.compose.persist.persistList
import app.vitune.core.ui.LocalAppearance
import app.vitune.providers.innertube.Innertube
import app.vitune.providers.innertube.models.bodies.SearchSuggestionsBody
import app.vitune.providers.innertube.requests.searchSuggestions
import io.ktor.http.Url
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.time.Duration.Companion.milliseconds

@OptIn(FlowPreview::class)
@Composable
fun OnlineSearch(
    onSearch: (String) -> Unit,
    onViewPlaylist: (String) -> Unit,
    focused: Boolean,
    modifier: Modifier = Modifier,
    state: TextFieldState = rememberTextFieldState(),
    decorator: TextFieldDecorator? = null,
) = Box(modifier = modifier) {
    val (colorPalette, typography) = LocalAppearance.current

    var history by persistList<SearchQuery>("search/online/history")
    var suggestionsResult by persist<Result<List<String>?>?>("search/online/suggestionsResult")

    LaunchedEffect(state) {
        if (DataPreferences.pauseSearchHistory) return@LaunchedEffect

        snapshotFlow { state.text }
            .distinctUntilChanged()
            .debounce(100.milliseconds)
            .collect { query ->
                Database.queries("%${query}%")
                    .distinctUntilChanged { old, new -> old.size == new.size }
                    .collect { history = it.toImmutableList() }
            }
    }

    LaunchedEffect(state) {
        snapshotFlow { state.text }
            .distinctUntilChanged()
            .debounce(100.milliseconds)
            .collect { query ->
                if (query.isEmpty()) return@collect
                suggestionsResult = Innertube.searchSuggestions(
                    body = SearchSuggestionsBody(input = query.toString())
                )
            }
    }

    val playlistId by remember(state) {
        derivedStateOf {
            runCatching {
                Url(state.text.toString()).takeIf {
                    it.host.endsWith("youtube.com", ignoreCase = true) &&
                        it.segments.lastOrNull()?.equals("playlist", ignoreCase = true) == true
                }?.parameters?.get("list")
            }.getOrNull()
        }
    }

    val focusRequester = remember { FocusRequester() }
    val lazyListState = rememberLazyListState()

    LazyColumn(
        state = lazyListState,
        contentPadding = LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Vertical + WindowInsetsSides.End)
            .asPaddingValues(),
        modifier = Modifier.fillMaxSize()
    ) {
        item(
            key = "header",
            contentType = 0
        ) {
            val container = LocalPinnableContainer.current

            DisposableEffect(Unit) {
                val handle = container?.pin()

                onDispose {
                    handle?.release()
                }
            }

            LaunchedEffect(focused) {
                if (!focused) return@LaunchedEffect

                delay(300.milliseconds)
                focusRequester.requestFocus()
            }

            Header(
                titleContent = {
                    BasicTextField(
                        state = state,
                        textStyle = typography.xxl.medium.align(TextAlign.End),
                        lineLimits = TextFieldLineLimits.SingleLine,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        onKeyboardAction = { performDefaultAction ->
                            if (state.text.isEmpty()) return@BasicTextField

                            onSearch(state.text.toString())
                            performDefaultAction()
                        },
                        cursorBrush = SolidColor(colorPalette.text),
                        decorator = decorator,
                        modifier = Modifier.focusRequester(focusRequester)
                    )
                },
                actionsContent = {
                    playlistId?.let { playlistId ->
                        val isAlbum = playlistId.startsWith("OLAK5uy_")

                        SecondaryTextButton(
                            text = if (isAlbum) stringResource(R.string.view_album)
                            else stringResource(R.string.view_playlist),
                            onClick = { onViewPlaylist(state.text.toString()) }
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (state.text.isNotEmpty()) SecondaryTextButton(
                        text = stringResource(R.string.clear),
                        onClick = { state.clearText() }
                    )
                }
            )
        }

        items(
            items = history,
            key = SearchQuery::id
        ) { searchQuery ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onSearch(searchQuery.query) }
                    .fillMaxWidth()
                    .padding(all = 16.dp)
                    .animateItem()
            ) {
                Spacer(
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .size(20.dp)
                        .paint(
                            painter = painterResource(R.drawable.time),
                            colorFilter = ColorFilter.disabled
                        )
                )

                BasicText(
                    text = searchQuery.query,
                    style = typography.s.secondary,
                    modifier = Modifier
                        .padding(horizontal = 8.dp)
                        .weight(1f)
                )

                Image(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    colorFilter = ColorFilter.disabled,
                    modifier = Modifier
                        .clickable(
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = {
                                query {
                                    Database.delete(searchQuery)
                                }
                            }
                        )
                        .padding(horizontal = 8.dp)
                        .size(20.dp)
                )

                Image(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    colorFilter = ColorFilter.disabled,
                    modifier = Modifier
                        .clickable(
                            indication = ripple(bounded = false),
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = { state.setTextAndPlaceCursorAtEnd(searchQuery.query) }
                        )
                        .rotate(225f)
                        .padding(horizontal = 8.dp)
                        .size(22.dp)
                )
            }
        }

        suggestionsResult?.getOrNull()?.let { suggestions ->
            items(items = suggestions) { suggestion ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clickable { onSearch(suggestion) }
                        .fillMaxWidth()
                        .padding(all = 16.dp)
                ) {
                    Spacer(
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .size(20.dp)
                            .paint(
                                painter = painterResource(R.drawable.search),
                                colorFilter = ColorFilter.disabled
                            )
                    )

                    BasicText(
                        text = suggestion,
                        style = typography.s.secondary,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .weight(1f)
                    )

                    Image(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        colorFilter = ColorFilter.disabled,
                        modifier = Modifier
                            .clickable(
                                indication = ripple(bounded = false),
                                interactionSource = remember { MutableInteractionSource() },
                                onClick = { state.setTextAndPlaceCursorAtEnd(suggestion) }
                            )
                            .rotate(225f)
                            .padding(horizontal = 8.dp)
                            .size(22.dp)
                    )
                }
            }
        } ?: suggestionsResult?.exceptionOrNull()?.let {
            item {
                Box(modifier = Modifier.fillMaxSize()) {
                    BasicText(
                        text = stringResource(R.string.error_message),
                        style = typography.s.secondary.center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }

    FloatingActionsContainerWithScrollToTop(lazyListState = lazyListState)
}
