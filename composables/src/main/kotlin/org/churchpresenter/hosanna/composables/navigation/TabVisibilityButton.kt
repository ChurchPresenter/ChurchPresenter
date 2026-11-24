package org.churchpresenter.hosanna.composables.navigation

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.window.Popup
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.awaitCancellation
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.SegmentedButton
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.MenuDefaults
import org.churchpresenter.hosanna.composables.defaults.TabMenuDefaults
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.field.MenuDivider
import org.churchpresenter.hosanna.composables.field.MenuHeader
import org.churchpresenter.hosanna.composables.field.MenuSurface
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.MenuPositionProvider
import org.churchpresenter.hosanna.composables.model.NavigationTab
import org.churchpresenter.hosanna.composables.model.SegmentedButtonSize
import org.churchpresenter.hosanna.composables.model.SegmentedOption
import org.churchpresenter.hosanna.composables.model.TabMenuLabels
import org.churchpresenter.hosanna.composables.model.TabStyle
import org.churchpresenter.hosanna.composables.model.canHideTab
import org.churchpresenter.hosanna.composables.model.moveTab
import org.churchpresenter.hosanna.composables.selection.Checkbox
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_drag_handle
import org.churchpresenter.icons.generated.resources.ic_visibility
import org.jetbrains.compose.resources.painterResource

@Composable
fun TabVisibilityButton(
    tabs: List<NavigationTab>,
    hidden: Set<String>,
    onVisibilityChange: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    style: TabStyle,
    onStyleChange: (TabStyle) -> Unit,
    labels: TabMenuLabels,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(open) {
        if (open) {
            val press = PressInteraction.Press(Offset.Zero)
            interactionSource.emit(press)
            try {
                awaitCancellation()
            } finally {
                interactionSource.tryEmit(PressInteraction.Release(press))
            }
        }
    }
    Box(modifier) {
        IconButton(
            icon = painterResource(Res.drawable.ic_visibility),
            contentDescription = labels.button,
            onClick = { open = !open },
            interactionSource = interactionSource,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = ButtonIntent.Neutral,
        )
        if (open) {
            val gap = with(LocalDensity.current) { MenuDefaults.Gap.roundToPx() }
            Popup(
                popupPositionProvider = remember(gap) { MenuPositionProvider(gap, alignEnd = true) },
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                TabVisibilityMenu(tabs, hidden, onVisibilityChange, onReorder, onReset, style, onStyleChange, labels)
            }
        }
    }
}

@Composable
private fun TabVisibilityMenu(
    tabs: List<NavigationTab>,
    hidden: Set<String>,
    onVisibilityChange: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
    onReset: () -> Unit,
    style: TabStyle,
    onStyleChange: (TabStyle) -> Unit,
    labels: TabMenuLabels,
) {
    val keys = tabs.map { it.key }
    val styles = TabStyle.entries
    MenuSurface(TabMenuDefaults.Width) {
        MenuHeader(labels.style.title)
        SegmentedButton(
            options = with(labels.style) { listOf(iconAndText, text, icon) }.map { SegmentedOption(it) },
            selectedIndex = styles.indexOf(style),
            onSelectionChange = { onStyleChange(styles[it]) },
            modifier = Modifier.padding(horizontal = TabMenuDefaults.RowPadding),
            size = SegmentedButtonSize.Small,
            fillWidth = true,
        )
        MenuDivider()
        MenuHeader(labels.showTabs)
        val currentKeys by rememberUpdatedState(keys)
        tabs.forEach { tab ->
            key(tab.key) {
                TabRow(
                    tab = tab,
                    visible = tab.key !in hidden,
                    canHide = canHideTab(tab.key, keys, hidden),
                    labels = labels,
                    onVisibilityChange = { onVisibilityChange(tab.key, it) },
                    onMove = { by -> onReorder(moveTab(currentKeys, tab.key, by)) },
                    first = tab.key == keys.first(),
                    last = tab.key == keys.last(),
                )
            }
        }
        MenuDivider()
        StandardButton(
            labels.reset,
            onReset,
            Modifier.padding(horizontal = TabMenuDefaults.SectionGap),
            hierarchy = ButtonHierarchy.Tertiary,
        )
    }
}

@Composable
private fun TabRow(
    tab: NavigationTab,
    visible: Boolean,
    canHide: Boolean,
    labels: TabMenuLabels,
    onVisibilityChange: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    first: Boolean,
    last: Boolean,
) {
    val rowSource = remember { MutableInteractionSource() }
    val hovered by rowSource.collectIsHoveredAsState()
    var offset by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    val move by rememberUpdatedState(onMove)
    val canMoveUp by rememberUpdatedState(!first)
    val canMoveDown by rememberUpdatedState(!last)
    val row = with(LocalDensity.current) { MenuDefaults.RowHeight.toPx() }
    val colors = HosannaTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = if (dragging) offset else 0f
                if (dragging) {
                    scaleX = TabMenuDefaults.DRAG_LIFT
                    scaleY = TabMenuDefaults.DRAG_LIFT
                }
            }
            .then(
                if (dragging) Modifier.background(colors.surfaceContainerHighest, HosannaTheme.shapes.m) else Modifier,
            )
            .hoverable(rowSource)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || !event.isAltPressed) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionUp -> if (canMoveUp) move(-1)
                    Key.DirectionDown -> if (canMoveDown) move(1)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .semantics {
                customActions = listOfNotNull(
                    if (!first) CustomAccessibilityAction(labels.move.up) { move(-1); true } else null,
                    if (!last) CustomAccessibilityAction(labels.move.down) { move(1); true } else null,
                )
            }
            .padding(start = TabMenuDefaults.RowPadding, end = TabMenuDefaults.RowPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Tooltip(labels.lastTab, Modifier.weight(1f), enabled = !canHide) {
            Checkbox(
                checked = visible,
                onCheckedChange = onVisibilityChange,
                label = tab.label,
                modifier = Modifier.fillMaxWidth(),
                enabled = canHide,
            )
        }
        Image(
            painterResource(Res.drawable.ic_drag_handle),
            null,
            Modifier
                .size(TabMenuDefaults.Handle)
                .alpha(if (hovered || dragging) 1f else 0f)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { dragging = true; offset = 0f },
                        onDragEnd = { dragging = false; offset = 0f },
                        onDragCancel = { dragging = false; offset = 0f },
                    ) { change, amount ->
                        change.consume()
                        offset += amount.y
                        when {
                            offset > row / 2 && canMoveDown -> { move(1); offset -= row }
                            offset < -row / 2 && canMoveUp -> { move(-1); offset += row }
                        }
                    }
                },
            colorFilter = ColorFilter.tint(colors.onSurfaceFaint),
        )
    }
}
