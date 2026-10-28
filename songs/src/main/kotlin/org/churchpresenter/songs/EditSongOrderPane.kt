package org.churchpresenter.songs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import org.churchpresenter.sharedui.composables.SectionInk
import org.churchpresenter.sharedui.composables.SectionLabelRow
import org.churchpresenter.sharedui.composables.ZoneLabel
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.song_background_untitled_section
import org.churchpresenter.strings.generated.resources.song_order
import org.churchpresenter.strings.generated.resources.song_order_add_section
import org.churchpresenter.strings.generated.resources.song_order_custom
import org.churchpresenter.strings.generated.resources.song_order_default
import org.churchpresenter.strings.generated.resources.song_order_not_in_lyrics
import org.churchpresenter.strings.generated.resources.song_order_preset_bridge
import org.churchpresenter.strings.generated.resources.song_order_preset_chorus_each
import org.churchpresenter.strings.generated.resources.song_order_preset_double_chorus
import org.churchpresenter.strings.generated.resources.song_order_preset_start_chorus
import org.churchpresenter.strings.generated.resources.song_order_presets
import org.churchpresenter.strings.generated.resources.song_order_remove
import org.churchpresenter.strings.generated.resources.song_order_repeat
import org.churchpresenter.strings.generated.resources.song_order_slides
import org.churchpresenter.strings.generated.resources.song_order_unused
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.KeyIconButton
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

internal val ORDER_COLUMN_WIDTH = 176.dp
private val CHIP_HEIGHT = 30.dp
private val CHIP_GAP = 5.dp
private val ChipShape = AppShape(6.dp)
private const val DEFAULT_ORDER_ALPHA = 0.6f
private const val DRAGGED_ALPHA = 0.5f
private const val REPEAT_ALPHA = 0.7f
private const val MENU_SEQUENCE_CHIPS = 10

/** Test handles for the order column. */
internal const val ORDER_PRESETS_TAG = "song_editor_order_presets"
internal const val ORDER_ADD_SECTION_TAG = "song_editor_order_add"
internal const val ORDER_CHIP_TAG = "song_editor_order_chip:"

/**
 * The order the song is sung in, between the lyrics and the right pane: a chip per section, dragged
 * to reorder, a preset menu, and Add section. With no order of its own the chips show the default,
 * faded; the first change makes it the song's own.
 */
