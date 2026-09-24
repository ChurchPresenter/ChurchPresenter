package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.churchpresenter.calendar.CalendarCloudSync
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_section_add
import org.churchpresenter.calendar.generated.resources.calendar_section_insert
import org.churchpresenter.calendar.generated.resources.calendar_section_remove
import org.churchpresenter.calendar.generated.resources.calendar_sections_note
import org.churchpresenter.calendar.generated.resources.calendar_settings
import org.churchpresenter.calendar.generated.resources.calendar_settings_defaults
import org.churchpresenter.calendar.generated.resources.calendar_settings_done
import org.churchpresenter.calendar.generated.resources.calendar_settings_export
import org.churchpresenter.calendar.generated.resources.calendar_settings_sections
import org.churchpresenter.calendar.generated.resources.calendar_settings_sub
import org.churchpresenter.calendar.generated.resources.calendar_settings_templates
import org.churchpresenter.calendar.generated.resources.calendar_templates_empty_sub
import org.churchpresenter.calendar.generated.resources.calendar_templates_note
import org.churchpresenter.calendar.generated.resources.calendar_template_remove
import org.churchpresenter.calendar.generated.resources.calendar_template_saved_sub
import org.churchpresenter.calendar.model.SavedTemplate
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.generated.resources.calendar_settings_presets
import org.churchpresenter.calendar.generated.resources.calendar_presets_empty_sub
import org.churchpresenter.calendar.generated.resources.calendar_presets_note
import org.churchpresenter.calendar.generated.resources.calendar_preset_remove
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.SECTION_SWATCHES
import org.churchpresenter.calendar.model.SectionStyle
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.calendar.model.clockText
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.LocalContentColor
import org.churchpresenter.theme.components.SegmentTrackItem
import org.churchpresenter.theme.components.SegmentTrack
import org.churchpresenter.theme.sunken
import org.churchpresenter.theme.elevationPalette

private val DIALOG_WIDTH = 640.dp
private val BODY_HEIGHT = 340.dp
private val SWATCH_BUTTON = 26.dp
private val HEX_FIELD = 82.dp
/**
 * Calendar-wide settings — everything that applies to every service rather than to today's.
 *
 * `Sections`, `Presets` and `Defaults` are live; `Templates` lists what has been saved and says so
 * when nothing has. Cues are not settings: they are rows of the run of show, armed and skipped
 * there.
 */
