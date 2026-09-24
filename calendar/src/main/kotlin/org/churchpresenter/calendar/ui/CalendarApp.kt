package org.churchpresenter.calendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import org.churchpresenter.theme.components.GhostButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.runtime.collectAsState
import org.churchpresenter.calendar.CalendarFileWatcher
import org.churchpresenter.calendar.CalendarHost
import org.churchpresenter.calendar.CalendarUsage
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.calendar.CalendarSource
import org.churchpresenter.calendar.CalendarState
import org.churchpresenter.calendar.CalendarStore
import org.churchpresenter.calendar.CueFeed
import org.churchpresenter.calendar.PresetStore
import org.churchpresenter.calendar.fireCue
import org.churchpresenter.calendar.generated.resources.calendar_all_manual
import org.churchpresenter.calendar.generated.resources.calendar_auto_start_one
import org.churchpresenter.calendar.generated.resources.calendar_auto_starts
import org.churchpresenter.calendar.model.rowsForSchedule
import org.churchpresenter.calendar.model.timingForSchedule
import org.churchpresenter.calendar.generated.resources.Res
import org.churchpresenter.calendar.generated.resources.calendar_all_saved
import org.churchpresenter.calendar.generated.resources.calendar_close
import org.churchpresenter.calendar.generated.resources.calendar_dismiss
import org.churchpresenter.calendar.generated.resources.calendar_header_sub_one
import org.churchpresenter.calendar.generated.resources.calendar_header_sub_other
import org.churchpresenter.calendar.generated.resources.calendar_load_into_schedule
import org.churchpresenter.calendar.generated.resources.calendar_lost_body
import org.churchpresenter.calendar.generated.resources.calendar_lost_title
import org.churchpresenter.calendar.generated.resources.calendar_recovered_body
import org.churchpresenter.calendar.generated.resources.calendar_recovered_title
import org.churchpresenter.calendar.generated.resources.calendar_title
import org.churchpresenter.calendar.generated.resources.calendar_today
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Settings
import org.churchpresenter.calendar.generated.resources.calendar_cloud_invite
import org.churchpresenter.calendar.generated.resources.calendar_settings_open
import org.churchpresenter.calendar.model.exportRunOfShowPdf
import org.churchpresenter.calendar.model.PdfAudience
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.countImages
import org.churchpresenter.calendar.model.relocatedTo
import org.churchpresenter.calendar.model.pointedPath
import org.churchpresenter.calendar.model.fix
import org.churchpresenter.calendar.model.ProblemFix
import org.churchpresenter.calendar.model.monthHeading
import org.jetbrains.compose.resources.stringResource
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import org.churchpresenter.theme.elevationPalette

