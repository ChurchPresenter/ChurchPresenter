package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.bottom
import org.churchpresenter.strings.generated.resources.middle
import org.churchpresenter.strings.generated.resources.preview_layout_empty_area
import org.churchpresenter.strings.generated.resources.preview_layout_remove
import org.churchpresenter.strings.generated.resources.preview_layout_split_across
import org.churchpresenter.strings.generated.resources.preview_layout_split_down
import org.churchpresenter.strings.generated.resources.top
import org.churchpresenter.settings.PreviewArea
import org.churchpresenter.settings.SPLIT_ACROSS
import org.churchpresenter.settings.SPLIT_DOWN
import org.churchpresenter.settings.removedAt
import org.churchpresenter.settings.splitAt
import org.churchpresenter.settings.updatedAt
import org.churchpresenter.settings.withDividerAt
import org.churchpresenter.settings.without
import org.churchpresenter.settings.utils.Constants
import org.jetbrains.compose.resources.stringResource
import java.awt.Cursor

private val DIVIDER = 6.dp
private val AREA_GAP = 4.dp

/** A preview with no output yet, in edit mode: a 16:9 placeholder the controls sit on. */
private const val EMPTY_ASPECT = 16f / 9f

/**
 * What an edit of the layout does to it, handed back whole: the operator splits, removes, fills and
 * places areas, and drags dividers, and each call is the new root.
 */
internal class PreviewLayoutEdits(
    val root: PreviewArea,
    val onRoot: (PreviewArea) -> Unit,
)

/**
 * The panel drawn as [root] divides it: each area one output's preview from [entries], fitted
 * inside at its own shape.
 *
 * [fills] stretches the layout to the height it is given, so every area is a share of it and a
 * preview is fitted to its area's height too; otherwise each area is as tall as its preview, as
 * groups always were, and the panel scrolls. [headerAllowance] is the height a preview's name line
 * takes above its picture. With [edits], the layout is being edited: every area carries its
 * controls and every divider can be dragged.
 */
@Composable
internal fun PreviewLayoutView(
    root: PreviewArea,
    entries: Map<String, PreviewEntry>,
    fills: Boolean,
    headerAllowance: Dp,
    edits: PreviewLayoutEdits?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.then(if (fills) Modifier.fillMaxSize() else Modifier.fillMaxWidth())) {
        AreaView(root, emptyList(), entries, fills, headerAllowance, edits, Modifier.fillMaxWidth())
    }
}

@Composable
private fun AreaView(
    area: PreviewArea,
    path: List<Int>,
    entries: Map<String, PreviewEntry>,
    fills: Boolean,
    headerAllowance: Dp,
    edits: PreviewLayoutEdits?,
    modifier: Modifier,
) {
    if (area.isLeaf) {
        LeafView(area, path, entries, fills, headerAllowance, edits, modifier)
        return
    }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val across = area.split == SPLIT_ACROSS
    val child: @Composable (Int, Modifier) -> Unit = { index, placed ->
        AreaView(area.children[index], path + index, entries, fills, headerAllowance, edits, placed)
    }
    if (across) {
        Row(
            modifier.then(if (fills) Modifier.fillMaxHeight() else Modifier).onSizeChanged { size = it },
            horizontalArrangement = Arrangement.spacedBy(if (edits == null) AREA_GAP else 0.dp),
        ) {
            area.children.indices.forEach { index ->
                child(index, Modifier.weight(area.ratios.getOrElse(index) { 1f }.coerceAtLeast(MIN_WEIGHT)))
                if (edits != null && index < area.children.lastIndex) {
                    Divider(edits, path, index, area, size, vertical = true)
                }
            }
        }
    } else {
        Column(
            modifier.then(if (fills) Modifier.fillMaxHeight() else Modifier).onSizeChanged { size = it },
            verticalArrangement = Arrangement.spacedBy(if (edits == null) AREA_GAP else 0.dp),
        ) {
            area.children.indices.forEach { index ->
                val placed = if (fills) {
                    Modifier.weight(area.ratios.getOrElse(index) { 1f }.coerceAtLeast(MIN_WEIGHT))
                } else {
                    Modifier
                }
                child(index, placed.fillMaxWidth())
                if (edits != null && index < area.children.lastIndex) {
                    Divider(edits, path, index, area, size, vertical = false)
                }
            }
        }
    }
}

private const val MIN_WEIGHT = 0.01f

/**
 * One area's preview: the output's own, fitted inside at its shape and placed Top, Middle or Bottom
 * where the area is taller -- or, while editing, a placeholder for an empty area -- with its
 * controls on it while the layout is edited.
 */
