package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_cue
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_cue_builtin
import org.churchpresenter.calendar.generated.resources.calendar_cue_builtin_countdown
import org.churchpresenter.calendar.generated.resources.calendar_cue_default
import org.churchpresenter.calendar.generated.resources.calendar_cue_do_what
import org.churchpresenter.calendar.generated.resources.calendar_cue_filter_clear
import org.churchpresenter.calendar.generated.resources.calendar_cue_filter_presets
import org.churchpresenter.calendar.generated.resources.calendar_cue_filter_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_loop_badge
import org.churchpresenter.calendar.generated.resources.calendar_cue_nothing_matches
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_builtin
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_note_builtin
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_note_preset
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_note_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_preset
import org.churchpresenter.calendar.generated.resources.calendar_cue_preview_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_run_of_show
import org.churchpresenter.calendar.generated.resources.calendar_cue_empty_presets
import org.churchpresenter.calendar.generated.resources.calendar_cue_empty_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_first_item
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_pinned
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_presets
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_relative
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_label
import org.churchpresenter.calendar.generated.resources.calendar_cue_pin
import org.churchpresenter.calendar.generated.resources.calendar_cue_play
import org.churchpresenter.calendar.generated.resources.calendar_cue_source_presets
import org.churchpresenter.calendar.generated.resources.calendar_cue_source_service
import org.churchpresenter.calendar.generated.resources.calendar_cue_target_item
import org.churchpresenter.calendar.generated.resources.calendar_cue_target_scene
import org.churchpresenter.calendar.generated.resources.calendar_cue_target_slides
import org.churchpresenter.calendar.generated.resources.calendar_cue_target_timer
import org.churchpresenter.calendar.generated.resources.calendar_cue_when
import org.churchpresenter.calendar.generated.resources.calendar_delete
import org.churchpresenter.calendar.generated.resources.calendar_edit_cue
import org.churchpresenter.calendar.generated.resources.calendar_new_cue
import org.churchpresenter.calendar.generated.resources.calendar_play_loop
import org.churchpresenter.calendar.generated.resources.calendar_play_n_times
import org.churchpresenter.calendar.generated.resources.calendar_play_note_loop
import org.churchpresenter.calendar.generated.resources.calendar_play_note_once
import org.churchpresenter.calendar.generated.resources.calendar_play_note_times
import org.churchpresenter.calendar.generated.resources.calendar_play_once
import org.churchpresenter.calendar.generated.resources.calendar_save_cue
import org.churchpresenter.calendar.model.CUE_OFFSETS
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.core.models.schedule.LOOP_FOREVER
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.formatDuration
import org.churchpresenter.calendar.model.isTargetFor
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource
import java.util.UUID
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.parseClockText
import org.churchpresenter.calendar.model.storedTime

private val SHEET_WIDTH = 760.dp
private val COLUMN_GAP = 18.dp
private val FILTER_HEIGHT = 30.dp
private val PREVIEW_THUMB = 104.dp
private const val FILTER_FROM = 4
private const val THUMB_RATIO = 16f / 9f
private val ACTION_HEIGHT = 32.dp
private val OFFSET_HEIGHT = 29.dp
private val TARGET_MIN_HEIGHT = 34.dp
private val TIME_FIELD = 68.dp
private val TARGET_LIST_MAX = 250.dp
private val DOT = 7.dp
private val CHECK_BOX = 16.dp
private const val ON_TINT = 0.16f
private const val ON_BORDER = 0.55f
private const val CARD_TINT = 0.4f
private const val HINT_TINT = 0.10f
private const val WARN_TINT = 0.12f
private const val DEFAULT_OFFSET = -15
private const val DEFAULT_PINNED = "11:00"

/** The `Play` chips the design offers; a saved value outside them is shown as its own chip. */
private val PLAY_CHOICES = listOf(1, 2, 3, 5, LOOP_FOREVER)

/**
 * Create or edit one cue.
 *
 * Laid out to the design: `Do what` as a two-column grid of actions; for an action that points
 * at something, a target list narrowed to what that action can use, switchable between the run of
 * show and the saved presets; `Play` for an action with a run to play; `When` as the offset chips
 * with the resolved clock time beside the heading, the pin-to-wall-clock checkbox under them and a
 * hint saying what the choice means; then `Label`. Delete sits at the far left of the footer.
 */
