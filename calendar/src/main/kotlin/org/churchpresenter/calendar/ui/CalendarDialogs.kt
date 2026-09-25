package org.churchpresenter.calendar.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarUsage
import org.churchpresenter.calendar.CalendarState
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_load_append
import org.churchpresenter.calendar.generated.resources.calendar_load_body
import org.churchpresenter.calendar.generated.resources.calendar_load_replace
import org.churchpresenter.calendar.generated.resources.calendar_load_title
import org.churchpresenter.calendar.generated.resources.calendar_template_blank
import org.churchpresenter.calendar.generated.resources.calendar_template_blank_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_copy_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_saved_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_schedule
import org.churchpresenter.calendar.generated.resources.calendar_template_schedule_sub
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.storedDate
import java.time.LocalDate
import org.churchpresenter.calendar.model.parseStoredDate
import org.churchpresenter.calendar.model.rowsForSchedule
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.timerSeconds
import org.churchpresenter.calendar.model.timingForSchedule
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.model.withBook
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.jetbrains.compose.resources.stringResource

/**
 * The window's dialogs, lifted out of [CalendarApp].
 *
 * Every one of them is driven entirely by [dialogs]; none holds state of its own.
 */
@Composable
internal fun CalendarDialogs(
    state: CalendarState,
    host: CalendarHost,
    dialogs: CalendarDialogState,
    colorPicker: (@Composable (ColorPickerRequest) -> Unit)?,
    songEditor: (@Composable (SongEditRequest) -> Unit)?,
    /** Called once a run of show has been put into the Schedule -- the window closes behind it. */
    onLoaded: () -> Unit,
    /** The window's today, which the date picker marks -- the same day its month grid marks. */
    today: LocalDate = LocalDate.now(),
) {
    val openService = state.selectedService
    val scope = rememberCoroutineScope()
    if (dialogs.settingsOpen) {
        CalendarSettingsDialog(
            preferences = state.document.preferences,
            templates = state.document.templates,
            presets = state.presets,
            initialTab = dialogs.settingsTab,
            canInsertSection = openService != null,
            onPreferencesChange = state::updatePreferences,
            onAddSection = state::addSection,
            onRenameSection = state::renameSection,
            onSectionColor = state::setSectionColor,
            onRemoveSection = state::removeSection,
            colorPicker = colorPicker,
            onInsertSection = { section ->
                openService?.let { service ->
                    state.addItems(service.id, listOf(sectionItem(section.name, section.colorHex)))
                }
            },
            cloudSync = host.cloudSync,
            onRemoveTemplate = state::deleteTemplate,
            onRemovePreset = state::deletePreset,
            onChooseLogo = {
                scope.launch {
                    val logo = host.chooseImageFile() ?: return@launch
                    val preferences = state.document.preferences
                    state.updatePreferences(
                        preferences.copy(pdfExport = preferences.pdfExport.copy(logoPath = logo.absolutePath)),
                    )
                }
            },
            onDismiss = { dialogs.settingsOpen = false },
        )
    }

    if (dialogs.creatingService || dialogs.editingService != null) {
        // What is in the app's Schedule tab can start a new service, when there is anything there.
        val fromSchedule = host.currentSchedule().takeIf { it.isNotEmpty() }?.let { ServiceTemplate.FromSchedule(it) }
        ServiceDialog(
            state,
            dialogs.editingService,
            fromSchedule = fromSchedule,
            startFromSchedule = dialogs.startFromSchedule,
            today = today,
            onServiceAdded = { host.recordUsage(CalendarUsage.SERVICE_ADDED) },
            onClose = dialogs::closeServiceSheet,
        )
    }

    if (dialogs.addingItem && openService != null) {
        AddItemDialog(state, host, openService, dialogs.replacing, songEditor, onClose = dialogs::closePicker)
    }

    dialogs.copyFrom?.let { service ->
        CopySheet(
            service = service,
            date = state.selectedDate,
            hasServices = state::hasServices,
            onCopy = { dates, includeRunOfShow, includeCues, repeat ->
                state.copyService(service, dates, includeRunOfShow, includeCues, repeat)
                host.recordUsage(CalendarUsage.SERVICE_COPIED)
                // A single paste is a jump to where it landed; a series is visible as the dots.
                if (repeat == ServiceRepeat.NONE) dates.firstOrNull()?.let(state::select)
                dialogs.copyFrom = null
            },
            onDismiss = { dialogs.copyFrom = null },
        )
    }

    dialogs.templateFrom?.let { service ->
        TemplateSheet(
            service = service,
            date = state.selectedDate,
            existing = state.document.templates,
            onSave = { name, sections, items, cues ->
                state.saveTemplate(service, name, sections, items, cues)
                host.recordUsage(CalendarUsage.TEMPLATE_SAVED)
                dialogs.templateFrom = null
            },
            onDismiss = { dialogs.templateFrom = null },
        )
    }

    dialogs.loadConfirmFor?.let { service ->
        LoadServiceConfirm(
            currentCount = host.currentSchedule().size,
            onReplace = {
                host.loadIntoSchedule(
                    service.rowsForSchedule(), service.timingForSchedule(), true, service.armed, service.startTime,
                )
                dialogs.loadConfirmFor = null
                onLoaded()
            },
            onAppend = {
                host.loadIntoSchedule(
                    service.rowsForSchedule(), service.timingForSchedule(), false, service.armed, service.startTime,
                )
                dialogs.loadConfirmFor = null
                onLoaded()
            },
            onDismiss = { dialogs.loadConfirmFor = null },
        )
    }
}

