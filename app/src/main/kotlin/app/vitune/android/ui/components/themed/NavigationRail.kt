package app.vitune.android.ui.components.themed

import android.os.Parcelable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
import app.vitune.android.LocalPlayerAwareWindowInsets
import app.vitune.android.R
import app.vitune.android.ui.screens.settings.SwitchSettingsEntry
import app.vitune.android.utils.center
import app.vitune.android.utils.color
import app.vitune.android.utils.semiBold
import app.vitune.core.data.enums.NavigationRailPosition
import app.vitune.core.ui.Dimensions
import app.vitune.core.ui.LocalAppearance
import app.vitune.core.ui.utils.isLandscape
import app.vitune.core.ui.utils.roundedShape
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize

class TabsBuilder @PublishedApi internal constructor() {
    companion object {
        @Composable
        inline fun rememberTabs(crossinline content: TabsBuilder.() -> Unit) = rememberSaveable(
            saver = listSaver(
                save = { it },
                restore = { it.toImmutableList() }
            )
        ) {
            TabsBuilder().apply(content).tabs.values.toImmutableList()
        }
    }

    @PublishedApi
    internal val tabs = mutableMapOf<String, Tab>()

    fun tab(
        key: Int,
        @StringRes
        title: Int,
        @DrawableRes
        icon: Int,
        canHide: Boolean = true
    ): Tab = tab(key.toString(), title, icon, canHide)

    fun tab(
        key: String,
        @StringRes
        title: Int,
        @DrawableRes
        icon: Int,
        canHide: Boolean = true
    ): Tab {
        require(key.isNotBlank()) { "key cannot be blank" }
        require(!tabs.containsKey(key)) { "key already exists" }
        require(icon != 0) { "icon is 0" }

        val ret = Tab.ResourcesTab(
            key = key,
            titleRes = title,
            icon = icon,
            canHide = canHide
        )
        tabs += key to ret
        return ret
    }

    fun tab(
        key: Int,
        title: String,
        @DrawableRes
        icon: Int,
        canHide: Boolean = true
    ): Tab = tab(key.toString(), title, icon, canHide)

    fun tab(
        key: String,
        title: String,
        @DrawableRes
        icon: Int,
        canHide: Boolean = true
    ): Tab {
        require(key.isNotBlank()) { "key cannot be blank" }
        require(title.isNotBlank()) { "title cannot be blank" }
        require(!tabs.containsKey(key)) { "key already exists" }
        require(icon != 0) { "icon is 0" }

        val ret = Tab.StaticTab(
            key = key,
            titleText = title,
            icon = icon,
            canHide = canHide
        )
        tabs += key to ret
        return ret
    }
}

@Parcelize
sealed class Tab : Parcelable {
    abstract val key: String

    @IgnoredOnParcel
    abstract val title: @Composable () -> String

    @get:DrawableRes
    abstract val icon: Int
    abstract val canHide: Boolean

    data class ResourcesTab(
        override val key: String,
        @param:StringRes
        private val titleRes: Int,
        @param:DrawableRes
        override val icon: Int,
        override val canHide: Boolean
    ) : Tab() {
        @IgnoredOnParcel
        override val title: @Composable () -> String = { stringResource(titleRes) }
    }

