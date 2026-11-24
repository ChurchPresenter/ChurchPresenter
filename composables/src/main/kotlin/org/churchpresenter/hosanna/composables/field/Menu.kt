package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.ScrollbarStyle
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.utf16CodePoint
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun DropdownMenu(
    entries: List<MenuEntry>,
    selected: String?,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    width: Dp,
    search: MenuSearch?,
) {
    var query by remember { mutableStateOf("") }
    val searchable = search != null && itemCount(entries) > MenuDefaults.SEARCH_AFTER
    val shown = if (searchable) filterEntries(entries, query) else entries
    val selectedIndex = shown.indexOfFirst { it is MenuEntry.Item && it.key == selected }
    var keyboard by remember { mutableStateOf(false) }
    var active by remember(shown) {
        mutableIntStateOf(if (selectedIndex >= 0) selectedIndex else nextPickable(shown, -1, 1))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, selectedIndex))
    LaunchedEffect(active) { if (active in shown.indices) listState.animateScrollToItem(active) }
    val visible = remember { MutableTransitionState(false).apply { targetState = true } }
    val density = LocalDensity.current
    val gap = with(density) { MenuDefaults.Gap.roundToPx() }
    val slide = with(density) { MenuDefaults.OpenSlide.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { MenuPositionProvider(gap) },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
        onPreviewKeyEvent = { event ->
            when (menuKey(event)) {
                MenuKey.Up -> {
                    keyboard = true
                    active = nextPickable(shown, active, -1)
                }
                MenuKey.Down -> {
                    keyboard = true
                    active = nextPickable(shown, active, 1)
                }
                MenuKey.Pick -> (shown.getOrNull(active) as? MenuEntry.Item)?.takeIf { it.enabled }?.let {
                    onPick(it.key)
                }
                MenuKey.Close -> onDismiss()
                MenuKey.None -> {
                    val typed = event.utf16CodePoint.toChar()
                    if (searchable || event.type != KeyEventType.KeyDown || !typed.isLetterOrDigit()) {
                        return@Popup false
                    }
                    firstMatch(shown, typed).takeIf { it >= 0 }?.let {
                        keyboard = true
                        active = it
                    }
                }
            }
            true
        },
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = fadeIn(tween(MenuDefaults.OPEN_MILLIS)) +
                slideInVertically(tween(MenuDefaults.OPEN_MILLIS)) { -slide },
        ) {
            MenuSurface(width) {
                if (searchable) {
                    SearchField(
                        query, { query = it }, search.placeholder, search.clearContentDescription,
                        Modifier.fillMaxWidth().padding(bottom = MenuDefaults.Padding),
                        compact = true,
                    )
                }
                if (shown.isEmpty() && search != null) {
                    BasicText(
                        search.noMatches,
                        Modifier.padding(MenuDefaults.RowPadding),
                        style = HosannaTheme.typography.body.copy(color = HosannaTheme.colors.onSurfaceFaint),
                    )
                }
                Box(Modifier.heightIn(max = MenuDefaults.MaxHeight)) {
                    LazyColumn(state = listState) {
                        itemsIndexed(shown) { index, entry ->
                            when (entry) {
                                is MenuEntry.Header -> MenuHeader(entry.title)
                                MenuEntry.Divider -> MenuDivider()
                                is MenuEntry.Item -> MenuRow(
                                    item = entry,
                                    selected = entry.key == selected,
                                    active = keyboard && index == active,
                                    onHover = {
                                        keyboard = false
                                        active = index
                                    },
                                    onClick = { onPick(entry.key) },
                                )
                            }
                        }
                    }
                    if (listState.canScrollForward || listState.canScrollBackward) {
                        VerticalScrollbar(
                            adapter = rememberScrollbarAdapter(listState),
                            modifier = Modifier.align(Alignment.CenterEnd),
                            style = menuScrollbarStyle(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun menuScrollbarStyle(): ScrollbarStyle {
    val thumb = HosannaTheme.colors.onSurfaceFaint
    return ScrollbarStyle(
        minimalHeight = MenuDefaults.RowHeight,
        thickness = MenuDefaults.ScrollbarThickness,
        shape = HosannaTheme.shapes.s,
        hoverDurationMillis = MenuDefaults.OPEN_MILLIS,
        unhoverColor = thumb,
        hoverColor = thumb,
    )
}

@Composable
internal fun MenuSurface(width: Dp?, padding: Dp = MenuDefaults.Padding, content: @Composable () -> Unit) {
    val shape = HosannaTheme.shapes.l
    Column(
        modifier = Modifier
            .then(width?.let { Modifier.width(it) } ?: Modifier.width(IntrinsicSize.Max))
            .hosannaElevation(HosannaLevel.Level3, shape, HosannaTheme.elevation)
            .background(HosannaTheme.colors.surfaceContainerHigh, shape)
            .padding(padding),
    ) {
        content()
    }
}

@Composable
private fun MenuHeader(title: String) {
    BasicText(
        title.asOverline(),
        Modifier.padding(
            start = MenuDefaults.RowPadding,
            end = MenuDefaults.RowPadding,
            top = MenuDefaults.HeaderTop,
            bottom = MenuDefaults.HeaderBottom,
        ),
        style = HosannaTheme.typography.overline.copy(color = HosannaTheme.colors.onSurfaceFaint),
    )
}

@Composable
private fun MenuDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = MenuDefaults.Padding, vertical = MenuDefaults.DividerMargin)
            .height(FieldDefaults.Border)
            .background(HosannaTheme.colors.outlineVariant),
    )
}

@Composable
private fun MenuRow(
    item: MenuEntry.Item,
    selected: Boolean,
    active: Boolean,
    onHover: () -> Unit,
    onClick: () -> Unit,
) {
    val colors = HosannaTheme.colors
    val typography = HosannaTheme.typography
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    LaunchedEffect(hovered) { if (hovered && item.enabled) onHover() }
    val shape = HosannaTheme.shapes.m
    val ink = when {
        !item.enabled -> colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        selected -> colors.primary
        else -> colors.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (item.supporting != null) MenuDefaults.TwoLineRowHeight else MenuDefaults.RowHeight)
            .background(colors.menuRowContainer(selected, item.enabled && (hovered || active)), shape)
            .then(if (active && item.enabled) Modifier.keyboardRing(colors.primary, shape) else Modifier)
            .clickable(source, null, item.enabled, role = Role.DropdownList, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = MenuDefaults.RowPadding),
        horizontalArrangement = Arrangement.spacedBy(MenuDefaults.Gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            BasicText(
                item.label,
                style = (if (selected) typography.bodyStrong else typography.body).copy(color = ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            item.supporting?.let {
                BasicText(it, style = typography.caption.copy(color = colors.onSurfaceFaint), maxLines = 1)
            }
        }
        item.count?.let { CountBadge(it, selected) }
        if (selected) FieldGlyph(painterResource(Res.drawable.ic_check), colors.primary, MenuDefaults.Check)
    }
}

@Composable
private fun CountBadge(text: String, selected: Boolean) {
    val colors = HosannaTheme.colors
    Box(
        modifier = Modifier
            .height(MenuDefaults.BadgeHeight)
            .background(
                if (selected) {
                    colors.primary.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_CONTAINER)
                } else {
                    colors.background
                },
                HosannaTheme.shapes.s,
            )
            .padding(horizontal = MenuDefaults.BadgePadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = HosannaTheme.typography.captionStrong.copy(
                color = if (selected) colors.primary else colors.onSurfaceFaint,
            ),
        )
    }
}

private fun Modifier.keyboardRing(color: Color, shape: Shape): Modifier =
    border(MenuDefaults.KeyboardRing, color, shape)
