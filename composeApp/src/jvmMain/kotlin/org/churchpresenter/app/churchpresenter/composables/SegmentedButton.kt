package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.theme.ElevationPalette
import org.churchpresenter.theme.RaisedFill
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.sunken
import androidx.compose.ui.graphics.graphicsLayer

/** Tight enough that a two-line label still fits a segment sized for one and a bit. */
private const val LINE_HEIGHT_RATIO = 1.15f

private const val ICON_SCALE = 1.1f
private const val SEGMENT_HOVER_ALPHA = 0.08f
private val SEGMENT_HOVER_SHIFT = 1.dp
private val TRACK_INSET = 3.dp
private val SEGMENT_GAP = 9.dp
private val DIVIDER_WIDTH = 1.dp
private const val DIVIDER_HEIGHT_FRACTION = 0.5f
private val TRACK_RADIUS = 10.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> SegmentedButton(
    items: List<SegmentedButtonItem<T>>,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    modifier: Modifier = Modifier,
    buttonWidth: Dp = 40.dp,
    buttonHeight: Dp = 40.dp,
    fontSize: TextUnit = 16.sp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    compactColumns: Int? = null,
    /** How many lines a label may take before it is ellipsized. One, unless the caller says more. */
    maxLines: Int = 1,
) {
    require(items.isNotEmpty()) { "SegmentedButton requires at least one item" }

    if (compactColumns == null) {
        SegmentedButtonGrid(
            items = items,
            columns = items.size,
            selectedValue = selectedValue,
            onValueChange = onValueChange,
            buttonWidth = buttonWidth,
            buttonHeight = buttonHeight,
            fontSize = fontSize,
            contentPadding = contentPadding,
            maxLines = maxLines,
            modifier = modifier
        )
    } else {
        BoxWithConstraints(modifier = modifier) {
            val fullRowWidth = buttonWidth * items.size
            val columns = if (maxWidth >= fullRowWidth) items.size else compactColumns
            SegmentedButtonGrid(
                items = items,
                columns = columns,
                selectedValue = selectedValue,
                onValueChange = onValueChange,
                buttonWidth = buttonWidth,
                buttonHeight = buttonHeight,
                fontSize = fontSize,
                contentPadding = contentPadding,
                maxLines = maxLines
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun <T> SegmentedButtonGrid(
    items: List<SegmentedButtonItem<T>>,
    columns: Int,
    selectedValue: T,
    onValueChange: (T) -> Unit,
    buttonWidth: Dp,
    buttonHeight: Dp,
    fontSize: TextUnit,
    contentPadding: PaddingValues,
    maxLines: Int = 1,
    modifier: Modifier = Modifier
) {
    val palette = elevationPalette()
    val accent = LocalSegmentedButtonTone.current == SegmentedButtonTone.ACCENT
    val selectedFill = if (accent) palette.accent else palette.selected
    // The track keeps the footprint the flat row had -- columns x buttonWidth by rows x buttonHeight
    // -- so every call site keeps its layout; the track's inset comes out of the segments.
    val segmentWidth = buttonWidth - (TRACK_INSET * 2 + SEGMENT_GAP * (columns - 1)) / columns
    val segmentHeight = buttonHeight - TRACK_INSET * 2
    val trackShape = RoundedCornerShape(TRACK_RADIUS)
    val style = SegmentStyle(fontSize, contentPadding, maxLines)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.chunked(columns).forEach { rowItems ->
            Row(
                modifier = Modifier.sunken(trackShape, palette).padding(TRACK_INSET),
            ) {
                rowItems.forEachIndexed { index, item ->
                    val isSelected = selectedValue == item.value
                    if (index > 0) {
                        // The gap between two segments, with a hairline in it when neither side is
                        // raised -- without it two flat labels in the track run into one another.
                        val divided = !isSelected && selectedValue != rowItems[index - 1].value
                        SegmentDivider(visible = divided, height = segmentHeight)
                    }

                    val button: @Composable () -> Unit = {
                        val size = Modifier.size(segmentWidth, segmentHeight)
                        Segment(item, isSelected, selectedFill, palette, size, style) { onValueChange(item.value) }
                    }

                    if (item.tooltip != null) {
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    color = MaterialTheme.colorScheme.inverseSurface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    tonalElevation = 4.dp
                                ) {
                                    Text(
                                        text = item.tooltip,
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            },
                            tooltipPlacement = TooltipPlacement.ComponentRect(
                                anchor = Alignment.BottomCenter,
                                offset = DpOffset(0.dp, 4.dp)
                            )
                        ) {
                            button()
                        }
                    } else {
                        button()
                    }
                }
            }
        }
    }
}

/** How a segment sets its label, the same for every segment of one control. */
private class SegmentStyle(val fontSize: TextUnit, val contentPadding: PaddingValues, val maxLines: Int)

/** One option: flat in the track, or raised in [selectedFill] when it is the chosen one. */
@Composable
private fun <T> Segment(
    item: SegmentedButtonItem<T>,
    isSelected: Boolean,
    selectedFill: RaisedFill,
    palette: ElevationPalette,
    modifier: Modifier,
    style: SegmentStyle,
    onClick: () -> Unit,
) {
    val segmentShape = RoundedCornerShape(TRACK_RADIUS - TRACK_INSET)
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val ink = when {
        isSelected -> selectedFill.ink
        hovered -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .then(
                when {
                    isSelected -> Modifier.raised(
                        segmentShape, selectedFill, palette, hovered = hovered, lift = 2.dp,
                    )
                    // A faint wash under the pointer, so an unchosen option shows it can be picked.
                    hovered -> Modifier.graphicsLayer { translationY = -SEGMENT_HOVER_SHIFT.toPx() }
                        .clip(segmentShape).background(ink.copy(alpha = SEGMENT_HOVER_ALPHA))
                    else -> Modifier.clip(segmentShape)
                }
            )
            .hoverable(interaction)
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick
            )
            .then(item.testTag?.let { Modifier.testTag(it) } ?: Modifier)
            .padding(style.contentPadding),
        contentAlignment = Alignment.Center
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            if (item.icon != null) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = ink,
                    modifier = Modifier.size(style.fontSize.value.dp * ICON_SCALE)
                )
            } else {
                Text(
                    text = item.label,
                    color = ink,
                    fontSize = style.fontSize,
                    fontWeight = FontWeight.Bold,
                    // Only tightened where a label may actually take two
                    // lines. Setting it on a single-line label shrinks the line
                    // box around the glyphs, which the button then centres by
                    // the box rather than by the type -- so the word sits off
                    // centre in a control whose whole job is to line up.
                    lineHeight = if (style.maxLines > 1) {
                        style.fontSize * LINE_HEIGHT_RATIO
                    } else {
                        TextUnit.Unspecified
                    },
                    textAlign = TextAlign.Center,
                    // Ellipsized rather than clipped. A segment too narrow for
                    // its label used to cut it mid-glyph, which reads as a
                    // rendering fault rather than as a label that does not fit.
                    overflow = TextOverflow.Ellipsis,
                    maxLines = style.maxLines
                )
            }
        }
    }
}

/** The [SEGMENT_GAP]-wide space between two segments, drawn with a short hairline when [visible]. */
@Composable
private fun SegmentDivider(visible: Boolean, height: Dp) {
    Box(modifier = Modifier.size(SEGMENT_GAP, height), contentAlignment = Alignment.Center) {
        if (visible) {
            Box(
                Modifier
                    .size(DIVIDER_WIDTH, height * DIVIDER_HEIGHT_FRACTION)
                    .background(elevationPalette().wellBorder)
            )
        }
    }
}
