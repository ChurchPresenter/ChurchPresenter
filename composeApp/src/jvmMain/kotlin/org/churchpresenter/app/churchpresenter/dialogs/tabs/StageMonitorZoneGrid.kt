package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.color
import org.churchpresenter.app.churchpresenter.composables.MetronomeDot
import org.churchpresenter.app.churchpresenter.composables.NumberSettingsTextField
import org.churchpresenter.app.churchpresenter.composables.TvScreenBox
import org.churchpresenter.app.churchpresenter.composables.toAlignment
import org.churchpresenter.app.churchpresenter.composables.tvScreenBoxWidthFor
import org.churchpresenter.settings.MetronomePosition
import org.churchpresenter.settings.StageMonitorSettings
import org.churchpresenter.settings.StageMonitorStyleZone
import org.churchpresenter.settings.StageMonitorZone
import org.churchpresenter.settings.toZone
import org.churchpresenter.app.churchpresenter.utils.calculateAutoFitFontSize
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.settings.STAGE_ZONE_FULL_PERCENT
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_width
import churchpresenter.composeapp.generated.resources.percent_suffix
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import java.awt.Cursor
import org.churchpresenter.settings.StageMonitorZoneSizes
import org.churchpresenter.settings.StageMonitorLayout
import org.churchpresenter.settings.STAGE_ZONE_MIN_WIDTH_PERCENT
import org.churchpresenter.settings.STAGE_ZONE_MIN_HEIGHT_PERCENT
import churchpresenter.composeapp.generated.resources.stage_monitor_zone_row_height
import churchpresenter.composeapp.generated.resources.stage_monitor_size_selected
import churchpresenter.composeapp.generated.resources.stage_monitor_size_even_row
import churchpresenter.composeapp.generated.resources.stage_monitor_size_even_all
import churchpresenter.composeapp.generated.resources.stage_monitor_size_even
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.clickable
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.BoxScope

/**
 * The stage monitor grid on the Screen Content card, and the controls that resize it.
 *
 * Separate from the tab because it is one coherent thing — a picture of the monitor you can click
 * a zone in and drag an edge of — and because the tab it sits on is already the app's longest
 * settings screen. Nothing here reads [org.churchpresenter.settings.StageMonitorSettings]: the grid
 * takes the layout, the sizes and a lookup for what each zone shows, so it draws a stage monitor
 * rather than a settings document.
 */

private const val PREVIEW_WIDTH_FRACTION = 0.9f
private val STAGE_PREVIEW_MAX_HEIGHT = 360.dp
private const val ZONE_ALPHA = 0.10f
private const val ZONE_BORDER_ALPHA = 0.45f
private const val ZONE_CAPTION_ALPHA = 0.6f
private const val SELECTED_ZONE_ALPHA = 0.38f
private const val SIZE_FIELD_WIDTH = 132
private const val SIZE_GRIP_ALPHA = 0.5f
private const val FIELD_LABEL_SIZE = 10
private const val FIELD_LABEL_ALPHA = 0.5f
private val SIZE_HANDLE_THICKNESS = 12.dp
private val SIZE_HANDLE_INSET = 3.dp
private val SIZE_GRIP_LENGTH = 22.dp
private val SIZE_GRIP_THICKNESS = 3.dp

