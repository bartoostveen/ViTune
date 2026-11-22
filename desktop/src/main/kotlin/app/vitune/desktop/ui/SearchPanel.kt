package app.vitune.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.vitune.desktop.model.MusicRepository
import app.vitune.desktop.model.Song
import kotlinx.coroutines.delay

@Composable
fun SearchPanel(initialQuery: String?, modifier: Modifier = Modifier) {
    val ui = LocalUi.current
    val appState = ui.appState
    val controller = ui.controller
    val focusRequester = remember { FocusRequester() }

    var isLoading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var focused by remember { mutableStateOf(false) }
    // Bumped to run a search immediately (Enter, chip clicks) instead of waiting for the debounce
    var submitTick by remember { mutableStateOf(0) }

    fun submit(q: String) {
        ui.searchQuery = q
        appState.addSearch(q)
        submitTick++
    }

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank() && initialQuery != ui.searchedFor) submit(initialQuery)
    }
    LaunchedEffect(ui.focusSearchRequest) {
        runCatching { focusRequester.requestFocus() }
    }

    DisposableEffect(Unit) { onDispose { ui.typing = false } }

    // Live search: waits for a pause in typing, then searches. A submit searches right away.
    val handledTick = remember { intArrayOf(0) }
    LaunchedEffect(ui.searchQuery, submitTick) {
        val q = ui.searchQuery.trim()
        val immediate = submitTick != handledTick[0]
        handledTick[0] = submitTick
        if (q.isEmpty()) return@LaunchedEffect
        if (!immediate) {
            if (q == ui.searchedFor) return@LaunchedEffect
            delay(450)
        }
        isLoading = true
        failed = false
        val result = MusicRepository.searchSongs(q)
        ui.searchResults = result.getOrDefault(emptyList())
        failed = result.isFailure
        // The top results are the likeliest clicks: get them ready to play right away
        ui.searchResults.take(2).forEach { controller.prefetch(it) }
        ui.searchedFor = q
        isLoading = false
    }
    LaunchedEffect(ui.searchQuery) {
        val q = ui.searchQuery.trim()
        if (q.length < 2) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        delay(200)
        suggestions = runCatching { MusicRepository.suggestions(q) }.getOrDefault(emptyList())
            .filter { !it.equals(q, ignoreCase = true) }
    }

    Column(modifier.fillMaxSize()) {
        // Search field
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(start = 28.dp, end = 28.dp, top = 24.dp, bottom = 12.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp))
                .background(Palette.PanelRaised)
                .border(
                    width = 2.dp,
                    color = if (focused) Palette.Violet.copy(alpha = 0.8f) else Color.Transparent,
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 18.dp)
        ) {
            Icon(AppIcons.Search, null, tint = if (focused) Palette.TextPrimary else Palette.TextSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (ui.searchQuery.isEmpty()) {
                    Text(
                        "What do you want to listen to?",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Palette.TextMuted
                    )
                }
                BasicTextField(
                    value = ui.searchQuery,
                    onValueChange = { ui.searchQuery = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = Palette.TextPrimary),
                    cursorBrush = SolidColor(Palette.Violet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(ui.searchQuery) }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged {
                            focused = it.isFocused
                            ui.typing = it.isFocused
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                submit(ui.searchQuery); true
                            } else false
                        }
                )
            }
            if (isLoading) {
                CircularProgressIndicator(strokeWidth = 2.dp, color = Palette.Violet, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            if (ui.searchQuery.isNotEmpty()) {
                ToolButton(AppIcons.Close, "Clear", onClick = {
                    ui.searchQuery = ""
                    ui.searchResults = emptyList()
                    ui.searchedFor = null
                    runCatching { focusRequester.requestFocus() }
                }, size = 30.dp, iconSize = 18.dp)
            }
        }

        // Suggestions while typing
        if (suggestions.isNotEmpty() && focused) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                items(suggestions) { s ->
                    PillButton(s, onClick = { submit(s) }, icon = AppIcons.Search)
                }
            }
        }

        when {
            ui.searchQuery.isBlank() -> BrowseContent()
            failed && ui.searchResults.isEmpty() && !isLoading -> EmptyState(
                AppIcons.Search,
                "Search isn't working right now",
                "Check your internet connection and try again.",
                action = { PillButton("Try again", onClick = { submit(ui.searchQuery) }) }
            )
            ui.searchResults.isEmpty() && ui.searchedFor != null && !isLoading -> EmptyState(
                AppIcons.Search,
                "No results found for \"${ui.searchedFor}\"",
                "Check the spelling, or try fewer or different keywords."
            )
            else -> Results(ui.searchResults, onPlay = { song -> controller.playRadio(song) })
        }
    }
}