/**
 * The Calendar Manager.
 *
 * Left is the month; right is the selected day, its services and the run of show of whichever is
 * open — with that service's cues merged into it, which [org.churchpresenter.calendar.CueRunner]
 * fires whether or not this window is open.
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
    /**
     * The wall clock the run of show judges its cues against — see [rememberRunClock].
     *
     * A parameter for the same reason [today] is one: a window that reads the real clock draws a
     * different picture every minute, which a screenshot cannot pin and a test cannot assert on.
     */
    now: () -> LocalTime = { LocalTime.now() },
    /**
     * Whether to watch the store folder for another machine's save — see [CalendarFileWatcher].
     *
     * On in the app, and off in a test: the watch is a loop that never finishes, and a Compose test
     * waits for every effect to be idle before it can look at anything. A test that wants the
     * merging reload calls `CalendarState.reloadMerging` itself, which is what the watch calls.
     */
    watchStoreFolder: Boolean = true,
    io: CoroutineDispatcher = Dispatchers.IO,
) {
    // The watcher is made first so the state can tell it which writes were this window's own.
    val watcher = remember(storeFolder) { CalendarFileWatcher(storeFolder, io) }
    val state = remember(storeFolder, songFolder) {
        CalendarState(
            store = CalendarStore(storeFolder),
            songFolder = songFolder,
            today = today,
            presetStore = PresetStore(storeFolder, onSaved = watcher::savedPresetsHere),
            onSaved = watcher::savedHere,
        )
    }
    LaunchedEffect(storeFolder) { state.loadAsync(io) }
    // A shared folder is how two machines keep one calendar (see CalendarFileWatcher): the other
    // machine's save arrives as a file change, and what it holds is merged into what is open here.
    if (watchStoreFolder) {
        LaunchedEffect(storeFolder) {
            watcher.run(
                onChanged = { state.reloadMerging(io) },
                onPresetsChanged = { state.reloadPresetsMerging(io) },
            )
        }
    }
    LaunchedEffect(songFolder) { state.loadSongsAsync(io) }

    val dialogs = remember { CalendarDialogState() }
    // Fetched when the picker is first opened, not up front and not per recomposition. The host's
    // CalendarHost is rebuilt by the app on every recomposition, so keying an effect on it would
    // re-walk every chapter of every book each time; and at first composition the Bible may not be
    // loaded yet, so doing it eagerly can produce an empty list that never refills.
    LaunchedEffect(dialogs.addingItem) {
        if (dialogs.addingItem && state.bibleBooks.isEmpty()) state.loadBibleBooks(host.bibleBooks())
    }
    // Presets are written by the app's tabs while this window may be open, so re-read them each
    // time something that offers them opens rather than once at load.
    LaunchedEffect(dialogs.addingItem, dialogs.settingsOpen) {
        if (dialogs.addingItem || dialogs.settingsOpen) state.reloadPresets(io)
    }
    val scope = rememberCoroutineScope()

    val openService = state.selectedService
    // Rows that can say how long they run, but were planned before anything asked them -- a clip
    // added by hand, a folder that grew. Filled once per service, and only where nothing is set,
    // so a typed length is never overwritten.
    LaunchedEffect(openService?.id) {
        val service = openService ?: return@LaunchedEffect
        service.items
            .filter { service.plannedSeconds[it.id] == null }
            .forEach { row ->
                host.itemRunSeconds(row)?.let { state.setPlannedSeconds(service.id, row.id, it) }
            }
    }
    // What each row has actually taken here, and which rows will not go on screen on the day.
    // Both re-read as the rows change -- a song swapped in has a history of its own, a file just
    // pointed at may or may not be there -- and the check again once the library has been read,
    // since a song cannot be missing from a library nobody has opened yet.
    LaunchedEffect(openService?.id, openService?.items, state.songsLoaded) {
        val service = openService ?: return@LaunchedEffect
        if (state.bibleBooks.isEmpty()) state.loadBibleBooks(host.bibleBooks())
        state.measureService(service, host.measuredSeconds)
        state.checkService(service, host.resolveBookId, io)
    }
    val clock = rememberRunClock(openService, today, now)
    // The latest fired cue, until dismissed. Keyed by firing, so the same cue going off again --
    // fired by hand, or on another day -- shows again.
    val firedCues by CueFeed.fired.collectAsState()
    var dismissedToast by remember { mutableStateOf<String?>(null) }
    val toast = firedCues.firstOrNull()?.takeUnless { it.key == dismissedToast }

    CompositionLocalProvider(LocalUse24HourClock provides state.document.preferences.use24HourClock) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Box(Modifier.fillMaxSize()) {
                CalendarBody(
                    state = state,
                    host = host,
                    dialogs = dialogs,
                    clock = clock,
                    today = today,
                    onExport = exportAction(state, host, io, scope),
                    exportAudience = state.document.preferences.pdfExport.lastAudience,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
                if (toast != null) {
                    CueToast(
                        event = toast,
                        onDismiss = { dismissedToast = toast.key },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(TOAST_MARGIN),
                    )
                }
            }
        }
        CalendarDialogs(
            state = state,
            host = host,
            dialogs = dialogs,
            colorPicker = colorPicker,
            songEditor = songEditor,
            onLoaded = { onClose?.invoke() },
        )
    }
}

/** The window itself -- header, banner, month, day and the open service -- without its dialogs. */
@Composable
private fun CalendarBody(
    state: CalendarState,
    host: CalendarHost,
    dialogs: CalendarDialogState,
    clock: RunClockState,
    today: LocalDate,
    onExport: ((PdfAudience) -> Unit)?,
    exportAudience: PdfAudience,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Header(
            monthLabel = monthHeading(state.visibleMonth),
            plannedThisMonth = state.servicesInVisibleMonth().size,
            onToday = state::goToToday,
            onExport = onExport,
            exportAudience = exportAudience,
            // Only while the cloud sync is on: an invite to a relay this computer is not talking
            // to would be a code that leads nowhere.
            onInvite = host.cloudSync?.takeIf { it.enabled() }?.invitePhone,
            onSettings = dialogs::openSettings,
        )
        HorizontalDivider()
        RecoveryBanner(source = state.source, onDismiss = state::acknowledgeSource)

        Row(Modifier.fillMaxSize().weight(1f)) {
            val service = state.selectedService
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
                    selectedServiceId = service?.id,
                    onSelectService = state::selectService,
                    onAddService = { dialogs.creatingService = true },
                    onEditService = { dialogs.editingService = it },
                )
                HorizontalDivider()
                if (service == null) {
                    NoServicesPane(
                        dayLabel = shortDate(state.selectedDate),
                        copyLabel = state.mostRecentServiceBefore()?.name,
                        onAddService = { dialogs.creatingService = true },
                        onCopyLast = { dialogs.creatingService = true },
                        modifier = Modifier.weight(1f),
                    )
                    HorizontalDivider()
                    // The footer is the window's own bar, not the run of show's: Close
                    // lives here, so it cannot disappear with the day's only service.
                    Footer(status = "", onLoad = null, onClose = onClose)
                } else {
                    OpenServicePane(state, host, dialogs, service, clock, onClose)
                }
            }
        }
    }
}