/** The monitor drawn to scale, every zone clickable and every shared edge draggable. */
@Composable
internal fun ZoneGrid(
    layout: StageMonitorLayout,
    sizes: StageMonitorZoneSizes,
    screenAspect: Float,
    selected: StageMonitorStyleZone,
    metronomePosition: MetronomePosition,
    /** Composable: the zone labels it joins come from string resources. */
    contentsOf: @Composable (StageMonitorZone) -> String,
    onSelect: (StageMonitorStyleZone) -> Unit,
    onWidthChange: (StageMonitorStyleZone, Float) -> Unit,
    onHeightChange: (StageMonitorStyleZone, Float) -> Unit,
) {
    val percentSuffix = stringResource(Res.string.percent_suffix)
    TvScreenBox(
        modifier = Modifier
            .fillMaxWidth(PREVIEW_WIDTH_FRACTION)
            .widthIn(max = tvScreenBoxWidthFor(STAGE_PREVIEW_MAX_HEIGHT, screenAspect)),
        screenAspectRatio = screenAspect,
        bezelColor = stageMonitorBezelColor(),
        screenColor = Color.Black,
    ) {
        // Inset, so the screen itself shows around the zones instead of being papered over.
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            val gridWidth = maxWidth
            val gridHeight = maxHeight

            Column(modifier = Modifier.fillMaxSize()) {
                layout.rows.forEachIndexed { rowIndex, layoutRow ->
                    Row(modifier = Modifier.fillMaxWidth().weight(sizes.rowHeights[rowIndex])) {
                        layoutRow.cells.forEachIndexed { cellIndex, cell ->
                            val zone = cell.slot.toZone()
                            val width = sizes.rowCellWidths[rowIndex][cellIndex]
                            ZoneLabelCell(
                                caption = zoneLabel(zone),
                                text = contentsOf(zone),
                                // "50%×67%" — the zone's own width, and the height of its row.
                                // Unspaced: a cell can be a tenth of a row wide, and the two
                                // spaces are what tip that line into wrapping and being clipped.
                                size = "${width.roundToInt()}$percentSuffix\u00D7" +
                                    "${sizes.rowHeights[rowIndex].roundToInt()}$percentSuffix",
                                selected = cell.slot == selected,
                                onClick = { onSelect(cell.slot) },
                                modifier = Modifier.weight(width)
                            )
                        }
                    }
                }
            }

            ZoneSizeDividers(
                layout = layout,
                sizes = sizes,
                gridWidth = gridWidth,
                gridHeight = gridHeight,
                onWidthChange = onWidthChange,
                onHeightChange = onHeightChange,
            )
        }
        metronomePosition.toAlignment()?.let { alignment ->
            MetronomeDot(
                bpm = 100,
                active = true,
                size = 24.dp,
                modifier = Modifier.align(alignment).padding(6.dp)
            )
        }
    }
}

/** The selected zone's numbers, and the two ways to even a lopsided grid back out. */
@Composable
internal fun ZoneSizeControls(
    selectedLabel: String,
    widthPercent: Float,
    heightPercent: Float,
    onWidthChange: (Float) -> Unit,
    onHeightChange: (Float) -> Unit,
    onEvenRow: () -> Unit,
    onEvenAll: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SelectedZoneField(
            label = stringResource(Res.string.stage_monitor_size_selected),
            value = selectedLabel,
            modifier = Modifier.weight(1f)
        )
        NumberSettingsTextField(
            modifier = Modifier.width(SIZE_FIELD_WIDTH.dp),
            label = stringResource(Res.string.stage_monitor_zone_width),
            initialText = widthPercent.roundToInt(),
            range = STAGE_ZONE_MIN_WIDTH_PERCENT.toInt()..STAGE_ZONE_FULL_PERCENT.toInt(),
            onValueChange = { percent -> onWidthChange(percent.toFloat()) },
        )
        NumberSettingsTextField(
            modifier = Modifier.width(SIZE_FIELD_WIDTH.dp),
            label = stringResource(Res.string.stage_monitor_zone_row_height),
            initialText = heightPercent.roundToInt(),
            range = STAGE_ZONE_MIN_HEIGHT_PERCENT.toInt()..STAGE_ZONE_FULL_PERCENT.toInt(),
            onValueChange = { percent -> onHeightChange(percent.toFloat()) },
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(Res.string.stage_monitor_size_even),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        EvenOutButton(
            label = stringResource(Res.string.stage_monitor_size_even_row),
            onClick = onEvenRow,
        )
        EvenOutButton(
            label = stringResource(Res.string.stage_monitor_size_even_all),
            onClick = onEvenAll,
        )
    }
}

