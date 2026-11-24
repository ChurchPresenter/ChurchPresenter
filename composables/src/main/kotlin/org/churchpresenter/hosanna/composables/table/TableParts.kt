package org.churchpresenter.hosanna.composables.table

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.defaults.TableDefaults
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.ListRowColors
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.listRowColors
import org.churchpresenter.hosanna.composables.list.RowFrame
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.TableColumn
import org.churchpresenter.hosanna.composables.model.TableRow
import org.churchpresenter.hosanna.composables.selection.TriStateCheckbox
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TableRowItem(
    row: TableRow,
    layout: TableLayout,
    selected: Boolean,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    compact: Boolean,
    interactionSource: MutableInteractionSource,
    keepActions: Boolean,
    actions: (@Composable RowScope.(String) -> Unit)?,
    onClick: () -> Unit,
    onOpen: () -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(true, false, hovered, pressed, focused)
    val colors = ThemeColors.current.listRowColors(selected, live = false, marker = false, state = state)
    val height = if (compact) TableDefaults.CompactRowHeight else TableDefaults.RowHeight
    val lines = !selected && state == ButtonState.Rest
    val showActions = keepActions || selected || state != ButtonState.Rest
    val click by rememberUpdatedState(onClick)
    val open by rememberUpdatedState(onOpen)
    val stableClick = remember { { click() } }
    val stableOpen = remember { { open() } }
    RowFrame(colors, height, dragging = false, modifier = Modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = height)
                .semantics { this.selected = selected }
                .combinedClickable(
                    interactionSource,
                    null,
                    role = Role.Button,
                    onDoubleClick = stableOpen,
                    onClick = stableClick,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (checked != null && onCheckedChange != null) {
                BulkCell(if (checked) ToggleableState.On else ToggleableState.Off, layout.nameOf(row)) {
                    onCheckedChange(it)
                }
            }
            layout.columns.forEachIndexed { index, column ->
                BodyCell(row.cells.getOrElse(index) { "" }, column, colors, cellWidth(layout, column))
                if (index < layout.columns.lastIndex) {
                    CellLine(if (lines) ThemeColors.current.outlineVariant else Color.Transparent)
                }
            }
            layout.actions?.let { width ->
                Row(
                    Modifier.width(width).padding(end = TableDefaults.ActionGap),
                    horizontalArrangement = Arrangement.spacedBy(TableDefaults.ActionGap, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showActions) actions?.invoke(this, row.key)
                }
            }
        }
    }
}

@Composable
private fun BodyCell(text: String, column: TableColumn, colors: ListRowColors, modifier: Modifier) {
    var cut by remember { mutableStateOf(false) }
    Box(
        modifier.padding(horizontal = TableDefaults.CellPadding),
        contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Tooltip(text, enabled = cut) {
            BasicText(
                text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { cut = it.hasVisualOverflow },
                style = HosannaTheme.typography.body.copy(
                    color = if (column.secondary) colors.supporting else colors.content,
                ),
            )
        }
    }
}

@Composable
private fun CellLine(color: Color) {
    Box(Modifier.width(TableDefaults.DividerGrip), contentAlignment = Alignment.Center) {
        Box(Modifier.width(TableDefaults.Divider).height(TableDefaults.HeaderDivider).background(color))
    }
}

@Composable
internal fun BulkCell(state: ToggleableState, label: String, onChange: (Boolean) -> Unit) {
    Box(Modifier.width(TableDefaults.CheckColumn), contentAlignment = Alignment.Center) {
        TriStateCheckbox(
            state,
            { onChange(state != ToggleableState.On) },
            label = "",
            modifier = Modifier.semantics { contentDescription = label },
            compact = true,
        )
    }
}

@Composable
internal fun TableSkeleton(layout: TableLayout) {
    val colors = ThemeColors.current
    val sweep by rememberInfiniteTransition().animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            tween(TableDefaults.SHIMMER_MILLIS, easing = LinearEasing),
            RepeatMode.Restart,
        ),
    )
    Column {
        repeat(TableDefaults.SKELETON_ROWS) {
            Row(
                Modifier.fillMaxWidth().height(TableDefaults.RowHeight),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (layout.checkbox) Box(Modifier.width(TableDefaults.CheckColumn))
                layout.columns.forEachIndexed { index, column ->
                    Box(
                        cellWidth(layout, column).padding(horizontal = TableDefaults.CellPadding),
                        contentAlignment = if (column.numeric) Alignment.CenterEnd else Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(if (column.numeric) SHORT_BAR else LONG_BAR)
                                .height(TableDefaults.SkeletonBar)
                                .background(colors.outlineVariant, RoundedCornerShape(TableDefaults.SkeletonBar))
                                .drawBehind {
                                    val start = sweep * size.width
                                    drawRect(
                                        Brush.horizontalGradient(
                                            listOf(Color.Transparent, colors.surfaceContainerLow, Color.Transparent),
                                            start,
                                            start + size.width,
                                        ),
                                    )
                                },
                        )
                    }
                    if (index < layout.columns.lastIndex) Box(Modifier.width(TableDefaults.DividerGrip))
                }
                layout.actions?.let { Box(Modifier.width(it)) }
            }
        }
    }
}

private const val SHORT_BAR = 0.4f
private const val LONG_BAR = 0.7f

/**
 * The line a table shows when it has no rows, with an optional text button.
 *
 * @param text the message.
 * @param modifier applied to the outermost layout.
 * @param actionText turns on the button, such as "Clear search".
 * @param onAction called when that button is pressed.
 */
@Composable
fun TableEmpty(text: String, modifier: Modifier = Modifier, actionText: String? = null, onAction: () -> Unit = {}) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BasicText(text, style = HosannaTheme.typography.body.copy(color = ThemeColors.current.onSurfaceVariant))
        actionText?.let { StandardButton(it, onAction, hierarchy = ButtonHierarchy.Tertiary) }
    }
}
