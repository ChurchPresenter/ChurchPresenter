package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_include
import org.churchpresenter.calendar.generated.resources.calendar_save_template_sub
import org.churchpresenter.calendar.generated.resources.calendar_save_template_title
import org.churchpresenter.calendar.generated.resources.calendar_template_footer
import org.churchpresenter.calendar.generated.resources.calendar_template_include_items
import org.churchpresenter.calendar.generated.resources.calendar_template_include_items_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_include_sections
import org.churchpresenter.calendar.generated.resources.calendar_template_include_sections_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_name
import org.churchpresenter.calendar.generated.resources.calendar_template_replace_note
import org.churchpresenter.calendar.generated.resources.calendar_template_save
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.SavedTemplate
import org.jetbrains.compose.resources.stringResource
import java.time.LocalDate

private val SHEET_WIDTH = 410.dp

/**
 * Names the template **Template** is about to save from [service], and picks what goes in it.
 *
 * Laid out to the design: the name, pre-filled with the service's so accepting it is one click,
 * a warning when that name is already taken, and an `Include` list. The design's list also has
 * cues; the planner has no cues yet, so that box waits for the automation engine rather than
 * sitting here wired to nothing.
 */
@Composable
fun TemplateSheet(
    service: PlannedService,
    date: LocalDate,
    existing: List<SavedTemplate>,
    onSave: (name: String, includeSections: Boolean, includeItems: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(service) { mutableStateOf(service.name) }
    var sections by remember(service) { mutableStateOf(true) }
    var items by remember(service) { mutableStateOf(true) }
    val trimmed = name.trim()
    val replaces = existing.any { it.name.equals(trimmed, ignoreCase = true) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = stringResource(Res.string.calendar_save_template_title),
            subtitle = stringResource(Res.string.calendar_save_template_sub, service.name, shortDate(date)),
            icon = Icons.Filled.Dashboard,
            width = SHEET_WIDTH,
            onDismiss = onDismiss,
            footer = {
                Text(
                    text = stringResource(Res.string.calendar_template_footer),
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                QuietButton(label = stringResource(Res.string.calendar_cancel), onClick = onDismiss)
                PrimaryButton(
                    label = stringResource(Res.string.calendar_template_save),
                    onClick = { onSave(trimmed, sections, items) },
                    enabled = trimmed.isNotEmpty() && (sections || items),
                )
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 14.dp),
            ) {
                Column {
                    FieldLabel(stringResource(Res.string.calendar_template_name))
                    Spacer(Modifier.height(5.dp))
                    CompactTextField(
                        value = name,
                        onValueChange = { name = it },
                        focused = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (replaces) {
                        Spacer(Modifier.height(5.dp))
                        Text(
                            text = stringResource(Res.string.calendar_template_replace_note),
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FieldLabel(stringResource(Res.string.calendar_include))
                    Spacer(Modifier.height(2.dp))
                    IncludeRow(
                        label = stringResource(Res.string.calendar_template_include_sections),
                        sub = stringResource(Res.string.calendar_template_include_sections_sub),
                        on = sections,
                        onToggle = { sections = !sections },
                    )
                    IncludeRow(
                        label = stringResource(Res.string.calendar_template_include_items),
                        sub = stringResource(Res.string.calendar_template_include_items_sub),
                        on = items,
                        onToggle = { items = !items },
                    )
                }
            }
        }
    }
}