/**
 * A draggable line on every shared edge of the grid, drawn over it.
 *
 * Over rather than in: a divider belongs to the two zones either side of it, not to one of them, and
 * a child of the left-hand cell would be painted under the right-hand one. The positions come from
 * the same percentages the cells are weighted by, so what you grab is always the line you can see.
 */
@Composable
private fun BoxScope.ZoneSizeDividers(
    layout: StageMonitorLayout,
    sizes: StageMonitorZoneSizes,
    gridWidth: Dp,
    gridHeight: Dp,
    onWidthChange: (StageMonitorStyleZone, Float) -> Unit,
    onHeightChange: (StageMonitorStyleZone, Float) -> Unit,
) {
    var rowTop = 0f
    layout.rows.forEachIndexed { rowIndex, layoutRow ->
        val top = gridHeight * (rowTop / STAGE_ZONE_FULL_PERCENT)
        val rowHeight = gridHeight * (sizes.rowHeights[rowIndex] / STAGE_ZONE_FULL_PERCENT)

        var cellRight = 0f
        layoutRow.cells.dropLast(1).forEachIndexed { cellIndex, cell ->
            cellRight += sizes.rowCellWidths[rowIndex][cellIndex]
            ResizeDivider(
                orientation = Orientation.Horizontal,
                span = gridWidth,
                percent = sizes.rowCellWidths[rowIndex][cellIndex],
                onPercentChange = { percent -> onWidthChange(cell.slot, percent) },
                modifier = Modifier
                    .offset(
                        x = gridWidth * (cellRight / STAGE_ZONE_FULL_PERCENT) - SIZE_HANDLE_THICKNESS / 2,
                        y = top + SIZE_HANDLE_INSET
                    )
                    .size(SIZE_HANDLE_THICKNESS, (rowHeight - SIZE_HANDLE_INSET * 2).coerceAtLeast(0.dp))
                    // Nothing else identifies a divider: it draws no text and names no action.
                    .testTag(zoneSizeDividerTag(cell.slot))
            )
        }

        rowTop += sizes.rowHeights[rowIndex]
        if (rowIndex < layout.rows.lastIndex) {
            ResizeDivider(
                orientation = Orientation.Vertical,
                span = gridHeight,
                percent = sizes.rowHeights[rowIndex],
                // Every zone on the row reports the same height, so any of them names the row.
                onPercentChange = { percent -> onHeightChange(layoutRow.cells.first().slot, percent) },
                modifier = Modifier
                    .offset(x = SIZE_HANDLE_INSET, y = top + rowHeight - SIZE_HANDLE_THICKNESS / 2)
                    .size((gridWidth - SIZE_HANDLE_INSET * 2).coerceAtLeast(0.dp), SIZE_HANDLE_THICKNESS)
                    .testTag(zoneSizeDividerTag(layoutRow.cells.first().slot, horizontal = false))
            )
        }
    }
}

/**
 * The tag on the divider that resizes [zone] — its right-hand edge, or the bottom of its row.
 *
 * A divider draws no text and publishes no action, so a tag is the only handle there is on it.
 */
internal fun zoneSizeDividerTag(zone: StageMonitorStyleZone, horizontal: Boolean = true): String =
    "zone_size_divider_${if (horizontal) "w" else "h"}_${zone.name}"

/**
 * A shared edge, draggable.
 *
 * The travel is measured from where the drag started rather than summed percentage by percentage:
 * each move re-derives the whole distance, so rounding to a whole percent cannot accumulate into
 * drift over a long drag. The callbacks are read through [rememberUpdatedState] because the block
 * `draggable` retains is the one from the composition that created it.
 */
