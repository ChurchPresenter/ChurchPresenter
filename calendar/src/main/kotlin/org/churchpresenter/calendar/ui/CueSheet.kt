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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_add_cue
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_cue_do_what
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_pinned
import org.churchpresenter.calendar.generated.resources.calendar_cue_hint_relative
import org.churchpresenter.calendar.generated.resources.calendar_cue_item
import org.churchpresenter.calendar.generated.resources.calendar_cue_label
import org.churchpresenter.calendar.generated.resources.calendar_cue_no_items
import org.churchpresenter.calendar.generated.resources.calendar_cue_pin
import org.churchpresenter.calendar.generated.resources.calendar_cue_when
import org.churchpresenter.calendar.generated.resources.calendar_delete
import org.churchpresenter.calendar.generated.resources.calendar_edit_cue
import org.churchpresenter.calendar.generated.resources.calendar_new_cue
import org.churchpresenter.calendar.generated.resources.calendar_save_cue
import org.churchpresenter.calendar.model.CUE_OFFSETS
import org.churchpresenter.calendar.model.CueAction
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceCue
import org.churchpresenter.calendar.model.isProjectableByCue
import org.churchpresenter.calendar.model.parseStoredTime
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource
import java.util.UUID

private val SHEET_WIDTH = 430.dp
private val ACTION_HEIGHT = 32.dp
private val OFFSET_HEIGHT = 29.dp
private val TIME_FIELD = 68.dp
private val ITEM_LIST_MAX = 150.dp
private val DOT = 7.dp
private val CHECK_BOX = 16.dp
private const val ON_TINT = 0.16f
private const val ON_BORDER = 0.55f
private const val CARD_TINT = 0.4f
private const val HINT_TINT = 0.10f
private const val DEFAULT_OFFSET = -15
private const val DEFAULT_PINNED = "11:00"

/**
 * Create or edit one cue.
 *
 * Laid out to the design: `Do what` as a two-column grid of actions; `Item` when the action shows
 * one, chosen from the service's own run of show; `When` as the offset chips with the resolved
 * clock time beside the heading, the pin-to-wall-clock checkbox under them and a hint saying what
 * the choice means; then `Label`. Delete sits at the far left of the footer, away from Save.
 */
@Composable
fun CueSheet(
    service: PlannedService,
    existing: ServiceCue?,
    onSave: (ServiceCue) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var action by remember(existing) { mutableStateOf(existing?.action ?: CueAction.COUNTDOWN) }
    var payload by remember(existing) { mutableStateOf(existing?.payload) }
    var offset by remember(existing) { mutableStateOf(existing?.offsetMinutes ?: DEFAULT_OFFSET) }
    var pinned by remember(existing) { mutableStateOf(existing?.isPinned() == true) }
    var pinnedTime by remember(existing) { mutableStateOf(existing?.absoluteTime?.ifEmpty { null } ?: DEFAULT_PINNED) }
    var label by remember(existing) { mutableStateOf(existing?.label ?: "") }

    val pinnedValid = !pinned || parseStoredTime(pinnedTime) != null
    val needsItem = action == CueAction.PROJECT
    val canSave = pinnedValid && (!needsItem || payload != null)
    val draft = ServiceCue(
        id = existing?.id ?: UUID.randomUUID().toString(),
        offsetMinutes = offset,
        absoluteTime = if (pinned) pinnedTime.trim() else "",
        label = label.trim(),
        payload = if (needsItem) payload else null,
        action = action,
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
            Column(
                verticalArrangement = Arrangement.spacedBy(13.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                ActionGrid(selected = action, onSelect = { action = it })
                if (needsItem) {
                    ItemChooser(service = service, selected = payload, onSelect = { payload = it })
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

/** `Do what`: the offered actions as a two-column grid, a colored dot before each. */
@Composable
private fun ActionGrid(selected: String, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        FieldLabel(stringResource(Res.string.calendar_cue_do_what))
        Spacer(Modifier.height(6.dp))
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            CueAction.offered.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    pair.forEach { action ->
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
                }
            }
        }
    }
}

/** The dot's hue per action — the theme's roles, so it follows every theme. */
@Composable
private fun actionColor(action: String): Color {
    val scheme = MaterialTheme.colorScheme
    return when (action) {
        CueAction.COUNTDOWN -> scheme.primary
        CueAction.GO_LIVE -> scheme.tertiary
        CueAction.BLANK -> scheme.error
        else -> scheme.secondary
    }
}

/** `Item`: the run of show's projectable rows as radio cards. The cue keeps a copy of the pick. */
@Composable
private fun ItemChooser(service: PlannedService, selected: ScheduleItem?, onSelect: (ScheduleItem) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val rows = service.items.filter { it.isProjectableByCue() }
    Column {
        FieldLabel(stringResource(Res.string.calendar_cue_item))
        Spacer(Modifier.height(6.dp))
        if (rows.isEmpty()) {
            Text(
                text = stringResource(Res.string.calendar_cue_no_items),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = scheme.onSurfaceVariant,
            )
            return@Column
        }
        ScrollableColumn(
            modifier = Modifier.heightIn(max = ITEM_LIST_MAX),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            rows.forEach { row ->
                // Matched by id first, then by what it shows: a cue saved last week holds a copy
                // whose id is not the row's, and should still read as that row.
                val on = selected != null && (selected.id == row.id || selected.displayText == row.displayText)
                val look = lookFor(row)
                SettingCard(
                    horizontalPadding = 10.dp,
                    verticalPadding = 7.dp,
                    modifier = Modifier
                        .clip(SheetMetrics.cardRadius)
                        .background(if (on) scheme.primary.copy(alpha = ON_TINT) else Color.Transparent)
                        .clickable { onSelect(row) },
                ) {
                    Box(
                        Modifier
                            .size(CalendarMetrics.rowIcon)
                            .clip(RoundedCornerShape(6.dp))
                            .background(look.color.copy(alpha = ON_TINT)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(12.dp))
                    }
                    CardText(title = row.displayText, subtitle = row.subtitle())
                    if (on) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
    }
}

/** `When`: offset chips, the resolved clock time, the pin checkbox and its field, and the hint. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhenSection(
    service: PlannedService,
    draft: ServiceCue,
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
                    placeholder = DEFAULT_PINNED,
                    errorBorder = !pinnedValid,
                    height = 28.dp,
                    modifier = Modifier.width(TIME_FIELD),
                )
            }
        }
        val hintColor = if (pinned) scheme.tertiary else scheme.primary
        Text(
            text = if (pinned) {
                stringResource(Res.string.calendar_cue_hint_pinned, pinnedTime)
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
