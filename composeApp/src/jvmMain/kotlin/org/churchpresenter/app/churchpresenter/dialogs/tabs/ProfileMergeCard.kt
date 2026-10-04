package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.profile_merge_caption
import org.churchpresenter.strings.generated.resources.profile_merge_mac_note
import org.churchpresenter.strings.generated.resources.profile_merge_mixed
import org.churchpresenter.strings.generated.resources.profile_merge_not_following
import org.churchpresenter.strings.generated.resources.profile_merge_not_rectangle
import org.churchpresenter.strings.generated.resources.profile_merge_other_kind
import org.churchpresenter.strings.generated.resources.profile_merge_result
import org.churchpresenter.strings.generated.resources.profile_merge_snap
import org.churchpresenter.strings.generated.resources.profile_merge_sub
import org.churchpresenter.strings.generated.resources.profile_merge_switch
import org.churchpresenter.strings.generated.resources.profile_merge_tiled_note
import org.churchpresenter.strings.generated.resources.profile_merge_too_few
import org.churchpresenter.strings.generated.resources.profile_merge_unavailable
import org.churchpresenter.canvas.deckLinkModeSize
import org.churchpresenter.app.churchpresenter.utils.isMacOs
import org.churchpresenter.settings.MergeKind
import org.churchpresenter.settings.MergeMember
import org.churchpresenter.settings.MergeProblem
import org.churchpresenter.settings.MergeTile
import org.churchpresenter.settings.OutputMerge
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.mergeMember
import org.churchpresenter.settings.resolveMerge
import org.churchpresenter.settings.sideBySide
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private val MAP_HEIGHT = 120.dp

/** The widest the map is drawn, as a multiple of its height; a wider wall is drawn smaller. */
private const val MAP_MAX_RATIO = 4f
private const val POSITION_LIMIT = 30_000

/** The merged picture's drawing, for a test to drag its tiles. */
internal const val MERGE_MAP_TAG = "merge-map"

/** The stored output key of an Outputs-page tile -- `screen:0`, `ndi:1`. */
internal val OutputTile.key: String
    get() = Constants.previewOutputKey(
        when (kind) {
            OutputKind.SCREEN -> Constants.PREVIEW_OUTPUT_SCREEN
            OutputKind.NDI -> Constants.PREVIEW_OUTPUT_NDI
            OutputKind.OMT -> Constants.PREVIEW_OUTPUT_OMT
            OutputKind.BROWSER_SOURCE -> Constants.PREVIEW_OUTPUT_BROWSER_SOURCE
        },
        index,
    )

/**
 * The outputs following [profile] that could take part in its merge: of [kind] when the merge has
 * one, otherwise of whichever kind [profile] has the most outputs of.
 */
internal fun mergeCandidates(
    profile: OutputProfile,
    proj: ProjectionSettings,
    tiles: List<OutputTile>,
    deckLinkSize: (Int) -> Pair<Int, Int>? = { null },
): List<MergeMember> {
    val mine = tiles.filter { it.profileId == profile.id }.mapNotNull { proj.mergeMember(it.key, deckLinkSize) }
    val merged = profile.merge?.tiles?.firstOrNull()?.let { t -> mine.firstOrNull { it.output == t.output }?.kind }
    val kind = merged ?: mine.groupBy { it.kind }.maxByOrNull { it.value.size }?.key ?: return emptyList()
    return mine.filter { it.kind == kind }
}

/** [profile]'s merge switched on: every candidate, side by side in list order. */
internal fun startedMerge(candidates: List<MergeMember>): OutputMerge {
    val widths = candidates.associate { it.output to it.width }
    return OutputMerge(sideBySide(candidates.map { MergeTile(it.output) }) { widths[it] })
}

/**
 * Profiles → Outputs: the outputs following this profile merged into one picture -- a video wall
 * of two TVs, or two NDI feeds each carrying half of it. Only outputs of one kind can be merged; a
 * real display's place in the picture is its place on the desktop, anything else is placed here.
 */
