package org.churchpresenter.appsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.churchpresenter.icons.generated.resources.Res as IconRes
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.detected_files_less
import org.churchpresenter.strings.generated.resources.detected_files_more
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.keyboardFocusRing
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * A folder's detected files, kept to one line: [lead], as many [chips] as fit, then a "N more"
 * toggle for the rest. A church with dozens of Bibles or songbooks would otherwise push the
 * rest of the Storage card off the screen. Opened, it wraps every chip as before, with a toggle to
 * close it again; it starts closed whenever [resetKey] changes.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CollapsibleChipRow(
    chips: List<String>,
    resetKey: Any?,
    lead: @Composable () -> Unit,
) {
    var expanded by remember(resetKey) { mutableStateOf(false) }
    if (expanded) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CHIP_GAP),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            lead()
            chips.forEach { DetectedChip(it) }
            MoreLessToggle(stringResource(Res.string.detected_files_less), expanded = true) { expanded = false }
        }
    } else {
        OneLineChips(chips, lead) { expanded = true }
    }
}

@Composable
private fun OneLineChips(
    chips: List<String>,
    lead: @Composable () -> Unit,
    onExpand: () -> Unit,
) {
    SubcomposeLayout { constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val gap = CHIP_GAP.roundToPx()
        val leading = subcompose(Slot.LEAD, lead).map { it.measure(loose) }
        // Every chip and the widest toggle are measured as silent copies, so the chips that end up
        // folded away, and the toggles that were only tried for size, never reach a screen reader.
        val widths = subcompose(Slot.PROBE) {
            chips.forEach { DetectedChip(it, Modifier.clearAndSetSemantics {}) }
        }.map { it.measure(loose).width }
        val fixed = leading.sumOf { it.width + gap }
        val limit = constraints.maxWidth
        var shown = chips.size
        val fullWidth = fixed + widths.sumOf { it + gap } - gap
        val toggleNeeded = chips.isNotEmpty() && constraints.hasBoundedWidth && fullWidth > limit
        if (toggleNeeded) {
            // The toggle never grows as chips come back, so sizing for "all of them hidden" is safe.
            val toggleWidth = subcompose(Slot.TOGGLE_PROBE) {
                val widest = stringResource(Res.string.detected_files_more, chips.size)
                ToggleFace(widest, expanded = false, modifier = Modifier.clearAndSetSemantics {})
            }.first().measure(loose).width
            // Drop chips from the end until the rest fit beside the toggle that counts them.
            while (shown > 0 && fixed + widths.take(shown).sumOf { it + gap } + toggleWidth > limit) shown--
        }
        val shownChips = subcompose(Slot.CHIPS) {
            chips.take(shown).forEach { DetectedChip(it) }
        }.map { it.measure(loose) }
        val more = if (toggleNeeded) {
            val hidden = chips.size - shown
            subcompose(Slot.TOGGLE) { MoreToggle(hidden, onExpand) }.map { it.measure(loose) }
        } else {
            emptyList()
        }
        val line = leading + shownChips + more
        val height = line.maxOfOrNull { it.height } ?: 0
        val width = rowWidth(line, gap).coerceAtMost(constraints.maxWidth)
        layout(width, height) {
            var x = 0
            line.forEach {
                it.placeRelative(x, Alignment.CenterVertically.align(it.height, height))
                x += it.width + gap
            }
        }
    }
}

private fun rowWidth(items: List<Placeable>, gap: Int): Int =
    if (items.isEmpty()) 0 else items.sumOf { it.width } + gap * (items.size - 1)

private enum class Slot { LEAD, PROBE, TOGGLE_PROBE, CHIPS, TOGGLE }

@Composable
private fun MoreToggle(hidden: Int, onExpand: () -> Unit) {
    MoreLessToggle(stringResource(Res.string.detected_files_more, hidden), expanded = false, onClick = onExpand)
}

/** Opens or closes a folded list: the word and its arrow in the accent, so it reads as a control. */
@Composable
internal fun MoreLessToggle(text: String, expanded: Boolean, onClick: () -> Unit) {
    val shape = AppShape(6.dp)
    ToggleFace(
        text,
        expanded,
        Modifier
            .keyboardFocusRing(shape)
            .clip(shape)
            .clickable(role = Role.Button, onClick = onClick)
            .testTag(CHIPS_TOGGLE_TAG),
    )
}

@Composable
private fun ToggleFace(text: String, expanded: Boolean, modifier: Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            painter = painterResource(if (expanded) IconRes.drawable.ic_arrow_up else IconRes.drawable.ic_arrow_down),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 3.dp).size(12.dp),
        )
    }
}

/** A file the folder holds, named as it is on disk. */
@Composable
internal fun DetectedChip(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(AppShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Test handle for a folded list's more/less toggle. */
internal const val CHIPS_TOGGLE_TAG = "storage_chips_toggle"

private val CHIP_GAP = 8.dp
