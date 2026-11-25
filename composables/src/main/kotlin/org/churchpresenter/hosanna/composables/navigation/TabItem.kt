package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.NavigationDefaults
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.foundation.TabColors
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.tabColors
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.TabStyle

@Composable
internal fun TabItem(
    tab: NavigationTab,
    selected: Boolean,
    style: TabStyle,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
    onMove: (Boolean) -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = HosannaTheme.colors.tabColors(selected, buttonState(true, false, hovered, pressed, focused))
    val reveal = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) reveal.bringIntoView() }
    val iconOnly = style == TabStyle.Icon
    Tooltip(tab.label, enabled = iconOnly) {
        TabBody(tab, selected, iconOnly, style, colors, interactionSource, focusRequester, reveal, onClick, onMove)
    }
}

@Composable
private fun TabBody(
    tab: NavigationTab,
    selected: Boolean,
    iconOnly: Boolean,
    style: TabStyle,
    colors: TabColors,
    interactionSource: MutableInteractionSource,
    focusRequester: FocusRequester,
    reveal: BringIntoViewRequester,
    onClick: () -> Unit,
    onMove: (Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .height(NavigationDefaults.BarHeight)
            .bringIntoViewRequester(reveal)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val forward = when (event.key) {
                    Key.DirectionRight -> true
                    Key.DirectionLeft -> false
                    else -> return@onPreviewKeyEvent false
                }
                if (event.type == KeyEventType.KeyDown) onMove(forward)
                true
            }
            .selectable(selected, interactionSource, null, role = Role.Tab, onClick = onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = tab.label } else Modifier)
            .padding(horizontal = if (iconOnly) NavigationDefaults.IconTabPadding else NavigationDefaults.TabPadding),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier.drawBehind { drawTabChrome(colors) },
            horizontalArrangement = Arrangement.spacedBy(NavigationDefaults.IconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (style != TabStyle.Text) {
                Box {
                    Image(
                        tab.icon,
                        null,
                        Modifier.size(NavigationDefaults.Icon),
                        colorFilter = ColorFilter.tint(colors.content),
                    )
                    if (iconOnly) {
                        tab.badge?.let {
                            TabBadge(
                                it, colors, NavigationDefaults.SmallBadge,
                                Modifier.align(Alignment.TopEnd).offset(
                                    x = NavigationDefaults.SmallBadge / 2,
                                    y = -NavigationDefaults.SmallBadge / 2,
                                ),
                            )
                        }
                    }
                }
            }
            if (!iconOnly) {
                BasicText(tab.label, style = HosannaTheme.typography.bodyStrong.copy(color = colors.content))
                tab.badge?.let { TabBadge(it, colors, NavigationDefaults.Badge) }
            }
        }
    }
}

private fun DrawScope.drawTabChrome(colors: TabColors) {
    val pad = NavigationDefaults.PillPadding.toPx()
    val pill = NavigationDefaults.Pill.toPx()
    val radius = CornerRadius(NavigationDefaults.PillRadius.toPx())
    val topLeft = Offset(-pad, size.height / 2 - pill / 2)
    val pillSize = Size(size.width + 2 * pad, pill)
    drawRoundRect(colors.layer, topLeft, pillSize, radius)
    colors.ring?.let {
        drawRoundRect(it, topLeft, pillSize, radius, style = Stroke(HosannaTheme.focusRingWidth.toPx()))
    }
    val bar = NavigationDefaults.Indicator.toPx()
    val bottom = size.height / 2 + NavigationDefaults.BarHeight.toPx() / 2
    val corner = NavigationDefaults.IndicatorRadius.toPx()
    drawRoundRect(colors.indicator, Offset(0f, bottom - bar), Size(size.width, bar + corner), CornerRadius(corner))
}

@Composable
private fun TabBadge(text: String, colors: TabColors, height: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .defaultMinSize(minWidth = height)
            .height(height)
            .background(colors.badge, CircleShape)
            .padding(horizontal = NavigationDefaults.BadgePadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = HosannaTheme.typography.captionStrong.copy(color = colors.onBadge))
    }
}

internal fun tabWidth(style: TabStyle, labelWidth: Float, badgeWidth: Float?, density: Density): Float =
    with(density) {
        val padding = if (style == TabStyle.Icon) NavigationDefaults.IconTabPadding else NavigationDefaults.TabPadding
        var width = 2 * padding.toPx()
        if (style != TabStyle.Text) width += NavigationDefaults.Icon.toPx()
        if (style == TabStyle.IconAndText) width += NavigationDefaults.IconGap.toPx()
        if (style != TabStyle.Icon) {
            width += labelWidth
            badgeWidth?.let {
                val pill = maxOf(NavigationDefaults.Badge.toPx(), it + 2 * NavigationDefaults.BadgePadding.toPx())
                width += NavigationDefaults.IconGap.toPx() + pill
            }
        }
        width
    }
