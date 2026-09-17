package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarSource
import org.churchpresenter.calendar.CalendarState
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_all_saved
import org.churchpresenter.calendar.generated.resources.calendar_cancel
import org.churchpresenter.calendar.generated.resources.calendar_close
import org.churchpresenter.calendar.generated.resources.calendar_dismiss
import org.churchpresenter.calendar.generated.resources.calendar_header_sub_one
import org.churchpresenter.calendar.generated.resources.calendar_header_sub_other
import org.churchpresenter.calendar.generated.resources.calendar_load_append
import org.churchpresenter.calendar.generated.resources.calendar_load_body
import org.churchpresenter.calendar.generated.resources.calendar_load_into_schedule
import org.churchpresenter.calendar.generated.resources.calendar_load_replace
import org.churchpresenter.calendar.generated.resources.calendar_load_title
import org.churchpresenter.calendar.generated.resources.calendar_lost_body
import org.churchpresenter.calendar.generated.resources.calendar_lost_title
import org.churchpresenter.calendar.generated.resources.calendar_recovered_body
import org.churchpresenter.calendar.generated.resources.calendar_recovered_title
import org.churchpresenter.calendar.generated.resources.calendar_template_blank
import org.churchpresenter.calendar.generated.resources.calendar_template_blank_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_copy_sub
import org.churchpresenter.calendar.generated.resources.calendar_template_saved_sub
import org.churchpresenter.calendar.generated.resources.calendar_title
import org.churchpresenter.calendar.generated.resources.calendar_today
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Settings
import org.churchpresenter.calendar.generated.resources.calendar_export_pdf
import org.churchpresenter.calendar.generated.resources.calendar_settings_open
import org.churchpresenter.calendar.model.exportRunOfShowPdf
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.model.sectionItem
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.monthHeading
import org.churchpresenter.calendar.model.parseStoredDate
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.time.LocalDate

/**
 * The Calendar Manager.
 *
 * Left is the month; right is the selected day, its services and the run of show of whichever is
 * open. The automation column the design carries is not here — cues are a later phase, and the
 * model already has the field they will fill ([PlannedService.cues]).
 *
 * [storeFolder] is where `calendar.json` goes, and [songFolder] is the song library the picker
 * reads. Both are passed in rather than resolved here, which is what lets this window be driven in
 * a test against a temp directory and keeps `:calendar` free of a dependency on `:settings`.
 */