    data class StaticTab(
        override val key: String,
        private val titleText: String,
        @param:DrawableRes
        override val icon: Int,
        override val canHide: Boolean
    ) : Tab() {
        @IgnoredOnParcel
        override val title: @Composable () -> String = { titleText }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
inline fun NavigationRail(
    topIconButtonId: Int,
    noinline onTopIconButtonClick: () -> Unit,
    tabIndex: Int,
    crossinline onTabIndexChange: (Int) -> Unit,
    hiddenTabs: ImmutableList<String>,
    crossinline setHiddenTabs: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    tabsEditingTitle: String = stringResource(R.string.tabs),
    position: NavigationRailPosition = NavigationRailPosition.Left,
    crossinline content: TabsBuilder.() -> Unit
) {
    val (colorPalette, typography) = LocalAppearance.current

    val tabs = TabsBuilder.rememberTabs(content)
    val isLandscape = isLandscape

    // Apply the inset on whichever side the rail is docked against, so it never collides with the
    // system bars / display cutout when moved to the right. Absolute Left/Right sides are used
    // (rather than Start/End) so the physical edge is correct regardless of layout direction.
    val horizontalInsetSide = when (position) {
        NavigationRailPosition.Left -> WindowInsetsSides.Left
        NavigationRailPosition.Right -> WindowInsetsSides.Right
    }

    val paddingValues = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Vertical + horizontalInsetSide)
        .asPaddingValues()

    var editing by remember { mutableStateOf(false) }

    if (editing) DefaultDialog(
        onDismiss = { editing = false },
        horizontalPadding = 0.dp
    ) {
        BasicText(
            text = tabsEditingTitle,
            style = typography.s.center.semiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            items(
                items = tabs,
                key = { it.key }
            ) { tab ->
                SwitchSettingsEntry(
                    title = tab.title(),
                    text = null,
                    isChecked = tab.key !in hiddenTabs,
                    onCheckedChange = {
                        if (!it && hiddenTabs.size == tabs.size - 1) return@SwitchSettingsEntry

                        setHiddenTabs(if (it) hiddenTabs - tab.key else hiddenTabs + tab.key)
                    },
                    isEnabled = tab.canHide && (tab.key in hiddenTabs || hiddenTabs.size < tabs.size - 1)
                )
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(paddingValues)
    ) {
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .size(
                    width = if (isLandscape) Dimensions.navigationRail.widthLandscape
                    else Dimensions.navigationRail.width,
                    height = Dimensions.items.headerHeight
                )
        ) {
            Image(
                painter = painterResource(topIconButtonId),
                contentDescription = null,
                colorFilter = ColorFilter.tint(colorPalette.textSecondary),
                modifier = Modifier
                    .offset(
                        x = if (isLandscape) 0.dp else Dimensions.navigationRail.iconOffset,
                        y = 48.dp
                    )
                    .clip(CircleShape)
                    .clickable(onClick = onTopIconButtonClick)
                    .padding(all = 12.dp)
                    .size(22.dp)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.width(
                if (isLandscape) Dimensions.navigationRail.widthLandscape
                else Dimensions.navigationRail.width
            )
        ) {
            val transition = updateTransition(targetState = tabIndex, label = null)

            tabs.fastForEachIndexed { index, tab ->
                AnimatedVisibility(
                    visible = tabIndex == index || tab.key !in hiddenTabs,
                    label = ""
                ) {
                    val dothAlpha by transition.animateFloat(label = "") {
                        if (it == index) 1f else 0f
                    }

                    val textColor by transition.animateColor(label = "") {
                        if (it == index) colorPalette.text else colorPalette.textDisabled
                    }

                    val iconContent: @Composable () -> Unit = {
                        Image(
                            painter = painterResource(tab.icon),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colorPalette.text),
                            modifier = Modifier
                                .vertical(enabled = !isLandscape)
                                .graphicsLayer {
                                    alpha = dothAlpha
                                    translationX = (1f - dothAlpha) * -48.dp.toPx()
                                    rotationZ = if (isLandscape) 0f else -90f
                                }
                                .size(Dimensions.navigationRail.iconOffset * 2)
                        )
                    }

                    val textContent: @Composable () -> Unit = {
                        BasicText(
                            text = tab.title(),
                            style = typography.xs.semiBold.center.color(textColor),
                            modifier = Modifier
                                .vertical(enabled = !isLandscape)
                                .rotate(if (isLandscape) 0f else -90f)
                                .padding(horizontal = 16.dp),
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 2
                        )
                    }

                    val contentModifier = Modifier
                        .clip(24.dp.roundedShape)
                        .combinedClickable(
                            onClick = { onTabIndexChange(index) },
                            onLongClick = { editing = true }
                        )

                    if (isLandscape) Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = contentModifier
                            .padding(vertical = 8.dp)
                            .fillMaxWidth()
                    ) {
                        iconContent()
                        textContent()
                    } else Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = contentModifier.padding(horizontal = 8.dp)
                    ) {
                        iconContent()
                        textContent()
                    }
                }
            }
        }
    }
}

fun Modifier.vertical(enabled: Boolean = true) =
    if (enabled)
        layout { measurable, constraints ->
            val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))
            layout(placeable.height, placeable.width) {
                placeable.place(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = -(placeable.height / 2 - placeable.width / 2)
                )
            }
        } else this
