package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.clockText
import org.churchpresenter.calendar.model.parseClockText
import org.churchpresenter.calendar.model.storedTime
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.raised
import org.churchpresenter.theme.elevationPalette

private val SHEET_WIDTH = 460.dp
/** `Start time` and `Type` share the row as 1 : 1.3, so three segment labels are not truncated. */
private const val TYPE_FLEX = 1.3f

/**
 * Everything the sheet collects, handed back whole on Save.
 *
 * [template] only means something for a new service; [wholeSeries] only for one that is already
 * part of a series. The sheet shows whichever applies and leaves the other at its default.
 */
data class ServiceForm(
    val name: String,
    val startTime: String,
    val kind: ServiceKind,
    val template: ServiceTemplate = ServiceTemplate.Blank,
    val wholeSeries: Boolean = false,
)

/**
 * Create or edit one service.
 *
 * Laid out to the design: `Name` full width, `Start time` beside a three-way `Type` selector,
 * `Applies to` for a series member, and — only when the service is new — a `Start from` list.
 * Delete sits at the far left of the footer, away from Save.
 *
 * The fields are [CompactTextField], not Material 3's `OutlinedTextField`. That is the whole reason
 * this dialog reads as the design now: an M3 field is about 56dp tall with a floating label, which
 * made a one-line name entry taller than the chip row beside it and turned the sheet into a form.
 */
@Composable
fun ServiceSheet(
    existing: PlannedService?,
    defaultStartTime: String,
    date: LocalDate,
    /** How many services share [existing]'s series, for the `Applies to` note. Zero for a one-off. */
    seriesSize: Int,
    templates: List<ServiceTemplate>,
    templateLabel: @Composable (ServiceTemplate) -> Pair<String, String>,
    onSave: (ServiceForm) -> Unit,
    onDelete: ((wholeSeries: Boolean) -> Unit)?,
    onDismiss: () -> Unit,
) {
    // The form holds the time as it is shown; it is stored as HH:mm only on save.
    val use24Hour = LocalUse24HourClock.current
    var form by remember(existing, use24Hour) {
        mutableStateOf(
            ServiceForm(
                name = existing?.name ?: "",
                startTime = clockText(existing?.startTime ?: defaultStartTime, use24Hour),
                kind = ServiceKind.from(existing?.kind ?: ServiceKind.SUNDAY.id),
            )
        )
    }
    val startAt = parseClockText(form.startTime)
    val canSave = form.name.isNotBlank() && startAt != null

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = if (existing == null) {
                stringResource(Res.string.calendar_new_service_on, shortDate(date))
            } else {
                stringResource(Res.string.calendar_edit_service)
            },
            icon = Icons.Filled.EventNote,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                if (onDelete != null) {
                    QuietButton(
                        label = stringResource(Res.string.calendar_delete),
                        onClick = { onDelete(form.wholeSeries) },
                    )
                }
                Spacer(Modifier.weight(1f))
                QuietButton(label = stringResource(Res.string.calendar_cancel), onClick = onDismiss)
                PrimaryButton(
                    label = if (existing == null) {
                        stringResource(Res.string.calendar_add_service_save)
                    } else {
                        stringResource(Res.string.calendar_save)
                    },
                    onClick = {
                        startAt?.let { onSave(form.copy(name = form.name.trim(), startTime = storedTime(it))) }
                    },
                    enabled = canSave,
                )
            },
        ) {
            ServiceFields(
                form = form,
                existing = existing,
                seriesSize = seriesSize,
                templates = templates,
                templateLabel = templateLabel,
                onChange = { form = it },
            )
        }
    }
}

@Composable
private fun ServiceFields(
    form: ServiceForm,
    existing: PlannedService?,
    seriesSize: Int,
    templates: List<ServiceTemplate>,
    templateLabel: @Composable (ServiceTemplate) -> Pair<String, String>,
    onChange: (ServiceForm) -> Unit,
) {
    val timeValid = parseClockText(form.startTime) != null
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 13.dp),
    ) {
        Column {
            FieldLabel(stringResource(Res.string.calendar_service_name))
            Spacer(Modifier.height(5.dp))
            CompactTextField(
                value = form.name,
                onValueChange = { onChange(form.copy(name = it)) },
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
                    value = form.startTime,
                    onValueChange = { onChange(form.copy(startTime = it)) },
                    errorBorder = !timeValid,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Column(Modifier.weight(TYPE_FLEX)) {
                FieldLabel(stringResource(Res.string.calendar_service_type))
                Spacer(Modifier.height(5.dp))
                SegmentedSelector(
                    options = ServiceKind.entries,
                    selected = form.kind,
                    label = { kindShortLabel(it) },
                    onSelect = { onChange(form.copy(kind = it)) },
                )
            }
        }

        // Below the row rather than under the field, so an invalid time does not resize the
        // Type selector beside it.
        if (!timeValid) {
            Text(
                text = stringResource(
                    Res.string.calendar_invalid_time,
                    clockText(EXAMPLE_TIME, LocalUse24HourClock.current),
                ),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.sp),
                color = MaterialTheme.colorScheme.error,
            )
        }

        if (existing != null && existing.isInSeries()) {
            SeriesScopeSection(
                repeat = ServiceRepeat.from(existing.repeat),
                seriesSize = seriesSize,
                wholeSeries = form.wholeSeries,
                onScope = { onChange(form.copy(wholeSeries = it)) },
            )
        }

        if (existing == null && templates.isNotEmpty()) {
            val use24Hour = LocalUse24HourClock.current
            Column {
                FieldLabel(stringResource(Res.string.calendar_start_from))
                Spacer(Modifier.height(5.dp))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    templates.forEach { option ->
                        val (label, sub) = templateLabel(option)
                        TemplateRow(
                            label = label,
                            sub = sub,
                            selected = option.id == form.template.id,
                            onClick = { onChange(form.withTemplate(option, use24Hour)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Picking a template pre-fills the name, time and type from it — the fields are the same ones
 * saved with it, and typing "Sunday Morning" over a template already called that is busywork.
 * Blank leaves the fields alone.
 */
private fun ServiceForm.withTemplate(option: ServiceTemplate, use24Hour: Boolean): ServiceForm = when (option) {
    ServiceTemplate.Blank -> copy(template = option)
    is ServiceTemplate.CopyOf -> copy(
        template = option,
        name = option.service.name,
        startTime = clockText(option.service.startTime, use24Hour),
        kind = ServiceKind.from(option.service.kind),
    )

    is ServiceTemplate.Saved -> copy(
        template = option,
        name = option.template.name,
        startTime = clockText(option.template.startTime, use24Hour),
        kind = ServiceKind.from(option.template.kind),
    )
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
        val palette = elevationPalette()
        // The app's radio: a sunken well, or a raised accent disc with a dot when chosen.
        Box(
            Modifier
                .size(14.dp)
                .then(
                    if (selected) {
                        Modifier.raised(CircleShape, palette.accent, palette, lift = 2.dp)
                    } else {
                        Modifier.sunken(CircleShape, palette)
                    }
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Box(Modifier.size(6.dp).clip(CircleShape).background(palette.accent.ink))
            }
        }
        CardText(title = label, subtitle = sub)
    }
}

private const val ON_TINT = 0.16f

/** The time the invalid-time hint is written with, in whichever format is on. */
private const val EXAMPLE_TIME = "10:00"