@Composable
fun CalendarSettingsDialog(
    preferences: CalendarPreferences,
    templates: List<SavedTemplate>,
    presets: List<ItemPreset>,
    initialTab: SettingsTab = SettingsTab.SECTIONS,
    canInsertSection: Boolean,
    colorPicker: (@Composable (ColorPickerRequest) -> Unit)?,
    onPreferencesChange: (CalendarPreferences) -> Unit,
    onAddSection: (name: String, colorHex: String) -> Unit,
    onRenameSection: (from: String, to: String) -> Unit,
    onSectionColor: (name: String, colorHex: String) -> Unit,
    onRemoveSection: (name: String) -> Unit,
    onInsertSection: (SectionStyle) -> Unit,
    onRemoveTemplate: (id: String) -> Unit,
    onRemovePreset: (id: String) -> Unit,
    onChooseLogo: () -> Unit = {},
    onDismiss: () -> Unit,
    cloudSync: CalendarCloudSync? = null,
) {
    var tab by remember { mutableStateOf(initialTab) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        SheetScaffold(
            title = stringResource(Res.string.calendar_settings),
            subtitle = stringResource(Res.string.calendar_settings_sub),
            icon = Icons.Filled.CalendarMonth,
            width = DIALOG_WIDTH,
            onDismiss = onDismiss,
            tabs = {
                // One segmented control, the same sunken track every other choice in the app sits on.
                SegmentTrack(modifier = Modifier.weight(1f).height(SheetMetrics.tabHeight)) {
                    SettingsTab.entries.forEach { entry ->
                        SegmentTrackItem(
                            selected = entry == tab,
                            onClick = { tab = entry },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        ) {
                            Text(
                                text = tabLabel(entry),
                                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                                fontWeight = if (entry == tab) FontWeight.Bold else FontWeight.Medium,
                                color = LocalContentColor.current,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            },
            footer = {
                Spacer(Modifier.weight(1f))
                PrimaryButton(stringResource(Res.string.calendar_settings_done), onDismiss)
            },
            // Done in the footer closes it; a second close in the header was one too many.
            showClose = false,
        ) {
            ScrollableColumn(
                modifier = Modifier.heightIn(min = BODY_HEIGHT, max = BODY_HEIGHT),
                verticalArrangement = Arrangement.spacedBy(9.dp),
                contentPadding = PaddingValues(horizontal = 15.dp, vertical = 13.dp),
            ) {
                when (tab) {
                    SettingsTab.SECTIONS -> SectionsTab(
                        sections = preferences.sections,
                        canInsert = canInsertSection,
                        colorPicker = colorPicker,
                        onAdd = onAddSection,
                        onRename = onRenameSection,
                        onColor = onSectionColor,
                        onRemove = onRemoveSection,
                        onInsert = onInsertSection,
                    )

                    SettingsTab.TEMPLATES -> TemplatesTab(templates, onRemoveTemplate)
                    SettingsTab.PRESETS -> PresetsTab(presets, onRemovePreset)
                    SettingsTab.DEFAULTS -> DefaultsTab(preferences, onPreferencesChange, cloudSync)
                    SettingsTab.EXPORT -> ExportTab(
                        settings = preferences.pdfExport,
                        sections = preferences.sections,
                        onChange = { onPreferencesChange(preferences.copy(pdfExport = it)) },
                        onChooseLogo = onChooseLogo,
                    )
                }
            }
        }
    }
}

@Composable
private fun tabLabel(tab: SettingsTab): String = stringResource(
    when (tab) {
        SettingsTab.SECTIONS -> Res.string.calendar_settings_sections
        SettingsTab.TEMPLATES -> Res.string.calendar_settings_templates
        SettingsTab.PRESETS -> Res.string.calendar_settings_presets
        SettingsTab.DEFAULTS -> Res.string.calendar_settings_defaults
        SettingsTab.EXPORT -> Res.string.calendar_settings_export
    }
)

private const val WHEN_TINT = 0.16f

/** The saved templates, each with the one thing to do to it here — delete. */
@Composable
private fun TemplatesTab(templates: List<SavedTemplate>, onRemove: (String) -> Unit) {
    if (templates.isEmpty()) {
        NoteLine(stringResource(Res.string.calendar_templates_empty_sub))
        return
    }
    NoteLine(stringResource(Res.string.calendar_templates_note))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        templates.forEach { template ->
            SettingCard {
                CardText(
                    title = template.name,
                    subtitle = stringResource(
                        Res.string.calendar_template_saved_sub,
                        clockText(template.startTime, LocalUse24HourClock.current),
                        template.contentItems().size,
                    ),
                )
                SmallIconButton(
                    icon = Icons.Filled.Close,
                    description = stringResource(Res.string.calendar_template_remove),
                    onClick = { onRemove(template.id) },
                    destructive = true,
                )
            }
        }
    }
}

/** The items saved from the app's tabs, each with the one thing to do to it here — delete. */
@Composable
private fun PresetsTab(presets: List<ItemPreset>, onRemove: (String) -> Unit) {
    if (presets.isEmpty()) {
        NoteLine(stringResource(Res.string.calendar_presets_empty_sub))
        return
    }
    NoteLine(stringResource(Res.string.calendar_presets_note))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        presets.forEach { preset ->
            val look = lookFor(preset.item)
            SettingCard {
                Box(
                    Modifier
                        .size(CalendarMetrics.rowIcon)
                        .clip(RoundedCornerShape(6.dp))
                        .background(look.color.copy(alpha = WHEN_TINT)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(look.icon, contentDescription = null, tint = look.color, modifier = Modifier.size(12.dp))
                }
                CardText(title = preset.name, subtitle = preset.item.displayText)
                SmallIconButton(
                    icon = Icons.Filled.Close,
                    description = stringResource(Res.string.calendar_preset_remove),
                    onClick = { onRemove(preset.id) },
                    destructive = true,
                )
            }
        }
    }
}

@Composable
private fun SectionsTab(
    sections: List<SectionStyle>,
    canInsert: Boolean,
    colorPicker: (@Composable (ColorPickerRequest) -> Unit)?,
    onAdd: (String, String) -> Unit,
    onRename: (String, String) -> Unit,
    onColor: (String, String) -> Unit,
    onRemove: (String) -> Unit,
    onInsert: (SectionStyle) -> Unit,
) {
    var paletteFor by remember { mutableStateOf<String?>(null) }
    var pickingFor by remember { mutableStateOf<SectionStyle?>(null) }

    NoteLine(stringResource(Res.string.calendar_sections_note))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        sections.forEach { section ->
            SectionRow(
                section = section,
                canInsert = canInsert,
                paletteOpen = paletteFor == section.name,
                // With a picker available the swatch and the hex both open it; without one they
                // fall back to the eight-swatch row, which is all this module can offer alone.
                onPickColor = if (colorPicker != null) {
                    { pickingFor = section }
                } else {
                    { paletteFor = if (paletteFor == section.name) null else section.name }
                },
                onRename = { onRename(section.name, it) },
                onColor = { onColor(section.name, it); paletteFor = null },
                onRemove = { onRemove(section.name) },
                onInsert = { onInsert(section) },
            )
        }
    }
    AddSectionRow(existing = sections, onAdd = onAdd)

    val picking = pickingFor
    if (picking != null && colorPicker != null) {
        colorPicker(
            ColorPickerRequest(
                initialHex = picking.colorHex,
                onPicked = { hex -> onColor(picking.name, hex); pickingFor = null },
                onDismiss = { pickingFor = null },
            )
        )
    }
}

/**
 * One section: its swatch, its name, its hex, and what can be done with it.
 *
 * The name and hex are editable in place, as the design has them — the name field is transparent
 * and borderless so the row reads as a list entry rather than as a form.
 */
@Composable
private fun SectionRow(
    section: SectionStyle,
    canInsert: Boolean,
    paletteOpen: Boolean,
    onPickColor: () -> Unit,
    onRename: (String) -> Unit,
    onColor: (String) -> Unit,
    onRemove: () -> Unit,
    onInsert: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var name by remember(section.name) { mutableStateOf(section.name) }

    Column {
        SettingCard(horizontalPadding = 10.dp, verticalPadding = 8.dp) {
            Box(
                Modifier
                    .size(SWATCH_BUTTON)
                    .clip(RoundedCornerShape(7.dp))
                    .background(parseHex(section.colorHex))
                    .border(1.dp, scheme.outlineVariant, RoundedCornerShape(7.dp))
                    .clickable(onClick = onPickColor)
            )
            // Transparent and unbordered: the design edits the name in place, not in a box.
            Box(Modifier.weight(1f)) {
                androidx.compose.foundation.text.BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onSurface,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(scheme.onSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .commitOnExit(name != section.name) { onRename(name) },
                )
            }
            // The hex opens the picker rather than being typed into. It is a *readout* of the
            // color: the swatch beside it and this both answer "what color is this section", and
            // having one of them be a free-text field that silently rejects `#GGHHII` is worse than
            // having both open the one control that cannot produce an invalid value.
            HexReadout(hex = section.colorHex, onClick = onPickColor)
            if (canInsert) {
                QuietButton(
                    label = stringResource(Res.string.calendar_section_insert),
                    onClick = onInsert,
                    height = SheetMetrics.rowButton,
                    accent = true,
                )
            }
            SmallIconButton(
                icon = Icons.Filled.Close,
                description = stringResource(Res.string.calendar_section_remove),
                onClick = onRemove,
                destructive = true,
                size = SheetMetrics.rowButton,
            )
        }
        if (paletteOpen) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(start = 8.dp, top = 5.dp),
            ) {
                SECTION_SWATCHES.forEach { swatch ->
                    val on = swatch.equals(section.colorHex, ignoreCase = true)
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(parseHex(swatch))
                            .border(
                                width = 2.dp,
                                color = if (on) scheme.onSurface else scheme.outlineVariant,
                                shape = RoundedCornerShape(6.dp),
                            )
                            .clickable { onColor(swatch) }
                    )
                }
            }
        }
    }
}