@Composable
fun CalendarApp(
    storeFolder: File,
    songFolder: File?,
    host: CalendarHost = CalendarHost(),
    /**
     * The color picker a section's swatch opens, supplied by whoever hosts this window.
     *
     * Inside ChurchPresenter that is the app's own `ColorPickerDialog`, the same one every other
     * color in the app is chosen with. Absent, the settings dialog falls back to its swatch row.
     */
    colorPicker: (@Composable (ColorPickerRequest) -> Unit)? = null,
    /**
     * The song editor a result row's pencil opens, supplied by whoever hosts this window.
     *
     * Inside ChurchPresenter that is the app's own Edit Song dialog — the same one the Songs tab
     * and the Song Library Manager open — so a song is edited in one place wherever it is reached
     * from. Absent, the pencil is not drawn.
     */
    songEditor: (@Composable (SongEditRequest) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    today: LocalDate = LocalDate.now(),
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    val state = remember(storeFolder, songFolder) {
        CalendarState(CalendarStore(storeFolder), songFolder, today)
    }
    LaunchedEffect(storeFolder) { state.loadAsync(io) }
    LaunchedEffect(songFolder) { state.loadSongsAsync(io) }


    var editingService by remember { mutableStateOf<PlannedService?>(null) }
    var creatingService by remember { mutableStateOf(false) }
    var addingItem by remember { mutableStateOf(false) }
    // The run-of-show row the picker is about to replace, or null when it is appending.
    var replacing by remember { mutableStateOf<ScheduleItem?>(null) }
    var loadConfirmFor by remember { mutableStateOf<PlannedService?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    // The service Copy or Template was pressed on, or null while that sheet is closed.
    var copyFrom by remember { mutableStateOf<PlannedService?>(null) }
    var templateFrom by remember { mutableStateOf<PlannedService?>(null) }

    // Fetched when the picker is first opened, not up front and not per recomposition. The host's
    // CalendarHost is rebuilt by the app on every recomposition, so keying an effect on it would
    // re-walk every chapter of every book each time; and at first composition the Bible may not be
    // loaded yet, so doing it eagerly can produce an empty list that never refills.
    LaunchedEffect(addingItem) {
        if (addingItem && state.bibleBooks.isEmpty()) state.loadBibleBooks(host.bibleBooks())
    }
    val scope = rememberCoroutineScope()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize()) {
            Header(
                monthLabel = monthHeading(state.visibleMonth),
                plannedThisMonth = state.servicesInVisibleMonth().size,
                onToday = state::goToToday,
                onExport = exportAction(state, host, io, scope),
                onSettings = { settingsOpen = true },
                onClose = onClose,
            )
            HorizontalDivider()
            RecoveryBanner(source = state.source, onDismiss = state::acknowledgeSource)

            Row(Modifier.fillMaxSize().weight(1f)) {
                MonthPane(
                    month = state.visibleMonth,
                    selected = state.selectedDate,
                    today = today,
                    servicesOn = state::servicesOn,
                    onSelect = state::select,
                    onPreviousMonth = state::showPreviousMonth,
                    onNextMonth = state::showNextMonth,
                    modifier = Modifier
                        .widthIn(min = CalendarMetrics.monthPaneMin, max = CalendarMetrics.monthPaneMax)
                        .fillMaxHeight(),
                )
                VerticalDivider()
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    DayPane(
                        date = state.selectedDate,
                        services = state.servicesOnSelectedDate,
                        selectedServiceId = state.selectedService?.id,
                        onSelectService = state::selectService,
                        onAddService = { creatingService = true },
                        onEditService = { editingService = it },
                    )
                    HorizontalDivider()
                    val service = state.selectedService
                    if (service == null) {
                        NoServicesPane(
                            dayLabel = shortDate(state.selectedDate),
                            copyLabel = state.mostRecentServiceBefore()?.name,
                            onAddService = { creatingService = true },
                            onCopyLast = { creatingService = true },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        RunOfShowPane(
                            service = service,
                            onAddItem = { replacing = null; addingItem = true },
                            onChangeItem = { replacing = it; addingItem = true },
                            onRemove = { state.removeItem(service.id, it) },
                            onMove = { from, to -> state.moveItem(service.id, from, to) },
                            onPlannedSecondsChange = { itemId, seconds ->
                                state.setPlannedSeconds(service.id, itemId, seconds)
                            },
                            onCopy = { copyFrom = service },
                            onSaveTemplate = { templateFrom = service },
                            modifier = Modifier.weight(1f),
                        )
                        HorizontalDivider()
                        Footer(
                            onLoad = {
                                // Only ask when replacing would actually discard something.
                                if (host.currentSchedule().isEmpty()) {
                                    host.loadIntoSchedule(service.items, true)
                                } else {
                                    loadConfirmFor = service
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (settingsOpen) {
        val openService = state.selectedService
        CalendarSettingsDialog(
            preferences = state.document.preferences,
            templates = state.document.templates,
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
            onRemoveTemplate = state::deleteTemplate,
            onDismiss = { settingsOpen = false },
        )
    }

    CalendarDialogs(
        state = state,
        host = host,
        songEditor = songEditor,
        replacing = replacing,
        creatingService = creatingService,
        editingService = editingService,
        addingItem = addingItem,
        loadConfirmFor = loadConfirmFor,
        copyFrom = copyFrom,
        templateFrom = templateFrom,
        onServiceSheetClosed = { creatingService = false; editingService = null },
        onAddingItemClosed = { addingItem = false; replacing = null },
        onLoadConfirmClosed = { loadConfirmFor = null },
        onCopySheetClosed = { copyFrom = null },
        onTemplateSheetClosed = { templateFrom = null },
    )
}

/**
 * The window's dialogs, lifted out of [CalendarApp].
 *
 * Only so that [CalendarApp] stays under the `LongMethod` threshold and reads as the layout it is;
 * every one of these is driven entirely by the flags passed in, and none of them holds state.
 */
@Composable
private fun CalendarDialogs(
    state: CalendarState,
    host: CalendarHost,
    songEditor: (@Composable (SongEditRequest) -> Unit)?,
    replacing: ScheduleItem?,
    creatingService: Boolean,
    editingService: PlannedService?,
    addingItem: Boolean,
    loadConfirmFor: PlannedService?,
    copyFrom: PlannedService?,
    templateFrom: PlannedService?,
    onServiceSheetClosed: () -> Unit,
    onAddingItemClosed: () -> Unit,
    onLoadConfirmClosed: () -> Unit,
    onCopySheetClosed: () -> Unit,
    onTemplateSheetClosed: () -> Unit,
) {
    if (creatingService || editingService != null) {
        val existing = editingService
        ServiceSheet(
            existing = existing,
            defaultStartTime = state.document.preferences.defaultStartTime,
            date = state.selectedDate,
            seriesSize = existing?.let { state.document.servicesInSeries(it.seriesId).size } ?: 0,
            templates = state.templateOptions(),
            templateLabel = { templateLabel(it) },
            onSave = { form ->
                if (existing == null) {
                    state.addService(form.name, form.startTime, form.kind, form.template)
                } else {
                    state.updateService(
                        existing.copy(name = form.name, startTime = form.startTime, kind = form.kind.id),
                        wholeSeries = form.wholeSeries,
                    )
                }
                onServiceSheetClosed()
            },
            onDelete = existing?.let {
                { wholeSeries ->
                    state.deleteService(it.id, wholeSeries)
                    onServiceSheetClosed()
                }
            },
            onDismiss = onServiceSheetClosed,
        )
    }

    val addTarget = state.selectedService
    if (addingItem && addTarget != null) {
        AddItemSheet(
            songs = state.songs,
            songsLoaded = state.songsLoaded,
            currentSchedule = host.currentSchedule(),
            sections = state.document.preferences.sections,
            bibleBooks = state.bibleBooks,
            serviceName = addTarget.name,
            replacing = replacing,
            songbooks = state.songbooks(),
            songEditor = songEditor,
            onSaveSong = { original, edited -> state.saveSong(original, edited) },
            onAdd = { items, plannedSeconds ->
                if (replacing != null) {
                    state.replaceItem(addTarget.id, replacing.id, items)
                } else {
                    state.addItems(addTarget.id, items)
                }
                // The duration typed in the picker's footer applies to what was just added.
                if (plannedSeconds != null) {
                    items.forEach { state.setPlannedSeconds(addTarget.id, it.id, plannedSeconds) }
                }
                onAddingItemClosed()
            },
            onDismiss = onAddingItemClosed,
        )
    }

    copyFrom?.let { service ->
        CopySheet(
            service = service,
            date = state.selectedDate,
            hasServices = state::hasServices,
            onCopy = { dates, includeRunOfShow, repeat ->
                state.copyService(service, dates, includeRunOfShow, repeat)
                // A single paste is a jump to where it landed; a series is visible as the dots.
                if (repeat == ServiceRepeat.NONE) dates.firstOrNull()?.let(state::select)
                onCopySheetClosed()
            },
            onDismiss = onCopySheetClosed,
        )
    }

    templateFrom?.let { service ->
        TemplateSheet(
            service = service,
            date = state.selectedDate,
            existing = state.document.templates,
            onSave = { name, sections, items ->
                state.saveTemplate(service, name, sections, items)
                onTemplateSheetClosed()
            },
            onDismiss = onTemplateSheetClosed,
        )
    }

    loadConfirmFor?.let { service ->
        LoadServiceConfirm(
            currentCount = host.currentSchedule().size,
            onReplace = { host.loadIntoSchedule(service.items, true); onLoadConfirmClosed() },
            onAppend = { host.loadIntoSchedule(service.items, false); onLoadConfirmClosed() },
            onDismiss = onLoadConfirmClosed,
        )
    }
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
}

/**
 * Writes [service]'s run of show to wherever the host's file chooser points.
 *
 * Nothing happens when the chooser is cancelled, which is the common case for a misclick. A failure
 * to write is swallowed here rather than crashing the window — the export is a convenience, and the
 * plan it was made from is still on screen.
 */
/**
 * The header's Export action, or null when no service is open.
 *
 * Built here rather than inline because a `let` whose last expression is a lambda reads as a
 * trailing-lambda call to the compiler, not as the value it returns.
 */
@Composable
private fun exportAction(
    state: CalendarState,
    host: CalendarHost,
    io: CoroutineDispatcher,
    scope: CoroutineScope,
): (() -> Unit)? {
    val service = state.selectedService ?: return null
    val label = shortDate(state.selectedDate)
    return { scope.launch { exportRunOfShow(service, label, host, io) } }
}

private suspend fun exportRunOfShow(
    service: PlannedService,
    dateLabel: String,
    host: CalendarHost,
    io: CoroutineDispatcher,
) {
    val target = host.chooseExportFile("${service.name} - ${service.date}.pdf") ?: return
    // Off the composing thread: this embeds a font and writes a file.
    withContext(io) { runCatching { exportRunOfShowPdf(service, target, dateLabel, host.pdfFont) } }
}

/**
 * The window's header.
 *
 * Ordered as the design has it: the badge, the title and its subtitle, a rule, then **Today** —
 * all on the left, directly over the month pane it acts on — and only then the spacer that pushes
 * the saved note, Export, Settings and Close to the right. Today sitting out on the right, next to
 * Close, reads as a window-level action rather than as calendar navigation, which is what it is.
 */
@Composable
private fun Header(
    monthLabel: String,
    plannedThisMonth: Int,
    onToday: () -> Unit,
    onExport: (() -> Unit)?,
    onSettings: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(HEADER_HEIGHT)
            .background(scheme.surface)
            .padding(horizontal = 14.dp),
    ) {
        Box(
            Modifier
                .size(HEADER_BADGE)
                .clip(RoundedCornerShape(9.dp))
                .background(scheme.primary.copy(alpha = BADGE_TINT)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(
                text = stringResource(Res.string.calendar_title),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 14.5.sp),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = if (plannedThisMonth == 1) {
                    stringResource(Res.string.calendar_header_sub_one, plannedThisMonth, monthLabel)
                } else {
                    stringResource(Res.string.calendar_header_sub_other, plannedThisMonth, monthLabel)
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.width(1.dp).height(24.dp).background(scheme.outlineVariant))
        HeaderButton(label = stringResource(Res.string.calendar_today), onClick = onToday)

        Spacer(Modifier.weight(1f))

        // Every change is written as it is made — see CalendarState.commit — so this is a statement
        // of fact rather than a save button.
        Text(
            text = stringResource(Res.string.calendar_all_saved),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
            color = scheme.onSurfaceVariant.copy(alpha = 0.7f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (onExport != null) {
            HeaderButton(
                label = stringResource(Res.string.calendar_export_pdf),
                icon = Icons.Filled.PictureAsPdf,
                onClick = onExport,
            )
        }
        HeaderButton(
            label = stringResource(Res.string.calendar_settings_open),
            icon = Icons.Filled.Settings,
            onClick = onSettings,
        )
        if (onClose != null) {
            PrimaryButton(stringResource(Res.string.calendar_close), onClose)
        }
    }
}

/** One of the header's quiet bordered controls, at the design's 28dp. */
@Composable
private fun HeaderButton(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .height(HEADER_BUTTON)
            .clip(CalendarMetrics.buttonRadius)
            .background(scheme.surfaceVariant.copy(alpha = 0.5f))
            .border(1.dp, scheme.outlineVariant, CalendarMetrics.buttonRadius)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(13.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun Footer(onLoad: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(CalendarMetrics.addServiceButtonHeight)
                .clip(CalendarMetrics.buttonRadius)
                .background(scheme.primary)
                .clickable(onClick = onLoad)
                .padding(horizontal = 13.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                tint = scheme.onPrimary,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(Res.string.calendar_load_into_schedule),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.Bold,
                color = scheme.onPrimary,
            )
        }
    }
}

/**
 * Says so when the calendar did not come from `calendar.json`.
 *
 * The whole reason the store keeps backups is that this case is survivable; the whole reason this
 * banner exists is that surviving it silently is not good enough — a planner that quietly opens a
 * week-old copy is indistinguishable from one that opened the current file.
 */
@Composable
private fun RecoveryBanner(source: CalendarSource, onDismiss: () -> Unit) {
    if (source != CalendarSource.RECOVERED && source != CalendarSource.LOST) return
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(scheme.errorContainer)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(
                    if (source == CalendarSource.RECOVERED) {
                        Res.string.calendar_recovered_title
                    } else {
                        Res.string.calendar_lost_title
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onErrorContainer,
            )
            Text(
                text = stringResource(
                    if (source == CalendarSource.RECOVERED) {
                        Res.string.calendar_recovered_body
                    } else {
                        Res.string.calendar_lost_body
                    }
                ),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onErrorContainer,
            )
        }
        TextButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_dismiss)) }
    }
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
            TextButton(onClick = onReplace) { Text(stringResource(Res.string.calendar_load_replace)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAppend) { Text(stringResource(Res.string.calendar_load_append)) }
                TextButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_cancel)) }
            }
        },
    )
}

private val HEADER_HEIGHT = 52.dp
private val HEADER_BADGE = 30.dp
private val HEADER_BUTTON = 28.dp
private const val BADGE_TINT = 0.16f