@Composable
private fun LeafView(
    area: PreviewArea,
    path: List<Int>,
    entries: Map<String, PreviewEntry>,
    fills: Boolean,
    headerAllowance: Dp,
    edits: PreviewLayoutEdits?,
    modifier: Modifier,
) {
    val entry = entries[area.output]
    val alignment = when (area.place) {
        Constants.TOP -> Alignment.TopCenter
        Constants.BOTTOM -> Alignment.BottomCenter
        else -> Alignment.Center
    }
    BoxWithConstraints(modifier.then(if (fills) Modifier.fillMaxHeight() else Modifier), contentAlignment = alignment) {
        val aspect = entry?.aspect ?: EMPTY_ASPECT
        // Filling the panel, a preview is as wide as its area allows *and* as its height allows.
        val width = if (fills && constraints.hasBoundedHeight) {
            minOf(maxWidth, (maxHeight - headerAllowance) * aspect)
        } else {
            maxWidth
        }
        when {
            entry != null -> entry.content(Modifier.width(width), true)
            edits != null -> Box(
                Modifier.width(width).aspectRatio(aspect).border(1.dp, MaterialTheme.colorScheme.outline),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(Res.string.preview_layout_empty_area),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (edits != null) {
            AreaControls(area, path, entries, edits, Modifier.align(Alignment.TopStart))
        }
    }
}

/** An area's controls while the layout is edited: what it shows, where it sits, and splitting or removing it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaControls(
    area: PreviewArea,
    path: List<Int>,
    entries: Map<String, PreviewEntry>,
    edits: PreviewLayoutEdits,
    modifier: Modifier,
) {
    val root = edits.root
    Column(
        modifier
            .padding(4.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = CONTROLS_ALPHA), MaterialTheme.shapes.small)
            .padding(4.dp)
            .testTag(previewAreaTag(path)),
    ) {
        OutputPicker(area, path, entries, edits)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                Constants.TOP to Res.string.top,
                Constants.MIDDLE to Res.string.middle,
                Constants.BOTTOM to Res.string.bottom,
            ).forEach { (place, label) ->
                    TextButton(
                        onClick = { edits.onRoot(root.updatedAt(path) { it.copy(place = place) }) },
                        enabled = area.place != place,
                    ) { ControlLabel(stringResource(label)) }
                }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextButton(onClick = { edits.onRoot(root.splitAt(path, SPLIT_ACROSS)) }) {
                ControlLabel(stringResource(Res.string.preview_layout_split_across))
            }
            TextButton(onClick = { edits.onRoot(root.splitAt(path, SPLIT_DOWN)) }) {
                ControlLabel(stringResource(Res.string.preview_layout_split_down))
            }
            TextButton(onClick = { edits.onRoot(root.removedAt(path)) }) {
                ControlLabel(stringResource(Res.string.preview_layout_remove))
            }
        }
    }
}

private const val CONTROLS_ALPHA = 0.85f

/** The small label on an area's control buttons. */
@Composable
private fun ControlLabel(text: String) = Text(text, style = MaterialTheme.typography.labelSmall)

/** Which output an area shows: each one the panel can preview, or none. */
@Composable
private fun OutputPicker(
    area: PreviewArea,
    path: List<Int>,
    entries: Map<String, PreviewEntry>,
    edits: PreviewLayoutEdits,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, modifier = Modifier.testTag(previewAreaOutputTag(path))) {
            Text(
                entries[area.output]?.label ?: stringResource(Res.string.preview_layout_empty_area),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.preview_layout_empty_area)) },
                onClick = {
                    open = false
                    edits.onRoot(edits.root.updatedAt(path) { it.copy(output = "") })
                },
            )
            entries.values.forEach { entry ->
                DropdownMenuItem(
                    text = { Text(entry.label) },
                    onClick = {
                        open = false
                        // An output is shown in one area at most: placing it here takes it from any other.
                        val cleared = edits.root.without(entry.key)
                        edits.onRoot(cleared.updatedAt(path) { it.copy(output = entry.key) })
                    },
                )
            }
        }
    }
}

/**
 * The handle between two areas of a split, dragged to share their room differently. [split] is the
 * split's measured size: a drag is measured against its length along the axis, and the handle is
 * as long as its other side -- a split in the scrolling panel has no height to fill.
 */
@Composable
private fun Divider(
    edits: PreviewLayoutEdits,
    path: List<Int>,
    index: Int,
    area: PreviewArea,
    split: IntSize,
    vertical: Boolean,
) {
    val latest by rememberUpdatedState(edits)
    val latestArea by rememberUpdatedState(area)
    val total by rememberUpdatedState(if (vertical) split.width.toFloat() else split.height.toFloat())
    var start by remember { mutableStateOf(0f) }
    var moved by remember { mutableStateOf(0f) }
    val across = with(LocalDensity.current) { (if (vertical) split.height else split.width).toDp() }
    Box(
        Modifier
            .then(if (vertical) Modifier.width(DIVIDER).height(across) else Modifier.height(DIVIDER).width(across))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = DIVIDER_ALPHA))
            .pointerHoverIcon(PointerIcon(Cursor(if (vertical) Cursor.E_RESIZE_CURSOR else Cursor.N_RESIZE_CURSOR)))
            .pointerInput(path, index) {
                detectDragGestures(
                    onDragStart = {
                        val ratios = latestArea.ratios
                        start = ratios[index] / (ratios[index] + ratios[index + 1])
                        moved = 0f
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        moved += if (vertical) amount.x else amount.y
                        val ratios = latestArea.ratios
                        val pair = ratios[index] + ratios[index + 1]
                        val share = start + moved / (total * pair).coerceAtLeast(1f)
                        latest.onRoot(latest.root.withDividerAt(path, index, share))
                    },
                )
            }
            .testTag(previewDividerTag(path, index)),
    )
}

private const val DIVIDER_ALPHA = 0.5f

/** Test handles for the layout editor. */
internal fun previewAreaTag(path: List<Int>): String = "preview_area_${path.joinToString("_")}"
internal fun previewAreaOutputTag(path: List<Int>): String = "preview_area_output_${path.joinToString("_")}"
internal fun previewDividerTag(path: List<Int>, index: Int): String =
    "preview_divider_${path.joinToString("_")}_$index"