@Composable
internal fun EditSongOrderColumn(state: EditSongState, sections: EditSongSections, modifier: Modifier = Modifier) {
    val custom = state.order.isNotEmpty()
    val effective = state.order.ifEmpty { sections.defaultOrder }
    // Removing the last section leaves the order empty, which is the default rather than nothing.
    val setOrder: (List<String>) -> Unit = { state.order = it }

    Column(
        modifier = modifier.width(ORDER_COLUMN_WIDTH).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ZoneLabel(stringResource(Res.string.song_order), Modifier.weight(1f))
                Text(
                    text = stringResource(if (custom) Res.string.song_order_custom else Res.string.song_order_default),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PresetsButton(state, sections)
        }

        OrderChips(
            effective = effective,
            sections = sections,
            faded = !custom,
            onOrderChange = setOrder,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AddSectionButton(sections, effective) { key -> setOrder(effective + key) }
            Text(
                text = stringResource(Res.string.song_order_slides, effective.count { sections.of(it) != null }),
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The chips, in order, each dragged up or down to a new place and removed with its ×. */
@Composable
private fun OrderChips(
    effective: List<String>,
    sections: EditSongSections,
    faded: Boolean,
    onOrderChange: (List<String>) -> Unit,
    modifier: Modifier,
) {
    val slotPx = with(LocalDensity.current) { (CHIP_HEIGHT + CHIP_GAP).toPx() }
    var dragged by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(start = 10.dp, end = 10.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(CHIP_GAP),
    ) {
        effective.forEachIndexed { index, key ->
            val isDragged = index == dragged
            OrderChip(
                key = key,
                section = sections.of(key),
                onRemove = { onOrderChange(effective.filterIndexed { i, _ -> i != index }) },
                modifier = Modifier
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragged) dragOffset else 0f }
                    .alpha(if (isDragged) DRAGGED_ALPHA else if (faded) DEFAULT_ORDER_ALPHA else 1f)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .pointerInput(effective, index) {
                        detectDragGestures(
                            onDragStart = { dragged = index; dragOffset = 0f },
                            onDrag = { change, amount -> change.consume(); dragOffset += amount.y },
                            onDragEnd = {
                                val target = (index + (dragOffset / slotPx).roundToInt())
                                    .coerceIn(0, effective.lastIndex)
                                dragged = -1
                                dragOffset = 0f
                                if (target != index) {
                                    val next = effective.toMutableList()
                                    next.add(target, next.removeAt(index))
                                    onOrderChange(next)
                                }
                            },
                            onDragCancel = { dragged = -1; dragOffset = 0f },
                        )
                    },
            )
        }
    }
}

/** One section in the order: a grip, its short name, its name and the button that removes it. */
@Composable
private fun OrderChip(key: String, section: WrittenSection?, onRemove: () -> Unit, modifier: Modifier) {
    val missing = section == null
    val name = when {
        section == null -> stringResource(Res.string.song_order_not_in_lyrics)
        section.label.isBlank() -> stringResource(Res.string.song_background_untitled_section)
        else -> section.label
    }
    val ink = if (section == null) MaterialTheme.colorScheme.error else SectionInk.of(section.kind)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(CHIP_HEIGHT)
            .background(if (missing) Color.Transparent else ink.copy(alpha = 0.16f), ChipShape)
            .border(1.dp, if (missing) ink else Color.Transparent, ChipShape)
            .padding(start = 4.dp, end = 2.dp)
            .testTag(ORDER_CHIP_TAG + key),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(
            Icons.Default.DragIndicator,
            contentDescription = null,
            tint = ink.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = section?.abbreviation.orEmpty().ifEmpty { "·" },
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = ink,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text = name,
            fontSize = 12.sp,
            color = ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        KeyIconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(Res.string.song_order_remove, name),
                tint = ink,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

/** The preset menu: each ready-made order with the sections it plays, the one in force ticked. */
@Composable
private fun PresetsButton(state: EditSongState, sections: EditSongSections) {
    var open by remember { mutableStateOf(false) }
    Box {
        KeyButton(
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth().height(28.dp).testTag(ORDER_PRESETS_TAG),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Text(stringResource(Res.string.song_order_presets), fontSize = 12.sp, modifier = Modifier.weight(1f))
            Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
        }
        val checkInk = MaterialTheme.colorScheme.primary
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            orderPresetsOf(sections).forEach { preset ->
                val selected = preset.keys == state.order
                val sequence = preset.keys.ifEmpty { sections.defaultOrder }
                DropdownMenuItem(
                    text = {
                        Column(Modifier.widthIn(min = 240.dp)) {
                            MenuTitle(presetName(preset.kind))
                            Text(
                                text = sequenceOf(sections, sequence),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    trailingIcon = if (selected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = checkInk) }
                    } else {
                        null
                    },
                    onClick = { state.order = preset.keys; open = false },
                )
            }
        }
    }
}

/** A menu entry's name, above its detail line. */
@Composable
private fun MenuTitle(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
}

/** [keys] as their short names, the first few only. */
private fun sequenceOf(sections: EditSongSections, keys: List<String>): String =
    keys.take(MENU_SEQUENCE_CHIPS).joinToString(" ") { sections.of(it)?.abbreviation.orEmpty() } +
        if (keys.size > MENU_SEQUENCE_CHIPS) " …" else ""

@Composable
private fun presetName(kind: OrderPresetKind): String = stringResource(
    when (kind) {
        OrderPresetKind.DEFAULT -> Res.string.song_order_default
        OrderPresetKind.CHORUS_AFTER_EACH_VERSE -> Res.string.song_order_preset_chorus_each
        OrderPresetKind.START_WITH_CHORUS -> Res.string.song_order_preset_start_chorus
        OrderPresetKind.END_WITH_DOUBLE_CHORUS -> Res.string.song_order_preset_double_chorus
        OrderPresetKind.BRIDGE_BEFORE_LAST_CHORUS -> Res.string.song_order_preset_bridge
    }
)

/** Add section: every written section, with its first line and how often the order plays it. */
@Composable
private fun AddSectionButton(sections: EditSongSections, effective: List<String>, onAdd: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val untitled = stringResource(Res.string.song_background_untitled_section)
    Box {
        KeyButton(
            onClick = { open = true },
            enabled = sections.written.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(28.dp).testTag(ORDER_ADD_SECTION_TAG),
            contentPadding = PaddingValues(horizontal = 10.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            Text(
                stringResource(Res.string.song_order_add_section),
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sections.written.forEach { section ->
                val ink = SectionInk.of(section.kind)
                val count = effective.count { it == section.key }
                DropdownMenuItem(
                    leadingIcon = {
                        Text(
                            text = section.abbreviation.ifEmpty { "·" },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ink,
                            modifier = Modifier.background(ink.copy(alpha = 0.16f), AppShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 3.dp),
                        )
                    },
                    text = {
                        Column(Modifier.widthIn(min = 180.dp, max = 260.dp)) {
                            MenuTitle(section.label.ifBlank { untitled })
                            Text(
                                text = section.lines.firstOrNull().orEmpty(),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    trailingIcon = {
                        Text(
                            text = if (count > 0) "×$count" else stringResource(Res.string.song_order_unused),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (count > 0) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    },
                    onClick = { onAdd(section.key); open = false },
                )
            }
        }
    }
}

/** The right pane's In order tab: the song as it will play, each section's words under its label. */
@Composable
internal fun EditSongOrderPreview(state: EditSongState, sections: EditSongSections, modifier: Modifier) {
    val effective = state.order.ifEmpty { sections.defaultOrder }
    val untitled = stringResource(Res.string.song_background_untitled_section)
    val seen = mutableSetOf<String>()
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        effective.forEachIndexed { index, key ->
            val section = sections.of(key)
            val repeat = !seen.add(key)
            Column(
                modifier = Modifier.alpha(if (repeat) REPEAT_ALPHA else 1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "${index + 1}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(18.dp),
                    )
                    if (section == null) {
                        ZoneLabel(
                            stringResource(Res.string.song_order_not_in_lyrics),
                            color = MaterialTheme.colorScheme.error,
                        )
                    } else {
                        Box(Modifier.weight(1f)) {
                            SectionLabelRow(label = section.label.ifBlank { untitled })
                        }
                        if (repeat) {
                            Text(
                                text = stringResource(Res.string.song_order_repeat),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                section?.lines?.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 26.dp),
                    )
                }
            }
        }
    }
}
