package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.NavigationDefaults
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.model.fittedTabStyle
import org.churchpresenter.hosanna.composables.model.neighbourTab
import org.churchpresenter.hosanna.composables.model.tabsScroll
import kotlin.math.abs
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * The tab bar across the top of the window, with optional buttons at the right. Tabs drop to icons when their
 * labels do not fit, and scroll when even icons do not. ← → move between tabs.
 *
 * @param tabs the tabs, in order.
 * @param selectedKey the key of the open tab.
 * @param onSelect called with the key of the tab picked.
 * @param modifier applied to the outermost layout.
 * @param style how the tabs are drawn when there is room.
 * @param interactionSources per tab key; pass them to watch or freeze each tab's looks.
 * @param actions optional buttons at the right, after a divider.
 */
@Composable
fun TopNavigationBar(
    tabs: List<NavigationTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    style: TabStyle = TabStyle.IconAndText,
    interactionSources: Map<String, MutableInteractionSource> = emptyMap(),
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    val colors = ThemeColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(NavigationDefaults.BarHeight)
            .background(colors.surface)
            .drawBehind {
                val line = NavigationDefaults.BarLine.toPx()
                drawRect(colors.outlineVariant, Offset(0f, size.height - line), Size(size.width, line))
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabRow(tabs, selectedKey, onSelect, style, interactionSources, Modifier.weight(1f))
        actions?.let {
            Box(
                Modifier
                    .padding(horizontal = NavigationDefaults.ActionsGap * 2)
                    .width(NavigationDefaults.Divider)
                    .height(NavigationDefaults.DividerHeight)
                    .background(colors.outlineVariant),
            )
            Row(
                modifier = Modifier.padding(end = NavigationDefaults.ActionsEnd),
                horizontalArrangement = Arrangement.spacedBy(NavigationDefaults.ActionsGap),
                verticalAlignment = Alignment.CenterVertically,
                content = it,
            )
        }
    }
}

@Composable
private fun TabRow(
    tabs: List<NavigationTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    requested: TabStyle,
    interactionSources: Map<String, MutableInteractionSource>,
    modifier: Modifier,
) {
    val measurer = rememberTextMeasurer()
    val label = HosannaTheme.typography.bodyStrong
    val badge = HosannaTheme.typography.captionStrong
    val density = LocalDensity.current
    val requesters = remember(tabs.map { it.key }) { tabs.associate { it.key to FocusRequester() } }
    val scroll = rememberScrollState()
    BoxWithConstraints(modifier.padding(start = NavigationDefaults.TabsStart)) {
        val available = with(density) { maxWidth.toPx() }
        val labels = tabs.map { measurer.measure(it.label, label).size.width.toFloat() }
        val badges = tabs.map { tab -> tab.badge?.let { measurer.measure(it, badge).size.width.toFloat() } }
        fun widthOf(style: TabStyle) = tabs.indices.sumOf { i ->
            tabWidth(style, labels[i], badges[i], density).toDouble()
        }.toFloat()
        val style = fittedTabStyle(requested, widthOf(requested), available)
        val scrolls = tabsScroll(style, widthOf(TabStyle.Icon), available)
        val fade = ThemeColors.current.surface
        Row(
            modifier = if (scrolls) Modifier.scrollingTabs(scroll, fade) else Modifier,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                TabItem(
                    tab = tab,
                    selected = tab.key == selectedKey,
                    style = style,
                    onClick = { onSelect(tab.key) },
                    interactionSource = interactionSources[tab.key] ?: remember(tab.key) { MutableInteractionSource() },
                    focusRequester = requesters.getValue(tab.key),
                    onMove = { forward ->
                        neighbourTab(tabs.map { it.key }, tab.key, forward)?.let { requesters[it]?.requestFocus() }
                    },
                )
            }
        }
    }
}

private fun Modifier.scrollingTabs(scroll: ScrollState, fade: Color): Modifier = this
    .pointerInput(scroll) {
        val step = NavigationDefaults.BarHeight.toPx()
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: continue
                val delta = change.scrollDelta
                if (event.type == PointerEventType.Scroll && abs(delta.y) > abs(delta.x)) {
                    scroll.dispatchRawDelta(delta.y * step)
                    change.consume()
                }
            }
        }
    }
    .horizontalScroll(scroll)
    .drawWithContent {
        drawContent()
        if (scroll.value < scroll.maxValue) {
            val width = NavigationDefaults.Fade.toPx()
            val left = scroll.value + size.width - scroll.maxValue - width
            drawRect(
                Brush.horizontalGradient(listOf(fade.copy(alpha = 0f), fade), startX = left, endX = left + width),
                Offset(left, 0f),
                Size(width, size.height),
            )
        }
    }