/** The run of show of [service] and the footer under it, filling the rest of the day column. */
@Composable
private fun ColumnScope.OpenServicePane(
    state: CalendarState,
    host: CalendarHost,
    dialogs: CalendarDialogState,
    service: PlannedService,
    clock: RunClockState,
    onClose: (() -> Unit)?,
) {
    val fixScope = rememberCoroutineScope()
    RunOfShowPane(
        service = service,
        now = clock.now,
        previewing = clock.previewing,
        header = RunOfShowHeaderActions(
            onClockStep = clock.step,
            onClockReset = clock.reset,
            onArmed = { state.setArmed(service.id, it) },
            onLayOutTimes = { state.layOutTimes(service.id) },
            onCopy = { dialogs.copyFrom = service },
            onSaveTemplate = { dialogs.templateFrom = service },
        ),
        measuredSeconds = state.measuredSeconds,
        problems = state.preflight,
        onAddItem = { dialogs.openPicker(null) },
        onChangeItem = { dialogs.openPicker(it) },
        onRemove = { state.removeItem(service.id, it) },
        onMove = { from, to -> state.moveItem(service.id, from, to) },
        onPlannedSecondsChange = { itemId, seconds -> state.setPlannedSeconds(service.id, itemId, seconds) },
        onCueEnabled = { cueId, enabled -> state.setCueEnabled(service.id, cueId, enabled) },
        onFireCue = { cue -> fireCue(host, service.rowsForSchedule(), cue, startTime = service.startTime) },
        onFixProblem = { item, problem ->
            when (problem.fix) {
                ProblemFix.PICK_AGAIN -> dialogs.openPicker(item)
                ProblemFix.LOCATE_FILE, ProblemFix.LOCATE_FOLDER -> fixScope.launch {
                    relocate(item, problem.fix, host)?.let {
                        state.updateItem(service.id, it)
                        host.recordUsage(CalendarUsage.MISSING_FILE_FIXED)
                    }
                }
            }
        },
        modifier = Modifier.weight(1f),
    )
    HorizontalDivider()
    Footer(
        status = service.name + " · " + when (val count = service.autoStartCount()) {
            0 -> stringResource(Res.string.calendar_all_manual)
            1 -> stringResource(Res.string.calendar_auto_start_one)
            else -> stringResource(Res.string.calendar_auto_starts, count)
        },
        onClose = onClose,
        onLoad = {
            // Only ask when replacing would actually discard something.
            if (host.currentSchedule().isEmpty()) {
                host.loadIntoSchedule(
                    service.rowsForSchedule(), service.timingForSchedule(), true, service.armed, service.startTime,
                )
                // The run of show is in the Schedule tab now, which is where the next thing
                // happens -- so get out of the way.
                onClose?.invoke()
            } else {
                dialogs.loadConfirmFor = service
            }
        },
    )
}

/**
 * [item] pointing at wherever the user says its file or folder went, or null if they gave up.
 *
 * The dialog opens on the path the row still holds, since a moved file is usually one folder
 * away. A folder is re-counted on the way in, off the composing thread.
 */
private suspend fun relocate(item: ScheduleItem, fix: ProblemFix, host: CalendarHost): ScheduleItem? {
    val missing = item.pointedPath() ?: return null
    return if (fix == ProblemFix.LOCATE_FOLDER) {
        val folder = host.locateFolder(missing) ?: return null
        item.relocatedTo(folder, imageCount = withContext(Dispatchers.IO) { countImages(folder) })
    } else {
        host.locateFile(missing)?.let { item.relocatedTo(it) }
    }
}