/** The service sheet: a new service on the selected day, or [existing] being edited. */
@Composable
private fun ServiceDialog(
    state: CalendarState,
    existing: PlannedService?,
    fromSchedule: ServiceTemplate.FromSchedule?,
    startFromSchedule: Boolean,
    today: LocalDate,
    onServiceAdded: () -> Unit,
    onClose: () -> Unit,
) {
    val templates = state.templateOptions()
    ServiceSheet(
        existing = existing,
        defaultStartTime = state.document.preferences.defaultStartTime,
        date = state.selectedDate,
        seriesSize = existing?.let { state.document.servicesInSeries(it.seriesId).size } ?: 0,
        // Second, after Blank: the Schedule is what is in front of the operator right now.
        templates = templates.take(1) + listOfNotNull(fromSchedule) + templates.drop(1),
        templateLabel = { templateLabel(it) },
        servicesOn = state::servicesOn,
        initialTemplate = fromSchedule?.takeIf { startFromSchedule },
        today = today,
        onSave = { form ->
            if (existing == null) {
                state.addService(form.name, form.startTime, form.kind, form.template, form.date)
                onServiceAdded()
            } else {
                state.updateService(
                    existing.copy(
                        name = form.name,
                        startTime = form.startTime,
                        kind = form.kind.id,
                        date = storedDate(form.date),
                    ),
                    wholeSeries = form.wholeSeries,
                )
            }
            onClose()
        },
        onDelete = existing?.let {
            { wholeSeries ->
                state.deleteService(it.id, wholeSeries)
                onClose()
            }
        },
        onDismiss = onClose,
    )
}