@Composable
private fun BrowseContent() {
    val ui = LocalUi.current
    val history = ui.appState.searchHistory
    LazyColumn(contentPadding = PaddingValues(start = 28.dp, end = 28.dp, bottom = 32.dp)) {
        if (history.isNotEmpty()) {
            item {
                SectionTitle(
                    "Recent searches",
                    actionLabel = "Clear",
                    onAction = { ui.appState.clearSearchHistory() },
                    modifier = Modifier.padding(top = 12.dp)
                )
                Spacer(Modifier.height(12.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(history) { q -> HistoryChip(q) }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        item {
            SectionTitle("Browse all", modifier = Modifier.padding(top = 12.dp))
            Spacer(Modifier.height(14.dp))
            GenreGrid()
        }
    }
}

@Composable
private fun HistoryChip(query: String) {
    val ui = LocalUi.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (hovered) Palette.PanelHover else Palette.PanelRaised)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable {
                ui.searchQuery = query
                ui.appState.addSearch(query)
            }
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
    ) {
        Icon(AppIcons.History, null, tint = Palette.TextSecondary, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(query, style = MaterialTheme.typography.labelLarge, color = Palette.TextPrimary)
        ToolButton(AppIcons.Close, "Remove", onClick = { ui.appState.removeSearch(query) }, size = 26.dp, iconSize = 14.dp)
    }
}

@Composable
private fun Results(results: List<Song>, onPlay: (Song) -> Unit) {
    val ui = LocalUi.current
    val controller = ui.controller
    if (results.isEmpty()) return
    val top = results.first()

    LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)) {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                val wide = maxWidth > 760.dp
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        Column(Modifier.weight(0.42f)) {
                            SectionTitle("Top result")
                            Spacer(Modifier.height(12.dp))
                            TopResultCard(top, onPlay = { onPlay(top) })
                        }
                        Column(Modifier.weight(0.58f)) {
                            SectionTitle("Songs")
                            Spacer(Modifier.height(8.dp))
                            results.drop(1).take(4).forEach { song ->
                                TrackRow(song, index = null, onPlay = { onPlay(song) }, compact = true)
                            }
                        }
                    }
                } else {
                    Column {
                        SectionTitle("Top result")
                        Spacer(Modifier.height(12.dp))
                        TopResultCard(top, onPlay = { onPlay(top) })
                    }
                }
            }
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 28.dp, bottom = 8.dp)
            ) {
                SectionTitle("All songs", modifier = Modifier.weight(1f))
                PillButton("Play all", onClick = { controller.playCollection(results) }, icon = AppIcons.Play)
                Spacer(Modifier.width(8.dp))
                PillButton("Shuffle", onClick = { controller.playCollection(results, shuffle = true) }, icon = AppIcons.Shuffle)
                Spacer(Modifier.width(8.dp))
                PillButton("Add all to queue", onClick = {
                    controller.addAllToQueue(results)
                    ui.toast("Added ${results.size} songs to queue")
                }, icon = AppIcons.QueueMusic)
            }
        }
        item { TrackListHeader() }
        itemsIndexed(results, key = { _, s -> s.id }) { i, song ->
            TrackRow(song, index = i, onPlay = { onPlay(song) }, modifier = Modifier.padding(horizontal = 16.dp))
        }
    }
}

@Composable
private fun TopResultCard(song: Song, onPlay: () -> Unit) {
    val controller = LocalUi.current.controller
    val isCurrent = controller.current?.id == song.id
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    PrefetchOnHover(song, hovered)
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (hovered) Palette.PanelHover else Palette.PanelRaised)
            .hoverable(interaction)
            .pointerHoverIcon(PointerIcon.Hand)
            .clickable(onClick = { if (isCurrent) controller.togglePlayPause() else onPlay() })
            .padding(20.dp)
    ) {
        Column {
            Artwork(song.thumbnailLarge, 104.dp, corner = 8.dp, placeholderKey = song.id)
            Spacer(Modifier.height(18.dp))
            Text(
                song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (isCurrent) Palette.Violet else Palette.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    song.artists,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    "SONG",
                    style = SectionLabelStyle,
                    color = Palette.TextPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
        if (hovered || isCurrent) {
            GradientPlayButton(
                isPlaying = isCurrent && controller.isPlaying,
                loading = isCurrent && controller.isLoading,
                onClick = { if (isCurrent) controller.togglePlayPause() else onPlay() },
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }
    }
}