@Composable
internal fun ProfileMergeCard(
    profile: OutputProfile,
    proj: ProjectionSettings,
    tiles: List<OutputTile>,
    onMergeChange: (OutputMerge?) -> Unit,
    onMac: Boolean = isMacOs(System.getProperty("os.name").orEmpty()),
    deckLinkSize: (Int) -> Pair<Int, Int>? = ::deckLinkModeSize,
) {
    val candidates = mergeCandidates(profile, proj, tiles, deckLinkSize)
    val merge = profile.merge
    val labels = tiles.associate { it.key to it.label }
    SettingsGroup(caption = stringResource(Res.string.profile_merge_caption), key = "merge") {
        SettingsSwitchRow(
            label = stringResource(Res.string.profile_merge_switch),
            sub = stringResource(
                if (candidates.size >= 2 || merge != null) {
                    Res.string.profile_merge_sub
                } else {
                    Res.string.profile_merge_unavailable
                },
            ),
            checked = merge != null,
            onCheckedChange = { on ->
                if (!on) onMergeChange(null) else if (candidates.size >= 2) onMergeChange(startedMerge(candidates))
            },
        )
        if (merge == null) return@SettingsGroup
        // An output that was deleted, or handed to another profile, leaves the merge: it can no
        // longer be ticked off here, and a merge naming it would never draw.
        val stale = merge.tiles.filter { t -> candidates.none { it.output == t.output } }
        if (stale.isNotEmpty()) {
            LaunchedEffect(stale) { onMergeChange(merge.copy(tiles = merge.tiles - stale.toSet())) }
        }
        val (resolved, problem) = proj.resolveMerge(profile, deckLinkSize)
        val kind = candidates.firstOrNull()?.kind
        val placeable = kind != null && kind != MergeKind.REAL_DISPLAY
        val sizes = candidates.associate { it.output to (it.width to it.height) }
        val latestMerge by rememberUpdatedState(merge)
        SettingsWideRow {
            MergeMap(merge, sizes, labels, draggable = placeable) { output, x, y ->
                onMergeChange(latestMerge.moved(output, x, y))
            }
            MergeStatus(resolved?.let { it.width to it.height }, problem)
            val note = when {
                kind == MergeKind.REAL_DISPLAY && onMac -> Res.string.profile_merge_mac_note
                placeable -> Res.string.profile_merge_tiled_note
                else -> null
            }
            if (note != null) {
                Text(stringResource(note), fontSize = 11.sp, color = profilesPalette().faintText)
            }
        }
        candidates.forEach { member ->
            val tile = merge.tiles.firstOrNull { it.output == member.output }
            SettingsRow(
                label = labels[member.output] ?: member.output,
                sub = "${member.width}×${member.height}",
                leading = {
                    Checkbox(
                        checked = tile != null,
                        onCheckedChange = { on -> onMergeChange(merge.including(member, on, sizes)) },
                    )
                },
            ) {
                if (tile != null && placeable) {
                    RowNumberField(
                        value = tile.x,
                        onValueChange = { onMergeChange(merge.moved(member.output, it, tile.y)) },
                        range = -POSITION_LIMIT..POSITION_LIMIT,
                        caption = "X",
                    )
                    RowNumberField(
                        value = tile.y,
                        onValueChange = { onMergeChange(merge.moved(member.output, tile.x, it)) },
                        range = -POSITION_LIMIT..POSITION_LIMIT,
                        caption = "Y",
                    )
                }
            }
        }
        val others = tiles.filter { t -> t.profileId == profile.id && candidates.none { it.output == t.key } }
        others.forEach { other ->
            SettingsRow(label = other.label, sub = stringResource(Res.string.profile_merge_other_kind)) {}
        }
        if (placeable) {
            SettingsWideRow {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    KeyButton(
                        onClick = {
                            onMergeChange(OutputMerge(sideBySide(merge.tiles) { sizes[it]?.first }))
                        },
                        shape = AppShape(7.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier.height(26.dp),
                    ) {
                        Text(stringResource(Res.string.profile_merge_snap), fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/** The merged picture's size, or why it cannot be drawn, in the error colour. */
@Composable
private fun MergeStatus(size: Pair<Int, Int>?, problem: MergeProblem?) {
    Text(
        text = when (problem) {
            null -> size?.let { (w, h) -> stringResource(Res.string.profile_merge_result, w, h) }.orEmpty()
            MergeProblem.TOO_FEW -> stringResource(Res.string.profile_merge_too_few)
            MergeProblem.MIXED_KINDS -> stringResource(Res.string.profile_merge_mixed)
            MergeProblem.NOT_FOLLOWING -> stringResource(Res.string.profile_merge_not_following)
            MergeProblem.NOT_A_RECTANGLE -> stringResource(Res.string.profile_merge_not_rectangle)
        },
        fontSize = 12.sp,
        fontWeight = if (problem == null) FontWeight.SemiBold else FontWeight.Normal,
        color = if (problem == null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
    )
}

/** [this] with [output]'s tile at [x], [y]. */
internal fun OutputMerge.moved(output: String, x: Int, y: Int): OutputMerge =
    copy(tiles = tiles.map { if (it.output == output) it.copy(x = x, y = y) else it })

/** [this] with [member] added to the right of everything else, or taken out. */
internal fun OutputMerge.including(member: MergeMember, on: Boolean, sizes: Map<String, Pair<Int, Int>>): OutputMerge {
    if (!on) return copy(tiles = tiles.filterNot { it.output == member.output })
    if (tiles.any { it.output == member.output }) return this
    val right = tiles.maxOfOrNull { it.x + (sizes[it.output]?.first ?: 0) } ?: 0
    return copy(tiles = tiles + MergeTile(member.output, right, 0))
}

/**
 * The merged picture drawn to scale, each tile labelled. Where tiles are placed by hand
 * ([draggable]), a tile is dragged to move it; [onMove] gets its new corner in output pixels.
 */
@Composable
private fun MergeMap(
    merge: OutputMerge,
    sizes: Map<String, Pair<Int, Int>>,
    labels: Map<String, String>,
    draggable: Boolean,
    onMove: (output: String, x: Int, y: Int) -> Unit,
) {
    val rects = merge.tiles.mapNotNull { t -> sizes[t.output]?.let { (w, h) -> t to (w to h) } }
    if (rects.isEmpty()) return
    val minX = rects.minOf { it.first.x }
    val minY = rects.minOf { it.first.y }
    val spanW = rects.maxOf { it.first.x + it.second.first } - minX
    val spanH = rects.maxOf { it.first.y + it.second.second } - minY
    val fill = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val line = MaterialTheme.colorScheme.primary
    val latest by rememberUpdatedState(merge)
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val ratio = spanW.toFloat() / spanH.coerceAtLeast(1)
        Canvas(
            Modifier
                .width(MAP_HEIGHT * ratio.coerceAtMost(MAP_MAX_RATIO))
                .height(MAP_HEIGHT)
                .testTag(MERGE_MAP_TAG)
                .pointerInput(draggable, spanW, spanH) {
                    if (!draggable) return@pointerInput
                    val scale = minOf(size.width / spanW.toFloat(), size.height / spanH.toFloat())
                    var dragging: String? = null
                    detectDragGestures(
                        onDragStart = { at ->
                            dragging = latest.tiles.lastOrNull { t ->
                                val (w, h) = sizes[t.output] ?: return@lastOrNull false
                                val left = (t.x - minX) * scale
                                val top = (t.y - minY) * scale
                                at.x in left..(left + w * scale) && at.y in top..(top + h * scale)
                            }?.output
                        },
                        onDragEnd = { dragging = null },
                    ) { change, amount ->
                        val output = dragging ?: return@detectDragGestures
                        change.consume()
                        val tile = latest.tiles.firstOrNull { it.output == output } ?: return@detectDragGestures
                        val dx = (amount.x / scale).roundToInt()
                        val dy = (amount.y / scale).roundToInt()
                        onMove(output, tile.x + dx, tile.y + dy)
                    }
                },
        ) {
            val scale = minOf(size.width / spanW, size.height / spanH)
            rects.forEach { (tile, wh) ->
                val topLeft = Offset((tile.x - minX) * scale, (tile.y - minY) * scale)
                val box = Size(wh.first * scale, wh.second * scale)
                drawRect(fill, topLeft, box)
                drawRect(line, topLeft, box, style = Stroke(width = 1.dp.toPx()))
            }
        }
        // Labels in order beside the map; the map itself stays a plain drawing.
        Text(
            text = rects.joinToString("  ·  ") { (t, _) -> labels[t.output] ?: t.output },
            fontSize = 11.sp,
            color = textColor,
            modifier = Modifier.width(160.dp),
        )
    }
}