@Composable
fun CueSheet(
    service: PlannedService,
    presets: List<ItemPreset>,
    existing: ScheduleItem.CueItem?,
    onSave: (ScheduleItem.CueItem) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var action by remember(existing) { mutableStateOf(existing?.action ?: CueAction.COUNTDOWN) }
    var payload by remember(existing) { mutableStateOf(existing?.payload) }
    var offset by remember(existing) { mutableStateOf(existing?.offsetMinutes ?: DEFAULT_OFFSET) }
    var pinned by remember(existing) { mutableStateOf(existing?.isPinned() == true) }
    val use24Hour = LocalUse24HourClock.current
    var pinnedTime by remember(existing, use24Hour) { mutableStateOf(pinnedTimeText(existing, use24Hour)) }
    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }
    var plays by remember(existing) { mutableStateOf(existing?.plays ?: 1) }

    val pinnedAt = parseClockText(pinnedTime)
    val pinnedValid = !pinned || pinnedAt != null
    val hasTarget = action in CueAction.withTarget
    // Countdown and go live have a built-in fallback; the loop and a scene need a pick.
    val needsPick = action == CueAction.PROJECT || action == CueAction.SCENE
    val target = payload?.takeIf { it.isTargetFor(action) }
    val canSave = pinnedValid && (!needsPick || target != null)
    val draft = ScheduleItem.CueItem(
        id = existing?.id ?: UUID.randomUUID().toString(),
        action = action,
        label = label.trim(),
        offsetMinutes = offset,
        absoluteTime = if (pinned) pinnedAt?.let(::storedTime).orEmpty() else "",
        payload = if (hasTarget) target else null,
        plays = if (action in CueAction.withPlays) plays else 1,
        enabled = existing?.enabled ?: true,
    )

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = if (existing == null) {
                stringResource(Res.string.calendar_new_cue, service.name)
            } else {
                stringResource(Res.string.calendar_edit_cue)
            },
            icon = Icons.Filled.Bolt,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                if (onDelete != null) {
                    QuietButton(label = stringResource(Res.string.calendar_delete), onClick = onDelete)
                }
                Spacer(Modifier.weight(1f))
                QuietButton(label = stringResource(Res.string.calendar_cancel), onClick = onDismiss)
                PrimaryButton(
                    label = if (existing == null) {
                        stringResource(Res.string.calendar_add_cue)
                    } else {
                        stringResource(Res.string.calendar_save_cue)
                    },
                    onClick = { onSave(draft) },
                    enabled = canSave,
                )
            },
        ) {
            // The design's grid: Do what across the top, the target list (with its preview) down
            // the left, Play / When / Label down the right. An action with no target — blank — has
            // nothing for the left column, so its right column takes the full width.
            Column(
                verticalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                ActionGrid(selected = action, onSelect = { action = it })
                Row(horizontalArrangement = Arrangement.spacedBy(COLUMN_GAP), verticalAlignment = Alignment.Top) {
                    if (hasTarget) {
                        TargetSection(
                            action = action,
                            service = service,
                            presets = presets,
                            selected = target,
                            plays = draft.plays,
                            onSelect = { payload = it },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(13.dp), modifier = Modifier.weight(1f)) {
                        if (action in CueAction.withPlays) {
                            PlaySection(plays = plays, onPlays = { plays = it })
                        }
                        WhenSection(
                            service = service,
                            draft = draft,
                            pinned = pinned,
                            pinnedTime = pinnedTime,
                            pinnedValid = pinnedValid,
                            onOffset = { offset = it; pinned = false },
                            onTogglePinned = { pinned = !pinned },
                            onPinnedTime = { pinnedTime = it },
                        )
                        Column {
                            FieldLabel(stringResource(Res.string.calendar_cue_label))
                            Spacer(Modifier.height(5.dp))
                            CompactTextField(
                                value = label,
                                onValueChange = { label = it },
                                placeholder = cueActionLabel(action),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** `Do what`: the offered actions as a two-column grid, a colored dot before each. */
@Composable
private fun ActionGrid(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        FieldLabel(stringResource(Res.string.calendar_cue_do_what))
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CueAction.offered.chunked(ACTION_COLUMNS).forEach { line ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    line.forEach { action ->
                        val on = action == selected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(ACTION_HEIGHT)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) scheme.primary.copy(alpha = ON_TINT) else scheme.surface)
                                .border(
                                    1.dp,
                                    if (on) scheme.primary.copy(alpha = ON_BORDER) else scheme.outlineVariant,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { onSelect(action) }
                                .padding(horizontal = 10.dp),
                        ) {
                            Box(Modifier.size(DOT).clip(CircleShape).background(actionColor(action)))
                            Text(
                                text = cueActionLabel(action),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                                color = if (on) scheme.onSurface else scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    // A short last line keeps its cells the same width as the full ones.
                    repeat(ACTION_COLUMNS - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

private const val ACTION_COLUMNS = 3

/** The dot's hue per action — the theme's roles, so it follows every theme. */
@Composable
private fun actionColor(action: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        CueAction.COUNTDOWN -> scheme.primary
        CueAction.GO_LIVE -> scheme.tertiary
        CueAction.SCENE -> scheme.error.copy(alpha = 0.8f)
        CueAction.BLANK -> scheme.outline
        else -> scheme.secondary
    }
}

/** Where a target comes from — the design's `In this service | Saved presets` switch. */
private enum class TargetSource { SERVICE, PRESETS }

/**
 * The target list: what the chosen action can point at, from the run of show or from the presets,
 * as `[icon] label …… meta` rows. A countdown and go live each carry a built-in first row — the
 * countdown to the service start, the first item of the run of show — so they work with nothing
 * picked; the loop and a scene have to be pointed at something, and say so when there is nothing.
 */
@Composable
private fun TargetSection(
    action: String,
    service: PlannedService,
    presets: List<ItemPreset>,
    selected: ScheduleItem?,
    plays: Int,
    onSelect: (ScheduleItem?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val rows = serviceTargets(service, action)
    val fromPresets = presets.filter { it.item.isTargetFor(action) }
    // Open on whichever side the saved pick came from, so an edit shows what it was set to.
    val initial = if (selected != null && rows.none { it.item.sameAs(selected) } &&
        fromPresets.any { it.item.sameAs(selected) }
    ) TargetSource.PRESETS else TargetSource.SERVICE
    var source by remember(action) { mutableStateOf(initial) }
    var filter by remember(action, source) { mutableStateOf("") }
    val pool = if (source == TargetSource.SERVICE) {
        rows.map { Target("row:" + it.item.id, it.item, it.item.displayText, it.meta) }
    } else {
        fromPresets.map { Target("preset:" + it.id, it.item, it.name, it.item.displayText) }
    }
    val query = filter.trim()
    val shown = if (query.isEmpty()) pool else pool.filter { it.label.contains(query, true) || it.meta.contains(query, true) }
    // Which row is lit. The row that was clicked, by its own key -- two presets saved from the
    // same scene hold equal items, so matching on the item lit both. Before anything is clicked
    // (an existing cue being edited) the first row holding the saved item stands for it.
    var pickedKey by remember(action) { mutableStateOf<String?>(null) }
    val picked = pool.firstOrNull { it.key == pickedKey }
        ?: selected?.let { sel -> pool.firstOrNull { it.item.sameAs(sel) } }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FieldLabel(targetLabel(action))
            Text(
                text = stringResource(
                    if (source == TargetSource.SERVICE) Res.string.calendar_cue_hint_service else Res.string.calendar_cue_hint_presets
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        SegmentedSelector(
            options = TargetSource.entries,
            selected = source,
            label = { side ->
                val count = if (side == TargetSource.SERVICE) rows.size else fromPresets.size
                stringResource(
                    if (side == TargetSource.SERVICE) Res.string.calendar_cue_source_service else Res.string.calendar_cue_source_presets
                ) + "  " + count
            },
            onSelect = { source = it },
        )
        val builtIn = builtInTarget(action, service, source)
        if (pool.isEmpty() && builtIn == null) {
            WarningNote(
                stringResource(
                    if (source == TargetSource.SERVICE) Res.string.calendar_cue_empty_service else Res.string.calendar_cue_empty_presets
                )
            )
            return@Column
        }
        // A filter only once the list is long enough to need one — four rows are scanned faster
        // than they are typed for.
        if (pool.size > FILTER_FROM) {
            FilterField(
                value = filter,
                placeholder = stringResource(
                    if (source == TargetSource.SERVICE) Res.string.calendar_cue_filter_service else Res.string.calendar_cue_filter_presets
                ),
                onChange = { filter = it },
            )
        }
        if (shown.isEmpty() && builtIn == null) {
            Text(
                text = stringResource(Res.string.calendar_cue_nothing_matches, query),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
                textAlign = TextAlign.Center,
            )
        }
        ScrollableColumn(modifier = Modifier.heightIn(max = TARGET_LIST_MAX), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            if (builtIn != null && (query.isEmpty() || builtIn.first.contains(query, true))) {
                TargetRow(
                    icon = { Icon(Icons.Filled.Timer, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(12.dp)) },
                    label = builtIn.first,
                    meta = builtIn.second,
                    selected = selected == null,
                    onClick = { pickedKey = null; onSelect(null) },
                )
            }
            shown.forEach { target ->
                val look = lookFor(target.item)
                TargetRow(
                    icon = { Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(12.dp)) },
                    label = target.label,
                    meta = target.meta,
                    selected = target.key == picked?.key,
                    onClick = { pickedKey = target.key; onSelect(target.item) },
                )
            }
        }
        when {
            picked != null -> PreviewCard(
                item = picked.item,
                name = picked.label,
                meta = picked.meta.ifBlank { stringResource(Res.string.calendar_cue_run_of_show) },
                tag = stringResource(
                    if (source == TargetSource.PRESETS) Res.string.calendar_cue_preview_preset else Res.string.calendar_cue_preview_service
                ),
                note = stringResource(
                    if (source == TargetSource.PRESETS) Res.string.calendar_cue_preview_note_preset else Res.string.calendar_cue_preview_note_service
                ),
                accent = source == TargetSource.PRESETS,
                plays = if (action in CueAction.withPlays) plays else 1,
            )
            selected == null && builtIn != null -> PreviewCard(
                item = null,
                name = builtIn.first,
                meta = builtIn.second,
                tag = stringResource(Res.string.calendar_cue_preview_builtin),
                note = stringResource(Res.string.calendar_cue_preview_note_builtin),
                accent = false,
                plays = 1,
            )
        }
    }
}

/** The filter over a long target list: a search glyph, the field, and a clear button once typed. */
@Composable
private fun FilterField(value: String, placeholder: String, onChange: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(contentAlignment = Alignment.CenterEnd) {
        CompactTextField(
            value = value,
            onValueChange = onChange,
            placeholder = placeholder,
            height = FILTER_HEIGHT,
            fontSize = 11.5f,
            focused = value.isNotEmpty(),
            leading = {
                Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(12.dp))
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (value.isNotEmpty()) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(19.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(scheme.surfaceVariant.copy(alpha = CARD_TINT))
                    .clickable { onChange("") },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(Res.string.calendar_cue_filter_clear),
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(8.dp),
                )
            }
        }
    }
}

/**
 * What the cue will show, under the list: a 16:9 tile with the item's glyph and, when it repeats,
 * a LOOP / N× badge; then the name, where it comes from, and what that means.
 */
@Composable
private fun PreviewCard(
    item: ScheduleItem?,
    name: String,
    meta: String,
    tag: String,
    note: String,
    accent: Boolean,
    plays: Int,
) {
    val scheme = MaterialTheme.colorScheme
    val tint = if (accent) scheme.primary else scheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .clip(SheetMetrics.cardRadius)
            .background(scheme.surface)
            .border(1.dp, scheme.outlineVariant, SheetMetrics.cardRadius),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
            modifier = Modifier.fillMaxWidth().height(25.dp).padding(horizontal = 9.dp),
        ) {
            SheetOverline(stringResource(Res.string.calendar_cue_preview), Modifier.weight(1f))
            Text(
                text = tag,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                fontWeight = FontWeight.Bold,
                color = tint,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(tint.copy(alpha = ON_TINT))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(scheme.outlineVariant.copy(alpha = CARD_TINT)))
        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.padding(9.dp)) {
            val look = item?.let { lookFor(it) }
            val tile = look?.color ?: scheme.primary
            Box(
                Modifier
                    .width(PREVIEW_THUMB)
                    .aspectRatio(THUMB_RATIO)
                    .clip(RoundedCornerShape(7.dp))
                    .background(tile.copy(alpha = ON_TINT))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(look?.icon ?: Icons.Filled.Timer, contentDescription = null, tint = tile, modifier = Modifier.size(20.dp))
                if (plays != 1) LoopBadge(plays)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp, lineHeight = 13.sp),
                    color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Composable
private fun BoxScope.LoopBadge(plays: Int) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 5.dp, bottom = 4.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(scheme.scrim.copy(alpha = 0.7f))
            .padding(horizontal = 4.dp, vertical = 1.dp),
    ) {
        Icon(Icons.Filled.Repeat, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(8.dp))
        Text(
            text = if (plays == LOOP_FOREVER) stringResource(Res.string.calendar_cue_loop_badge) else stringResource(Res.string.calendar_play_n_times, plays),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 7.5.sp),
            fontWeight = FontWeight.ExtraBold,
            color = scheme.tertiary,
        )
    }
}

private data class Target(val key: String, val item: ScheduleItem, val label: String, val meta: String)
private data class ServiceTarget(val item: ScheduleItem, val meta: String)

/** The run-of-show rows [action] can use, each with the section it sits under and its length. */
private fun serviceTargets(service: PlannedService, action: String): List<ServiceTarget> {
    var section = ""
    return service.items.mapNotNull { item ->
        if (item is ScheduleItem.LabelItem) {
            section = item.displayText
            return@mapNotNull null
        }
        if (!item.isTargetFor(action)) return@mapNotNull null
        val length = service.plannedSeconds[item.id]?.let(::formatDuration)
        ServiceTarget(item, listOfNotNull(section.ifBlank { null }, length).joinToString(" \u00b7 "))
    }
}

/** The row an action falls back to when nothing is picked, as label and meta; null if it has none. */
@Composable
private fun builtInTarget(action: String, service: PlannedService, source: TargetSource): Pair<String, String>? {
    if (source != TargetSource.SERVICE) return null
    return when (action) {
        CueAction.COUNTDOWN ->
            stringResource(
                Res.string.calendar_cue_builtin_countdown,
                clockText(service.startTime, LocalUse24HourClock.current),
            ) to
                stringResource(Res.string.calendar_cue_builtin)
        CueAction.GO_LIVE ->
            stringResource(Res.string.calendar_cue_first_item) to stringResource(Res.string.calendar_cue_default)
        else -> null
    }
}

/** Matched by id first, then by what it shows: a cue holds a copy whose id is not the row's. */
private fun ScheduleItem.sameAs(other: ScheduleItem): Boolean = id == other.id || displayText == other.displayText

@Composable
private fun targetLabel(action: String): String = stringResource(
    when (action) {
        CueAction.COUNTDOWN -> Res.string.calendar_cue_target_timer
        CueAction.PROJECT -> Res.string.calendar_cue_target_slides
        CueAction.SCENE -> Res.string.calendar_cue_target_scene
        else -> Res.string.calendar_cue_target_item
    }
)

@Composable
private fun TargetRow(
    icon: @Composable () -> Unit,
    label: String,
    meta: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TARGET_MIN_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) scheme.primary.copy(alpha = ON_TINT) else scheme.surface)
            .border(1.dp, if (selected) scheme.primary.copy(alpha = ON_BORDER) else scheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Box(Modifier.width(18.dp), contentAlignment = Alignment.Center) { icon() }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 9.5.sp),
                color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

/** The design's amber note for a target list with nothing in it. */
@Composable
private fun WarningNote(text: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(scheme.tertiary.copy(alpha = WARN_TINT))
            .border(1.dp, scheme.tertiary.copy(alpha = WARN_TINT * 2), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = scheme.tertiary, modifier = Modifier.size(11.dp).padding(top = 1.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 14.sp),
            color = scheme.tertiary,
        )
    }
}

/** `Play`: Once / 2× / 3× / 5× / Loop as chips, with the design's note under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaySection(plays: Int, onPlays: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val choices = if (plays in PLAY_CHOICES) PLAY_CHOICES else (PLAY_CHOICES + plays).sortedBy { if (it == LOOP_FOREVER) Int.MAX_VALUE else it }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        FieldLabel(stringResource(Res.string.calendar_cue_play))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            choices.forEach { choice ->
                val on = choice == plays
                Box(
                    Modifier
                        .height(OFFSET_HEIGHT)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) scheme.primary else scheme.surface)
                        .border(1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { onPlays(choice) }
                        .padding(horizontal = 13.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when (choice) {
                            1 -> stringResource(Res.string.calendar_play_once)
                            LOOP_FOREVER -> stringResource(Res.string.calendar_play_loop)
                            else -> stringResource(Res.string.calendar_play_n_times, choice)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Text(
            text = when (plays) {
                LOOP_FOREVER -> stringResource(Res.string.calendar_play_note_loop)
                1 -> stringResource(Res.string.calendar_play_note_once)
                else -> stringResource(Res.string.calendar_play_note_times, plays)
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
            color = scheme.onSurfaceVariant,
        )
    }
}

/** `When`: offset chips, the resolved clock time, the pin checkbox and its field, and the hint. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenSection(
    service: PlannedService,
    draft: ScheduleItem.CueItem,
    pinned: Boolean,
    pinnedTime: String,
    pinnedValid: Boolean,
    onOffset: (Int) -> Unit,
    onTogglePinned: () -> Unit,
    onPinnedTime: (String) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FieldLabel(stringResource(Res.string.calendar_cue_when), Modifier.weight(1f))
            Text(
                text = cueTimeText(draft, service.startTime),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.5.sp),
                color = scheme.primary,
            )
        }
        // Eight chips do not fit one line at this width; the design wraps them, so they wrap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CUE_OFFSETS.forEach { minutes ->
                val on = !pinned && draft.offsetMinutes == minutes
                Box(
                    Modifier
                        .height(OFFSET_HEIGHT)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (on) scheme.primary else scheme.surface)
                        .border(1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(8.dp))
                        .clickable { onOffset(minutes) }
                        .padding(horizontal = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = offsetLabel(minutes),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                        color = if (on) scheme.onPrimary else scheme.onSurfaceVariant.copy(alpha = if (pinned) CARD_TINT else 1f),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().clip(CalendarMetrics.smallRadius).clickable(onClick = onTogglePinned).padding(top = 3.dp),
        ) {
            Box(
                Modifier
                    .size(CHECK_BOX)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (pinned) scheme.tertiary else Color.Transparent)
                    .border(1.5.dp, if (pinned) scheme.tertiary else scheme.outline, RoundedCornerShape(4.dp)),
                contentAlignment = Alignment.Center,
            ) {
                if (pinned) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.onTertiary, modifier = Modifier.size(11.dp))
                }
            }
            Text(
                text = stringResource(Res.string.calendar_cue_pin),
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 11.5.sp),
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            if (pinned) {
                CompactTextField(
                    value = pinnedTime,
                    onValueChange = onPinnedTime,
                    placeholder = clockText(DEFAULT_PINNED, LocalUse24HourClock.current),
                    errorBorder = !pinnedValid,
                    height = 28.dp,
                    modifier = Modifier.width(TIME_FIELD),
                )
            }
        }
        val hintColor = if (pinned) scheme.tertiary else scheme.primary
        Text(
            text = if (pinned) {
                stringResource(
                    Res.string.calendar_cue_hint_pinned,
                    parseClockText(pinnedTime)?.let { clockText(it, LocalUse24HourClock.current) } ?: pinnedTime,
                )
            } else {
                stringResource(
                    Res.string.calendar_cue_hint_relative,
                    offsetLabel(draft.offsetMinutes).lowercase(),
                    cueTimeText(draft, service.startTime),
                )
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp, lineHeight = 15.sp),
            color = hintColor,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(hintColor.copy(alpha = HINT_TINT))
                .border(1.dp, hintColor.copy(alpha = HINT_TINT * 2), RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

/** What the pinned-time field starts out holding: the cue's own time, or the default, in the format that is on. */
private fun pinnedTimeText(existing: ScheduleItem.CueItem?, use24Hour: Boolean): String =
    clockText(existing?.absoluteTime?.ifEmpty { null } ?: DEFAULT_PINNED, use24Hour)