/** The item picker over [target]'s run of show, appending -- or replacing [replacing]. */
@Composable
private fun AddItemDialog(
    state: CalendarState,
    host: CalendarHost,
    target: PlannedService,
    replacing: ScheduleItem?,
    songEditor: (@Composable (SongEditRequest) -> Unit)?,
    onClose: () -> Unit,
) {
    val dialogScope = rememberCoroutineScope()
    AddItemSheet(
        songs = state.songs,
        songsLoaded = state.songsLoaded,
        presets = state.presets,
        sections = state.document.preferences.sections,
        bibleBooks = state.bibleBooks,
        serviceName = target.name,
        serviceStartTime = target.startTime,
        replacing = replacing,
        songbooks = state.songbooks(),
        songEditor = songEditor,
        onSaveSong = { original, edited -> state.saveSong(original, edited) },
        timing = replacing?.let { target.timingOf(it.id) } ?: RowTiming.DEFAULT,
        plannedSeconds = replacing?.let { target.plannedSeconds[it.id] },
        measuredSeconds = replacing?.let { state.measuredSeconds[it.id] },
        previewSources = host.preview,
        onTimingChange = { timing, seconds ->
            replacing?.let { row ->
                state.setTiming(target.id, row.id, timing)
                state.setPlannedSeconds(target.id, row.id, seconds)
            }
        },
        onAdd = { picked, plannedSeconds, timing -> dialogScope.launch {
            // A typed reference knows only the name it was typed as; settle its book now, the
            // way go-live would, so the row carries the id and the Bible's own spelling.
            val items = picked.map { it.withBookResolved(state.bibleBooks, host.resolveBookId) }
            if (replacing != null) {
                state.replaceItem(target.id, replacing.id, items)
            } else {
                state.addItems(target.id, items)
            }
            // The panel's length and timing apply to what was just added; a countdown's own
            // length is its planned length whether or not one was typed.
            items.forEach { item ->
                (plannedSeconds ?: item.timerSeconds())?.let { state.setPlannedSeconds(target.id, item.id, it) }
                // A song has usually been sung here before, a clip knows how long it is, and a
                // picture folder is its count times the slideshow interval; nobody should have
                // to type any of them in. Failing all of those, the preferences' default for
                // its kind. Only for a row that arrived without a length, and off the composing
                // thread.
                if (plannedSeconds == null && item.timerSeconds() == null) {
                    val fallback = state.document.preferences.defaultLengthFor(item)
                    dialogScope.launch {
                        (host.itemRunSeconds(item) ?: fallback)?.let { state.setPlannedSeconds(target.id, item.id, it) }
                    }
                }
                if (!timing.isDefault() && item !is ScheduleItem.LabelItem) {
                    state.setTiming(target.id, item.id, timing)
                }
            }
            onClose()
        } },
        onDismiss = onClose,
    )
}

/** A `Start from` option's two lines. */
@Composable
private fun templateLabel(option: ServiceTemplate): Pair<String, String> = when (option) {
    ServiceTemplate.Blank -> stringResource(Res.string.calendar_template_blank) to
        stringResource(Res.string.calendar_template_blank_sub)

    is ServiceTemplate.CopyOf -> {
        val date = parseStoredDate(option.service.date)?.let(::shortDate).orEmpty()
        option.service.name to stringResource(
            Res.string.calendar_template_copy_sub,
            date,
            option.service.contentItems().size,
        )
    }

    is ServiceTemplate.Saved -> option.template.name to stringResource(
        Res.string.calendar_template_saved_sub,
        option.template.startTime,
        option.template.contentItems().size,
    )

    is ServiceTemplate.FromSchedule -> stringResource(Res.string.calendar_template_schedule) to
        stringResource(Res.string.calendar_template_schedule_sub, option.items.size)
}

@Composable
private fun LoadServiceConfirm(
    currentCount: Int,
    onReplace: () -> Unit,
    onAppend: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.calendar_load_title)) },
        text = { Text(stringResource(Res.string.calendar_load_body, currentCount)) },
        confirmButton = {
            GhostButton(onClick = onReplace) { Text(stringResource(Res.string.calendar_load_replace)) }
        },
        dismissButton = {
            Row {
                GhostButton(onClick = onAppend) { Text(stringResource(Res.string.calendar_load_append)) }
                GhostButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_cancel)) }
            }
        },
    )
}

/** [this] with a typed book settled -- see `withBook`; anything else, or an unrecognised name, as it was. */
private suspend fun ScheduleItem.withBookResolved(
    books: List<CalendarBibleBook>,
    resolve: suspend (String) -> Int?,
): ScheduleItem {
    if (this !is ScheduleItem.BibleVerseItem || bookId != 0 || books.isEmpty()) return this
    val book = books.firstOrNull { it.name.equals(bookName.trim(), ignoreCase = true) }
        ?: resolve(bookName)?.let { id -> books.firstOrNull { it.bookId == id } }
        ?: return this
    return withBook(book.bookId, book.name)
}