/**
 * The header's Export action, or null when no service is open. It writes the chosen copy of the
 * run of show wherever the host's file chooser points; a cancelled chooser does nothing, and a
 * failure to write is reported through the host rather than crashing the window.
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
): ((PdfAudience) -> Unit)? {
    val service = state.selectedService ?: return null
    val label = shortDate(state.selectedDate)
    val preferences = state.document.preferences
    return { audience ->
        if (audience != preferences.pdfExport.lastAudience) {
            state.updatePreferences(
                preferences.copy(pdfExport = preferences.pdfExport.copy(lastAudience = audience)),
            )
        }
        scope.launch {
            val lastFolder = File(preferences.pdfExport.lastFolder).takeIf { it.path.isNotEmpty() && it.isDirectory }
            val target = host.chooseExportFile("${service.name} - ${service.date}.pdf", lastFolder) ?: return@launch
            target.parentFile?.path?.let { folder ->
                val current = state.document.preferences
                if (folder != current.pdfExport.lastFolder) {
                    state.updatePreferences(current.copy(pdfExport = current.pdfExport.copy(lastFolder = folder)))
                }
            }
            // Off the composing thread: this embeds a font and writes a file.
            withContext(io) {
                runCatching {
                    exportRunOfShowPdf(
                        service = service,
                        target = target,
                        dateLabel = label,
                        font = host.pdfFont,
                        use24Hour = preferences.use24HourClock,
                        settings = preferences.pdfExport,
                        audience = audience,
                    )
                }.onSuccess { host.recordUsage(CalendarUsage.EXPORTED) }
                    .onFailure { host.reportError("Calendar run-of-show PDF export", it) }
            }
        }
    }
}

/**
 * The window's header.
 *
 * Ordered as the design has it: the badge, the title and its subtitle, a rule, then **Today** —
 * all on the left, directly over the month pane it acts on — and only then the spacer that pushes
 * the saved note, Export and Settings to the right. Close is not here: it sits in the footer,
 * beside Load into Schedule, where the window's two ways out are next to each other.
 */
@Composable
private fun Header(
    monthLabel: String,
    plannedThisMonth: Int,
    onToday: () -> Unit,
    onExport: ((PdfAudience) -> Unit)?,
    exportAudience: PdfAudience,
    onInvite: (() -> Unit)?,
    onSettings: () -> Unit,
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
            ExportSplitButton(audience = exportAudience, height = HEADER_BUTTON, onExport = onExport)
        }
        if (onInvite != null) {
            HeaderButton(
                label = stringResource(Res.string.calendar_cloud_invite),
                icon = Icons.Filled.QrCode2,
                onClick = onInvite,
            )
        }
        HeaderButton(
            label = stringResource(Res.string.calendar_settings_open),
            icon = Icons.Filled.Settings,
            onClick = onSettings,
        )
    }
}

/** One of the header's quiet bordered controls, at the design's 28dp. */
@Composable
private fun HeaderButton(
    label: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val key = elevationPalette().key
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .height(HEADER_BUTTON)
            .raisedKey(CalendarMetrics.buttonRadius, key, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = key.ink, modifier = Modifier.size(13.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
            fontWeight = FontWeight.SemiBold,
            color = key.ink,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * The window's bottom bar: what the automation will do, then Close and Load into Schedule.
 *
 * The two are deliberately unalike. Load is the primary action, raised in the accent; Close is the
 * neutral key beside it, so the pair does not read as two equal choices.
 */
@Composable
private fun Footer(status: String, onLoad: (() -> Unit)?, onClose: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 9.dp),
    ) {
        // `Sunday Morning · 4 of 4 cues will fire automatically`: what the automation will do,
        // said once where it is read last.
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 10.5.sp),
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (onClose != null) {
            QuietButton(
                label = stringResource(Res.string.calendar_close),
                onClick = onClose,
                height = CalendarMetrics.addServiceButtonHeight,
            )
        }
        if (onLoad == null) return@Row
        val accent = elevationPalette().accent
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .height(CalendarMetrics.addServiceButtonHeight)
                .raisedKey(CalendarMetrics.buttonRadius, accent, onClick = onLoad)
                .padding(horizontal = 13.dp),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = null,
                tint = accent.ink,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = stringResource(Res.string.calendar_load_into_schedule),
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.5.sp),
                fontWeight = FontWeight.Bold,
                color = accent.ink,
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
        GhostButton(onClick = onDismiss) { Text(stringResource(Res.string.calendar_dismiss)) }
    }
}

private val HEADER_HEIGHT = 52.dp
private val HEADER_BADGE = 30.dp
private val HEADER_BUTTON = 28.dp
private const val BADGE_TINT = 0.16f

private val TOAST_MARGIN = 14.dp