@Composable
private fun ResizeDivider(
    orientation: Orientation,
    span: Dp,
    percent: Float,
    onPercentChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val currentPercent by rememberUpdatedState(percent)
    val currentSpan by rememberUpdatedState(span)
    val currentChange by rememberUpdatedState(onPercentChange)
    var startPercent by remember { mutableFloatStateOf(0f) }
    var travel by remember { mutableFloatStateOf(0f) }

    val horizontal = orientation == Orientation.Horizontal
    Box(
        modifier = modifier
            .pointerHoverIcon(
                PointerIcon(Cursor(if (horizontal) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR))
            )
            .draggable(
                orientation = orientation,
                state = rememberDraggableState { delta ->
                    travel += delta
                    val moved = with(density) { travel.toDp() } / currentSpan * STAGE_ZONE_FULL_PERCENT
                    currentChange(startPercent + moved)
                },
                onDragStarted = {
                    startPercent = currentPercent
                    travel = 0f
                },
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(
                    width = if (horizontal) SIZE_GRIP_THICKNESS else SIZE_GRIP_LENGTH,
                    height = if (horizontal) SIZE_GRIP_LENGTH else SIZE_GRIP_THICKNESS
                )
                .background(Color.White.copy(alpha = SIZE_GRIP_ALPHA), RoundedCornerShape(2.dp))
        )
    }
}

/** The zone the width and height fields beside it are pointed at, styled to match them. */
@Composable
private fun SelectedZoneField(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .height(42.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            // One item, caption and value together — the number fields beside it read that way too.
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 11.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label.uppercase(),
            fontSize = FIELD_LABEL_SIZE.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = FIELD_LABEL_ALPHA),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** One of the two evening-out shortcuts: this row, or the whole grid. */
@Composable
private fun EvenOutButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(26.dp),
        contentPadding = PaddingValues(horizontal = 11.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

/** How text changing in a zone is animated — one setting for every zone and the full screen. */

@Composable
private fun ZoneLabelCell(
    caption: String,
    text: String,
    size: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(3.dp)
    // Content on a lit screen — a translucent panel over the black, not an opaque tile. The one
    // that is selected is lifted to the accent, since the fields below it are pointed at it.
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(2.dp)
            .background(
                if (selected) accent.copy(alpha = SELECTED_ZONE_ALPHA) else Color.White.copy(alpha = ZONE_ALPHA),
                shape
            )
            // Two pixels when selected: these cells sit on black whatever the theme, so a light
            // theme's primary is a dark blue there and the fill alone barely separates from grey.
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) accent else Color.White.copy(alpha = ZONE_BORDER_ALPHA),
                shape
            )
            .clickable(onClick = onClick)
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        val body = text.ifBlank { "\u2014" }
        BoxWithConstraints(contentAlignment = Alignment.Center) {
            // A narrow zone in a five-zone layout has room for a word, not a list, so the label
            // steps down until it fits rather than being clipped mid-name.
            val measurer = rememberTextMeasurer()
            val base = MaterialTheme.typography.labelSmall
            val ceiling = base.fontSize.value.toInt()
            val fitted = remember(caption, body, size, base, maxWidth, maxHeight) {
                calculateAutoFitFontSize(
                    textMeasurer = measurer,
                    text = "$caption\n$body\n$size",
                    baseStyle = base,
                    availableWidth = maxWidth.value.toInt(),
                    availableHeight = maxHeight.value.toInt(),
                ).coerceAtMost(ceiling)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = caption,
                    style = base,
                    fontSize = fitted.sp,
                    lineHeight = (fitted * ZONE_LINE_HEIGHT).sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = ZONE_CAPTION_ALPHA),
                )
                Text(
                    text = body,
                    style = base,
                    fontSize = fitted.sp,
                    lineHeight = (fitted * ZONE_LINE_HEIGHT).sp,
                    color = Color.White.copy(alpha = if (text.isBlank()) ZONE_BORDER_ALPHA else 1f),
                    textAlign = TextAlign.Center
                )
                Text(
                    text = size,
                    style = base,
                    fontSize = fitted.sp,
                    lineHeight = (fitted * ZONE_LINE_HEIGHT).sp,
                    // White on the selected cell: its fill is already the accent, and the accent
                    // on top of itself is a light theme's dark blue on dark blue.
                    color = if (selected) Color.White else Color.White.copy(alpha = ZONE_CAPTION_ALPHA),
                )
            }
        }
    }
}