/** The section's color in words, as a control that opens the picker. */
@Composable
private fun HexReadout(hex: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(7.dp)
    Box(
        Modifier
            .width(HEX_FIELD)
            .height(SWATCH_BUTTON)
            .sunken(shape, elevationPalette())
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = hex.uppercase(),
            style = MaterialTheme.typography.bodySmall.copy(
                fontSize = 11.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
            ),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddSectionRow(existing: List<SectionStyle>, onAdd: (String, String) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    fun commit() {
        if (draft.isNotBlank()) {
            // The next unused swatch, so two sections added in a row do not come out the same color.
            val used = existing.map { it.colorHex.uppercase() }.toSet()
            onAdd(draft, SECTION_SWATCHES.firstOrNull { it !in used } ?: SECTION_SWATCHES.first())
        }
        draft = ""
        adding = false
    }

    if (!adding) {
        DashedAddButton(
            label = stringResource(Res.string.calendar_section_add),
            icon = Icons.Filled.Add,
            onClick = { adding = true },
        )
        return
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CompactTextField(
            value = draft,
            onValueChange = { draft = it },
            placeholder = stringResource(Res.string.calendar_section_add),
            focused = true,
            modifier = Modifier.weight(1f).commitOnExit(true) { commit() },
        )
        QuietButton(label = stringResource(Res.string.calendar_settings_done), onClick = ::commit, accent = true)
    }
}

@Composable
private fun NoteLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
