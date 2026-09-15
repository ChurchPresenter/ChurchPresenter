package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
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
import org.churchpresenter.calendar.generated.resources.calendar_add_service_save
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_delete
import org.churchpresenter.calendar.generated.resources.calendar_edit_service
import org.churchpresenter.calendar.generated.resources.calendar_invalid_time
import org.churchpresenter.calendar.generated.resources.calendar_new_service_on
import org.churchpresenter.calendar.generated.resources.calendar_save
import org.churchpresenter.calendar.generated.resources.calendar_service_name
import org.churchpresenter.calendar.generated.resources.calendar_service_name_hint
import org.churchpresenter.calendar.generated.resources.calendar_service_start
import org.churchpresenter.calendar.generated.resources.calendar_service_type
import org.churchpresenter.calendar.generated.resources.calendar_start_from
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.parseStoredTime
import org.jetbrains.compose.resources.stringResource

private val SHEET_WIDTH = 460.dp
/** `Start time` and `Type` share the row as 1 : 1.3, so three segment labels are not truncated. */
private const val TYPE_FLEX = 1.3f
private val SELECTOR_HEIGHT = 34.dp
private val TRACK_PADDING = 2.dp
private val TRACK_GAP = 2.dp
private const val TRACK_TINT = 0.5f

/**
 * Create or edit one service.
 *
 * Laid out to the design: `Name` full width, `Start time` beside a three-way `Type` selector, and —
 * only when the service is new — a `Start from` list. Delete sits at the far left of the footer,
 * away from Save.
 *
 * The fields are [CompactTextField], not Material 3's `OutlinedTextField`. That is the whole reason
 * this dialog reads as the design now: an M3 field is about 56dp tall with a floating label, which
 * made a one-line name entry taller than the chip row beside it and turned the sheet into a form.
 */
@Composable
fun ServiceSheet(
    existing: PlannedService?,
    defaultStartTime: String,
    dateLabel: String,
    templates: List<ServiceTemplate>,
    templateLabel: @Composable (ServiceTemplate) -> Pair<String, String>,
    onSave: (name: String, startTime: String, kind: ServiceKind, template: ServiceTemplate) -> Unit,
    onDelete: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    var name by remember(existing) { mutableStateOf(existing?.name ?: "") }
    var startTime by remember(existing) { mutableStateOf(existing?.startTime ?: defaultStartTime) }
    var kind by remember(existing) { mutableStateOf(ServiceKind.from(existing?.kind ?: ServiceKind.SUNDAY.id)) }
    var template by remember(existing) { mutableStateOf<ServiceTemplate>(ServiceTemplate.Blank) }

    val timeValid = parseStoredTime(startTime) != null
    val canSave = name.isNotBlank() && timeValid

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = if (existing == null) {
                stringResource(Res.string.calendar_new_service_on, dateLabel)
            } else {
                stringResource(Res.string.calendar_edit_service)
            },
            icon = Icons.Filled.EventNote,
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
                        stringResource(Res.string.calendar_add_service_save)
                    } else {
                        stringResource(Res.string.calendar_save)
                    },
                    onClick = { onSave(name.trim(), startTime.trim(), kind, template) },
                    enabled = canSave,
                )
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
            ) {
                Column {
                    FieldLabel(stringResource(Res.string.calendar_service_name))
                    Spacer(Modifier.height(5.dp))
                    CompactTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = stringResource(Res.string.calendar_service_name_hint),
                        focused = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        FieldLabel(stringResource(Res.string.calendar_service_start))
                        Spacer(Modifier.height(5.dp))
                        CompactTextField(
                            value = startTime,
                            onValueChange = { startTime = it },
                            errorBorder = !timeValid,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Column(Modifier.weight(TYPE_FLEX)) {
                        FieldLabel(stringResource(Res.string.calendar_service_type))
                        Spacer(Modifier.height(5.dp))
                        KindSelector(selected = kind, onSelect = { kind = it })
                    }
                }

                // Below the row rather than under the field, so an invalid time does not resize the
                // Type selector beside it.
                if (!timeValid) {
                    Text(
                        text = stringResource(Res.string.calendar_invalid_time),
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                if (existing == null && templates.isNotEmpty()) {
                    Column {
                        FieldLabel(stringResource(Res.string.calendar_start_from))
                        Spacer(Modifier.height(5.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            templates.forEach { option ->
                                val (label, sub) = templateLabel(option)
                                TemplateRow(
                                    label = label,
                                    sub = sub,
                                    selected = option.id == template.id,
                                    onClick = { template = option },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The service-type control: a **segmented** selector, not three buttons.
 *
 * The design is one inset track — its own background, one border, 2dp of padding — holding three
 * borderless segments that share it, with the selected one filled. Three separately-bordered pills
 * with gaps between them is a different control entirely: it reads as three independent toggles
 * rather than as one field with three states, which is what `Type` is.
 *
 * No color dots either. The kind's color belongs on the day grid and the service chip, where it
 * distinguishes one service from another; inside a three-way picker whose options are already
 * named, it is decoration competing with the label.
 */
@Composable
private fun KindSelector(selected: ServiceKind, onSelect: (ServiceKind) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val trackShape = RoundedCornerShape(8.dp)
    Row(
        horizontalArrangement = Arrangement.spacedBy(TRACK_GAP),
        modifier = Modifier
            .fillMaxWidth()
            .height(SELECTOR_HEIGHT)
            .clip(trackShape)
            .background(scheme.surfaceVariant.copy(alpha = TRACK_TINT))
            .border(1.dp, scheme.outlineVariant, trackShape)
            .padding(TRACK_PADDING),
    ) {
        ServiceKind.entries.forEach { kind ->
            val on = kind == selected
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (on) scheme.primary else Color.Transparent)
                    .clickable { onSelect(kind) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = kindShortLabel(kind),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    color = if (on) scheme.onPrimary else scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** One `Start from` option: a radio dot, a label and the line under it. */
@Composable
private fun TemplateRow(label: String, sub: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    SettingCard(
        horizontalPadding = 10.dp,
        verticalPadding = 8.dp,
        modifier = Modifier
            .clip(SheetMetrics.cardRadius)
            .background(if (selected) scheme.primary.copy(alpha = ON_TINT) else Color.Transparent)
            .clickable(onClick = onClick),
    ) {
        Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .border(1.5.dp, if (selected) scheme.primary else scheme.outline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(scheme.primary))
            }
        }
        CardText(title = label, subtitle = sub)
    }
}

private const val ON_TINT = 0.16f
