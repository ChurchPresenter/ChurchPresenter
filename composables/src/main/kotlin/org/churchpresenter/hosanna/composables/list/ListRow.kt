package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.chip.Tag
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.ListRowColors
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.listRowColors
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.ClickTargets
import org.churchpresenter.hosanna.composables.foundation.ProvideClickTargets
import org.churchpresenter.hosanna.composables.foundation.firstPassClick
import org.churchpresenter.hosanna.composables.foundation.collectIsFocusVisibleAsState

/**
 * A list row with one to three lines, optional leading and trailing parts, and actions that show on hover.
 *
 * @param headline the first line.
 * @param onClick called when the row is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param supporting an optional second line.
 * @param threeLine lets [supporting] wrap to two lines.
 * @param leading optional content before the text, such as an icon tile, checkbox or number.
 * @param trailing optional content after the text, such as meta text or a tag.
 * @param actions buttons shown on hover, focus or selection.
 * @param keepActions keeps [actions] visible.
 * @param selected marks the row as picked.
 * @param liveTag marks the row as on screen with a red bar and this tag.
 * @param dragging lifts the row while it is dragged.
 * @param onDoubleClick called on a double-click, such as to put the row live; its first click still
 * reaches [onClick].
 * @param enabled whether the row can be pressed.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    headline: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    supporting: String? = null,
    threeLine: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    keepActions: Boolean = false,
    selected: Boolean = false,
    liveTag: String? = null,
    dragging: Boolean = false,
    onDoubleClick: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val click by rememberUpdatedState(onClick)
    val doubleClick by rememberUpdatedState(onDoubleClick)
    val stableClick = remember { { click() } }
    val stableDoubleClick = remember { { doubleClick?.invoke() ?: Unit } }
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusVisibleAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = ThemeColors.current.listRowColors(selected, liveTag != null, marker = false, state = state)
    val height = when {
        supporting == null -> ListDefaults.OneLine
        threeLine -> ListDefaults.ThreeLine
        else -> ListDefaults.TwoLine
    }
    val showActions = actions != null && (keepActions || selected || state == ButtonState.Hovered ||
        state == ButtonState.Pressed || state == ButtonState.Focused)
    val targets = remember { ClickTargets() }
    RowFrame(colors, height, dragging, modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = height)
                .semantics { this.selected = selected }
                .firstPassClick(
                    enabled,
                    stableClick,
                    if (onDoubleClick != null) stableDoubleClick else null,
                    container = targets,
                )
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    enabled = enabled,
                    role = Role.Button,
                    onDoubleClick = if (onDoubleClick != null) stableDoubleClick else null,
                    onClick = stableClick,
                )
                .padding(horizontal = ListDefaults.RowPadding),
            horizontalArrangement = Arrangement.spacedBy(ListDefaults.PartGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProvideClickTargets(targets) {
                leading?.invoke()
                RowText(headline, supporting, threeLine, selected, colors, Modifier.weight(1f))
                trailing?.invoke(this)
                liveTag?.let { Tag(it, color = ChipColor.Live) }
                if (showActions) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ListDefaults.ActionGap), content = actions)
                }
            }
        }
    }
}

@Composable
internal fun RowFrame(
    colors: ListRowColors,
    height: Dp,
    dragging: Boolean,
    modifier: Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(ListDefaults.RowRadius)
    Box(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = height)
            .then(
                if (dragging) {
                    Modifier
                        .graphicsLayer { rotationZ = ListDefaults.DRAG_TILT }
                        .hosannaElevation(HosannaLevel.Level4, shape, HosannaTheme.elevation)
                        .background(ThemeColors.current.surfaceContainerHigh, shape)
                } else {
                    Modifier
                },
            )
            .clip(shape)
            .background(colors.container)
            .then(colors.ring?.let { Modifier.border(ListDefaults.Ring, it, shape) } ?: Modifier),
        contentAlignment = Alignment.CenterStart,
    ) {
        content()
        colors.bar?.let {
            Box(Modifier.matchParentSize()) {
                Box(Modifier.width(ListDefaults.Bar).fillMaxHeight().background(it))
            }
        }
    }
}

@Composable
private fun RowText(
    headline: String,
    supporting: String?,
    threeLine: Boolean,
    selected: Boolean,
    colors: ListRowColors,
    modifier: Modifier,
) {
    val typography = HosannaTheme.typography
    val strong = supporting != null || selected
    Column(modifier) {
        BasicText(
            headline,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = (if (strong) typography.bodyStrong else typography.body).copy(color = colors.content),
        )
        supporting?.let {
            BasicText(
                it,
                maxLines = if (threeLine) 2 else 1,
                overflow = TextOverflow.Ellipsis,
                style = typography.caption.copy(color = colors.supporting),
            )
        }
    }
}

/**
 * The surface a list or table sits in, with a hairline edge and the inset rows sit inside.
 *
 * @param modifier applied to the outermost layout.
 * @param content the list.
 */
@Composable
fun ListPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(ListDefaults.PanelRadius)
    val colors = ThemeColors.current
    Box(
        modifier
            .clip(shape)
            .background(colors.surface)
            .border(ListDefaults.Line, colors.outlineVariant, shape)
            .padding(ListDefaults.PanelInset),
    ) {
        content()
    }
}

/**
 * A line between multi-line rows.
 *
 * @param modifier applied to the outermost layout.
 */
@Composable
fun ListDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ListDefaults.RowPadding)
            .height(ListDefaults.Line)
            .background(ThemeColors.current.outlineVariant),
    )
}

/**
 * The line that shows where a dragged row will drop.
 *
 * @param modifier applied to the outermost layout.
 */
@Composable
fun ListDropIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(ListDefaults.DropLine)
            .background(ThemeColors.current.primary, RoundedCornerShape(ListDefaults.DropLine)),
    )
}
