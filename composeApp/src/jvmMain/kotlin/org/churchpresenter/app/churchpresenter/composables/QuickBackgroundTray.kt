@file:OptIn(ExperimentalFoundationApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.quick_background_hint
import churchpresenter.composeapp.generated.resources.quick_background_reset
import churchpresenter.composeapp.generated.resources.quick_background_slot_hint
import churchpresenter.composeapp.generated.resources.quick_background_title
import org.churchpresenter.app.churchpresenter.dialogs.SongBackgroundFill
import org.churchpresenter.app.churchpresenter.utils.LocalShortcuts
import org.churchpresenter.app.churchpresenter.utils.label
import org.churchpresenter.settings.QuickBackground
import org.jetbrains.compose.resources.stringResource

internal const val QUICK_BACKGROUND_TRAY_TAG = "quick_background_tray"
internal const val QUICK_BACKGROUND_HEADER_TAG = "quick_background_header"
internal const val QUICK_BACKGROUND_RESET_TAG = "quick_background_reset"

/** Tiles per row when the tray is open — the sidebar is narrow, three is what fits legibly. */
private const val TRAY_COLUMNS = 3

/**
 * The quick backgrounds tray, docked under the live preview.
 *
 * **A live control, and only that.** Picking a tile overrides every output's background for the
 * rest of the service and writes nothing; [onPick] with null puts the configured backgrounds back.
 * Which backgrounds are in the tray, what they are called and what order they sit in are all
 * settings, edited in Settings → Background — nothing here adds, edits or removes one.
 *
 * Renders nothing at all when [backgrounds] is empty: an operator who has configured none has
 * nothing to pick, and a permanently empty strip in a sidebar this narrow is noise.
 */
@Composable
internal fun QuickBackgroundTray(
    backgrounds: List<QuickBackground>,
    activeId: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (QuickBackground?) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (backgrounds.isEmpty()) return
    val slots = backgrounds.take(QUICK_BACKGROUND_SLOTS)
    Column(modifier = modifier.testTag(QUICK_BACKGROUND_TRAY_TAG).fillMaxWidth()) {
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        TrayHeader(
            slots = slots,
            activeId = activeId,
            expanded = expanded,
            onExpandedChange = onExpandedChange,
            onPick = onPick,
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            TrayGrid(slots = slots, activeId = activeId, onPick = onPick)
            Spacer(Modifier.height(8.dp))
            TrayHint()
        }
    }
}

/**
 * The row that is always there: the disclosure caret, the title, and the way back to normal.
 *
 * When the tray is closed the slots come with it, as swatches small enough to sit on one line —
 * shutting the tray to reclaim the sidebar's height must not cost the operator the one-click pick
 * that is the whole point of it.
 */
@Composable
private fun TrayHeader(
    slots: List<QuickBackground>,
    activeId: String?,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onPick: (QuickBackground?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Weighted, so the swatches and the reset button are measured at their full size first and
        // it is the *title* that gives way in a narrow sidebar — losing the last letters of a word
        // the caret already explains costs nothing; losing a swatch costs a pick.
        Row(
            modifier = Modifier
                .weight(1f)
                .testTag(QUICK_BACKGROUND_HEADER_TAG)
                .clip(RoundedCornerShape(4.dp))
                .clickable { onExpandedChange(!expanded) }
                .padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(14.dp).rotate(if (expanded) 0f else CARET_CLOSED_DEGREES),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(Res.string.quick_background_title).uppercase(),
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!expanded) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                slots.forEachIndexed { index, entry ->
                    MiniSwatch(
                        entry = entry,
                        slot = index + 1,
                        active = entry.id == activeId,
                        onPick = { onPick(entry) },
                    )
                }
            }
        }
        if (activeId != null) {
            val resetLabel = stringResource(Res.string.quick_background_reset)
            ConditionalTooltipArea(tooltip = { TrayTooltip(resetLabel) }) {
                Box(
                    modifier = Modifier
                        .testTag(QUICK_BACKGROUND_RESET_TAG)
                        .size(18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { onPick(null) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = resetLabel,
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** The open tray: every slot as a tile, [TRAY_COLUMNS] to a row. */
@Composable
private fun TrayGrid(slots: List<QuickBackground>, activeId: String?, onPick: (QuickBackground?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        slots.chunked(TRAY_COLUMNS).forEachIndexed { rowIndex, row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.forEachIndexed { columnIndex, entry ->
                    QuickBackgroundTile(
                        entry = entry,
                        slot = rowIndex * TRAY_COLUMNS + columnIndex + 1,
                        active = entry.id == activeId,
                        onPick = { onPick(entry) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Keeps a short last row's tiles the same width as a full one's, rather than
                // letting two tiles share the space three were sized for.
                repeat(TRAY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** The shortcut range the slots answer to, and where to go to change what they hold. */
@Composable
private fun TrayHint() {
    val shortcuts = LocalShortcuts.current
    // The range of slots that actually have a key, rather than a hardcoded 1–8: the last slot
    // ships unbound, and every one of them can be rebound or cleared in the shortcuts dialog.
    val bound = (1..QUICK_BACKGROUND_SLOTS).mapNotNull { slot ->
        quickBackgroundActionFor(slot)?.let { shortcuts.chordsFor(it).firstOrNull() }
    }
    val first = bound.firstOrNull()
    val last = bound.lastOrNull()
    if (first == null || last == null || first == last) return
    Text(
        text = stringResource(Res.string.quick_background_hint, first.label(), last.label()),
        fontSize = 9.5.sp,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    )
}

/** One tile: the background as it will look, its slot number, and what it is called. */
@Composable
private fun QuickBackgroundTile(
    entry: QuickBackground,
    slot: Int,
    active: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name = quickBackgroundLabel(entry)
    ConditionalTooltipArea(tooltip = { TrayTooltip(slotTooltip(entry, slot)) }, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(TILE_ASPECT)
                    .clip(RoundedCornerShape(7.dp))
                    .clickable(onClick = onPick)
                    .border(
                        width = 2.dp,
                        color = if (active) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(7.dp),
                    ),
            ) {
                SongBackgroundFill(entry.background, Modifier.fillMaxSize())
                SlotBadge(slot, Modifier.align(Alignment.TopStart).padding(2.dp))
            }
            Text(
                text = name,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = if (active) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The header's stand-in for a tile while the tray is closed. */
@Composable
private fun MiniSwatch(entry: QuickBackground, slot: Int, active: Boolean, onPick: () -> Unit) {
    ConditionalTooltipArea(tooltip = { TrayTooltip(slotTooltip(entry, slot)) }) {
        Box(
            modifier = Modifier
                .size(15.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(onClick = onPick)
                .border(
                    width = 1.5.dp,
                    color = if (active) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp),
                ),
        ) {
            SongBackgroundFill(entry.background, Modifier.fillMaxSize())
        }
    }
}

/**
 * The slot number, on a scrim of its own.
 *
 * White ink alone is unreadable over a white background and black ink over a black one, and the
 * tiles are whatever the operator has configured — so the number carries its own contrast.
 */
@Composable
private fun SlotBadge(slot: Int, modifier: Modifier = Modifier) {
    if (slot > QUICK_BACKGROUND_SLOTS) return
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color.Black.copy(alpha = SLOT_SCRIM_ALPHA))
            .padding(horizontal = 3.dp, vertical = 1.dp),
    ) {
        Text(
            text = slot.toString(),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = SLOT_INK_ALPHA),
        )
    }
}

/** A tile's tooltip: what it is called, and the key that reaches it. */
@Composable
private fun slotTooltip(entry: QuickBackground, slot: Int): String {
    val name = quickBackgroundLabel(entry)
    val chord = quickBackgroundActionFor(slot)?.let { LocalShortcuts.current.chordsFor(it).firstOrNull() }
    return if (chord == null) name
    else stringResource(Res.string.quick_background_slot_hint, name, chord.label())
}

@Composable
private fun TrayTooltip(text: String) {
    Box(
        modifier = Modifier
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.inverseSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.inverseOnSurface,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

private const val TILE_ASPECT = 16f / 10f
private const val CARET_CLOSED_DEGREES = -90f
private const val SLOT_INK_ALPHA = 0.85f
private const val SLOT_SCRIM_ALPHA = 0.4f
