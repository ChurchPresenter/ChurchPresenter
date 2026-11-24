package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.TabLabelMargin
import org.churchpresenter.settings.TabLabelStyle
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.guideTarget
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.background
import org.churchpresenter.icons.generated.resources.ic_settings
import org.churchpresenter.strings.generated.resources.tab_visibility
import org.churchpresenter.strings.generated.resources.tab_visibility_tabs
import org.churchpresenter.strings.generated.resources.tooltip_settings
import org.churchpresenter.sharedui.composables.ContextMenu
import org.churchpresenter.sharedui.composables.ToggleMenuHeader
import org.churchpresenter.sharedui.composables.ToggleMenuItem
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.app.churchpresenter.tabs.TabSection
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.app.churchpresenter.tabs.getStringName
import org.churchpresenter.theme.AppShape
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private const val CONTENT_CROSSFADE_MS = 120
private val TAB_MENU_WIDTH = 220.dp

/** The tab bar's bottom corners, matching the cards below it; the divider under it stops where they start. */
private val TAB_BAR_CORNER_RADIUS = 14.dp
private val TAB_BAR_SHAPE = AppShape(bottomStart = TAB_BAR_CORNER_RADIUS, bottomEnd = TAB_BAR_CORNER_RADIUS)

/** What the tab bar shows: the tabs, which one is showing, how they are labelled and which are hidden. */
internal class TabBarState(
    val visibleTabs: List<Tabs>,
    val selectedTabIndex: Int,
    val labelStyle: TabLabelStyle,
    val labelMargin: TabLabelMargin,
    val hiddenTabs: Set<String>,
)

/** What the tab bar's controls do. */
internal class TabBarActions(
    val onTabSelected: (Int) -> Unit,
    /** Shows a hidden tab or hides a shown one; never offered for the last tab showing. */
    val onToggleTabHidden: (Tabs) -> Unit,
    val onShowBackgroundSettings: () -> Unit,
    val onShowSettings: () -> Unit,
)

/**
 * The middle of the main screen: the tab bar above the showing tab. [currentTab] is crossfaded in,
 * drawn by [tabContent].
 */
@Composable
internal fun MainTabArea(
    modifier: Modifier,
    tabBar: TabBarState,
    actions: TabBarActions,
    currentTab: Tabs,
    tabContent: @Composable (Tabs) -> Unit,
) {
    Column(modifier = modifier) {
        TabBar(tabBar, actions)
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = TAB_BAR_CORNER_RADIUS),
            color = MaterialTheme.colorScheme.outlineVariant,
            thickness = 1.dp,
        )
        AnimatedContent(
            targetState = currentTab,
            transitionSpec = {
                fadeIn(tween(CONTENT_CROSSFADE_MS)) togetherWith fadeOut(tween(CONTENT_CROSSFADE_MS))
            },
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "tab_content"
        ) { tab ->
            tabContent(tab)
        }
    }
}

@Composable
private fun TabBar(tabBar: TabBarState, actions: TabBarActions) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .clip(TAB_BAR_SHAPE)
            .background(MaterialTheme.colorScheme.surface),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TabSection(
            modifier = Modifier.weight(1f),
            visibleTabs = tabBar.visibleTabs,
            selectedTabIndex = tabBar.selectedTabIndex,
            labelStyle = tabBar.labelStyle,
            labelMargin = tabBar.labelMargin,
            onTabSelected = actions.onTabSelected
        )
        TabVisibilityMenu(tabBar.hiddenTabs, actions.onToggleTabHidden)
        val backgroundLabel = stringResource(Res.string.background)
        Tooltip(text = backgroundLabel, modifier = Modifier.guideTarget(GuideTargets.BACKGROUND_BUTTON)) {
            IconButton(
                icon = rememberVectorPainter(Icons.Default.Wallpaper),
                contentDescription = backgroundLabel,
                onClick = actions.onShowBackgroundSettings,
                size = IconButtonSize.Large,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
            )
        }
        val settingsLabel = stringResource(Res.string.tooltip_settings)
        Tooltip(text = settingsLabel, modifier = Modifier.guideTarget(GuideTargets.SETTINGS_BUTTON)) {
            IconButton(
                icon = painterResource(IconRes.drawable.ic_settings),
                contentDescription = settingsLabel,
                onClick = actions.onShowSettings,
                size = IconButtonSize.Large,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
            )
        }
    }
}

/** The tab bar's show/hide menu: every tab but the crossword, never letting the last one go. */
@Composable
private fun TabVisibilityMenu(hiddenTabs: Set<String>, onToggleTabHidden: (Tabs) -> Unit) {
    var showTabVisibilityMenu by remember { mutableStateOf(false) }
    Box {
        val label = stringResource(Res.string.tab_visibility)
        Tooltip(text = label) {
            IconToggleButton(
                icon = rememberVectorPainter(Icons.Default.Tune),
                contentDescription = label,
                checked = showTabVisibilityMenu,
                onCheckedChange = { showTabVisibilityMenu = true },
                size = IconButtonSize.Large,
                hierarchy = ButtonHierarchy.Tertiary,
                intent = ButtonIntent.Neutral,
            )
        }
        ContextMenu(
            expanded = showTabVisibilityMenu,
            onDismissRequest = { showTabVisibilityMenu = false },
            width = TAB_MENU_WIDTH,
        ) {
            val menuTabs = MENU_TABS
            val visibleCount = visibleTabCount(hiddenTabs)
            ToggleMenuHeader(
                title = stringResource(Res.string.tab_visibility_tabs),
                shown = visibleCount,
                total = menuTabs.size,
                onShowAll = { menuTabs.filter { it.name in hiddenTabs }.forEach(onToggleTabHidden) },
            )
            menuTabs.forEach { tab ->
                val isOnlyVisible = isOnlyVisibleTab(tab, hiddenTabs, visibleCount)
                ToggleMenuItem(
                    label = getStringName(tab),
                    checked = tab.name !in hiddenTabs,
                    onCheckedChange = { onToggleTabHidden(tab) },
                    enabled = !isOnlyVisible,
                )
            }
        }
    }
}
