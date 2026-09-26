package org.churchpresenter.calendar
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.CopiedRows
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PreflightProblem
import org.churchpresenter.calendar.model.preflight
import org.churchpresenter.calendar.model.typedBookNames
import org.churchpresenter.calendar.model.SavedTemplate
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.isDurationTimer
import org.churchpresenter.calendar.model.withNewId
import org.churchpresenter.calendar.model.withStartMovedFrom
import org.churchpresenter.calendar.model.withTimesLaidOut
import org.churchpresenter.calendar.model.withTimerSeconds
import org.churchpresenter.calendar.model.withCuesAsRows
import org.churchpresenter.calendar.model.copiedRows
import org.churchpresenter.calendar.model.parseStoredDate
import org.churchpresenter.calendar.model.stampingChanged
import org.churchpresenter.calendar.model.storedDate
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.withServices
import org.churchpresenter.calendar.model.withUniqueRowIds
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
/**
 * What the planner is showing, and the only thing that writes [CalendarStore].
 *
 * The decisions live in `model/` as plain functions — this holds the answers where Compose can see
 * them. Two things it does differently from [org.churchpresenter.songlibrary.SongLibraryState], and
 * both are deliberate:
 *
 * - **Every mutation saves immediately.** A calendar is a few kilobytes and the store is atomic
 *   with three backups behind it, so there is no reason to hold a dirty document in memory and a
 *   very good reason not to: this window is opened mid-week, edited, and left open, and an app that
 *   is killed on the Sunday must not lose the Thursday's planning.
 * - **The song list is read once and cached.** The picker searches it on every keystroke; a library
 *   is thousands of songs and re-reading the folder per search is the same mistake `SongLibraryState`
 *   documents in its own header.
 */
// One function per thing the planner can do to a service. Splitting the class would split the
// document they all read and write.
@Suppress("TooManyFunctions")
class CalendarState(
    private val store: CalendarStore,
    private val songFolder: File?,
    private val today: LocalDate = LocalDate.now(),
    private val presetStore: PresetStore? = null,
    /** Where an edit's timestamp comes from; a test pins it so a merge can be reasoned about. */
    private val now: () -> Instant = { Instant.now() },
    /**
     * Called after every save, so a watcher can tell this window's own write from another
     * machine's — see [CalendarFileWatcher].
     */
    private val onSaved: () -> Unit = {},
) {
    var document by mutableStateOf(CalendarDocument())
        private set
    /** Where [document] came from, so the window can say it was recovered. Cleared once acknowledged. */
    var source by mutableStateOf(CalendarSource.NEW)
        private set
    var visibleMonth by mutableStateOf(YearMonth.from(today))
        private set
    var selectedDate by mutableStateOf(today)
        private set
    /** Which of [servicesOnSelectedDate] is open below the grid. Null when the day is empty. */
    var selectedServiceId by mutableStateOf<String?>(null)
        private set
    var songs by mutableStateOf<List<SongItem>>(emptyList())
        private set
    var songsLoaded by mutableStateOf(false)
        private set

    /** The items saved with **Save preset** from the app's tabs, newest first. */
    var presets by mutableStateOf<List<ItemPreset>>(emptyList())
        private set
    /** `presets.json` as last read, so another machine's copy can be merged against it. */
    private var presetDocument = PresetDocument()


    /** The primary Bible's books, as the host supplies them. Read once — see [loadBibleBooks]. */
    var bibleBooks by mutableStateOf<List<CalendarBibleBook>>(emptyList())
        private set

    /**
     * What each row of the open service has actually taken on screen, by row id -- the host's
     * measured length, fetched by [measureService] and shown where it differs from the plan.
     */
    var measuredSeconds by mutableStateOf<Map<String, Int>>(emptyMap())
        private set

    /** The open service's rows that will not go on screen on the day, by row id -- see [checkService]. */
    var preflight by mutableStateOf<Map<String, PreflightProblem>>(emptyMap())
        private set
    /** Recomputed when the document or the selection changes, not on every read — the run of show
     *  reads this once per row per frame. */
    private val servicesOnDay by derivedStateOf {
        document.servicesOn(storedDate(selectedDate))
    }
    val servicesOnSelectedDate: List<PlannedService> get() = servicesOnDay
    /** The service whose run of show is showing, or null when the day has none. */
    val selectedService: PlannedService?
        get() = selectedServiceId?.let { id -> servicesOnDay.firstOrNull { it.id == id } }
            ?: servicesOnDay.firstOrNull()
    private val plannedDaySet by derivedStateOf { document.plannedDates() }
    fun hasServices(date: LocalDate): Boolean = storedDate(date) in plannedDaySet
    fun servicesOn(date: LocalDate): List<PlannedService> = document.servicesOn(storedDate(date))
    // ── Loading ───────────────────────────────────────────────────────────────
    /** Reads the calendar. Off the composing thread: the file is small, but the folder may not be local. */
    suspend fun loadAsync(io: CoroutineDispatcher = Dispatchers.IO) {
        val loaded = withContext(io) { store.load() }
        // Before anything renders: the run of show keys its rows by id, and a file on disk cannot
        // promise those are unique. See withUniqueRowIds.
        document = loaded.document.withCuesAsRows().withUniqueRowIds()
        source = loaded.source
        reloadPresets(io)
    }

    /**
     * Re-reads `presets.json`. Called on load and whenever the window comes back to the front —
     * the tabs write presets while this window is open, and it has no other way to hear of them.
     */
    suspend fun reloadPresets(io: CoroutineDispatcher = Dispatchers.IO) {
        val store = presetStore ?: return
        showPresets(withContext(io) { store.load() })
    }

    /**
     * Another machine wrote `presets.json`: what it holds is merged with what was last read here,
     * preset by preset, and written back only if the merge added something — the same shape as
     * [reloadMerging], in [PresetStore.reloadMerging].
     */
    suspend fun reloadPresetsMerging(io: CoroutineDispatcher = Dispatchers.IO) {
        val store = presetStore ?: return
        val known = presetDocument
        showPresets(withContext(io) { store.reloadMerging(known) })
    }

    private fun showPresets(document: PresetDocument) {
        presetDocument = document
        presets = document.presets.sortedWith(compareByDescending<ItemPreset> { it.savedAt }.thenBy { it.id })
    }

    fun deletePreset(id: String) {
        val store = presetStore ?: return
        runCatching { store.remove(id) }
        presetDocument = presetDocument.withoutPreset(id, now())
        presets = presets.filterNot { it.id == id }
    }
    /** Reads the song folder for the add-item picker. Thousands of files — never on the UI thread. */
    suspend fun loadSongsAsync(io: CoroutineDispatcher = Dispatchers.IO) {
        val folder = songFolder
        if (folder == null) {
            songsLoaded = true
            return
        }
        songs = withContext(io) { runCatching { SongLibrary(folder).load() }.getOrDefault(emptyList()) }
        songsLoaded = true
    }
    /**
     * Takes the host's book list once.
     *
     * Once, because building it walks every chapter of every book to count verses — cheap, but not
     * something to repeat on each recomposition of the picker.
     */
    /** Asks [measure] what every row of [service] has taken on screen, and keeps the answers. */
    suspend fun measureService(service: PlannedService, measure: suspend (ScheduleItem) -> Int?) {
        measuredSeconds = service.contentItems().mapNotNull { row -> measure(row)?.let { row.id to it } }.toMap()
    }

    /**
     * Checks every row of [service] against the disk, the song library and the primary Bible --
     * see [preflight]. Off the composing thread, as it touches the file system.
     */
    suspend fun checkService(
        service: PlannedService,
        resolveBook: suspend (name: String) -> Int? = { null },
        io: CoroutineDispatcher = Dispatchers.IO,
    ) {
        val songsNow = songs
        val booksNow = bibleBooks
        // Resolved up front, once per distinct name: the resolver suspends, and the check itself
        // is a plain function that must not.
        val resolved = typedBookNames(service.items).associateWith { resolveBook(it) }
        preflight = withContext(io) { preflight(service.items, songsNow, booksNow, resolveBook = { resolved[it] }) }
    }

    fun loadBibleBooks(books: List<CalendarBibleBook>) {
        if (bibleBooks.isEmpty()) bibleBooks = books
    }

    /** Dismisses the "recovered from a backup" note once the user has seen it. */
    fun acknowledgeSource() {
        source = CalendarSource.FILE
    }
    // ── Navigation ────────────────────────────────────────────────────────────
    fun showMonth(month: YearMonth) {
        visibleMonth = month
    }
    fun showPreviousMonth() = showMonth(visibleMonth.minusMonths(1))
    fun showNextMonth() = showMonth(visibleMonth.plusMonths(1))
    fun goToToday() {
        visibleMonth = YearMonth.from(today)
        select(today)
    }
    fun select(date: LocalDate) {
        selectedDate = date
        if (YearMonth.from(date) != visibleMonth) visibleMonth = YearMonth.from(date)
        // The previous day's selection would otherwise stick and show nothing.
        selectedServiceId = document.servicesOn(storedDate(date)).firstOrNull()?.id
    }
    fun selectService(id: String) {
        selectedServiceId = id
    }
    // ── Services ──────────────────────────────────────────────────────────────
    /** Every service planned in the month currently on screen, for the window's header count. */
    fun servicesInVisibleMonth(): List<PlannedService> = document.services.filter { service ->
        parseStoredDate(service.date)?.let { YearMonth.from(it) == visibleMonth } == true
    }

    /**
     * The most recent service planned before [selectedDate], which is what "copy last week's" means.
     *
     * Before rather than nearest: a planner is looking at a date that has not happened yet, and the
     * thing worth copying is the last one that did.
     */
    fun mostRecentServiceBefore(): PlannedService? = document.services
        .filter { (parseStoredDate(it.date) ?: LocalDate.MAX) < selectedDate }
        .maxByOrNull { it.date + it.startTime }

    /**
     * What a new service can start from: nothing, or a copy of the most recent service of each kind.
     *
     * Saved templates — the design's `Sunday Morning · Template` entries — need a template store,
     * which is a later phase. These are real services, so the list is never a promise the planner
     * cannot keep.
     */
    fun templateOptions(): List<ServiceTemplate> {
        val saved = document.templates.map { ServiceTemplate.Saved(it) }
        val recentByKind = ServiceKind.entries.mapNotNull { kind ->
            document.services
                .filter { it.kind == kind.id && (parseStoredDate(it.date) ?: LocalDate.MAX) < selectedDate }
                .maxByOrNull { it.date + it.startTime }
        }
        return listOf(ServiceTemplate.Blank) + saved + recentByKind.map { ServiceTemplate.CopyOf(it) }
    }

    /** Adds a service on [date] -- the selected day unless another was chosen -- and opens it. */
    fun addService(
        name: String,
        startTime: String,
        kind: ServiceKind,
        template: ServiceTemplate = ServiceTemplate.Blank,
        date: LocalDate = selectedDate,
    ): PlannedService {
        // Copied rows are re-keyed, or the new service and the one it came from share row ids — and
        // plannedSeconds is keyed by them, so editing one estimate would move the other's too.
        val rows = when (template) {
            ServiceTemplate.Blank -> CopiedRows(emptyList(), emptyMap())
            is ServiceTemplate.CopyOf ->
                copiedRows(template.service.items, template.service.plannedSeconds, template.service.timing)
            is ServiceTemplate.Saved ->
                copiedRows(template.template.items, template.template.plannedSeconds, template.template.timing)
            is ServiceTemplate.FromSchedule -> scheduleRows(template.items)
        }
        val service = PlannedService(
            id = UUID.randomUUID().toString(),
            date = storedDate(date),
            name = name,
            startTime = startTime,
            kind = kind.id,
            items = rows.items,
            plannedSeconds = rows.plannedSeconds,
            timing = rows.timing,
            armed = document.preferences.armByDefault,
        )
        commit(document.withService(service))
        select(date)
        selectedServiceId = service.id
        return service
    }

    /**
     * The Schedule tab's rows as a new service's, **ids kept** so the Schedule is recognised as
     * this service afterwards. A row whose id another service already holds -- one loaded from the
     * calendar and kept -- is the one exception: sharing it would tie the two services together.
     */
    private fun scheduleRows(items: List<ScheduleItem>): CopiedRows {
        val taken = document.services.flatMapTo(HashSet()) { service -> service.items.map { it.id } }
        return CopiedRows(items.map { if (it.id in taken) it.withNewId() else it }, emptyMap())
    }

    /**
     * Saves [service]'s name, time and kind — and, with [wholeSeries], the same three on every other
     * occurrence of its series. Never the run of show: each week's is its own.
     */
    /**
     * Saves an edited service -- and moves its plan with it.
     *
     * A start time that changed carries every pinned row and cue by the same amount, so a
     * pre-service sequence built as `−20 / −15 / −5` still reads that way after the service moves.
     * See [withStartMovedFrom]. Each service of a series is shifted by its *own* difference, not
     * by the edited one's.
     *
     * A new date moves this service alone, never the rest of its series, and the calendar follows
     * it to its new day.
     */
    fun updateService(service: PlannedService, wholeSeries: Boolean = false) {
        val target = document.serviceById(service.id)
            ?.let { service.withStartMovedFrom(it.startTime) }
            ?: service
        val siblings = if (wholeSeries) document.servicesInSeries(service.seriesId) else emptyList()
        val updated = siblings
            .filterNot { it.id == service.id }
            .map {
                it.copy(name = service.name, startTime = service.startTime, kind = service.kind)
                    .withStartMovedFrom(it.startTime)
            }
        commit(document.withServices(listOf(target) + updated))
        if (target.date != storedDate(selectedDate)) {
            parseStoredDate(target.date)?.let(::select)
            selectedServiceId = target.id
        }
    }

    fun deleteService(id: String, wholeSeries: Boolean = false) {
        val target = document.serviceById(id) ?: return
        val ids = if (wholeSeries && target.isInSeries()) {
            document.servicesInSeries(target.seriesId).map { it.id }
        } else {
            listOf(id)
        }
        commit(ids.fold(document) { current, each -> current.withoutService(each) })
        if (selectedServiceId in ids) {
            selectedServiceId = servicesOnDay.firstOrNull()?.id
        }
    }

    // ── Copy ──────────────────────────────────────────────────────────────────

    /**
     * Plans a copy of [service] on each of [dates] — the Copy sheet's **Paste** and **Create N**.
     *
     * With [includeRunOfShow] each copy gets its own re-keyed run of show, and with [includeCues]
     * the cue rows in it; without either, an empty service with the same name, time and kind. A [repeat]
     * other than NONE makes the copies — and
     * [service] itself, unless it already belongs to one — a series, which is what lets a later
     * edit or delete reach all of them. One commit for the lot, so the file is written once.
     */
    fun copyService(
        service: PlannedService,
        dates: List<LocalDate>,
        includeRunOfShow: Boolean,
        includeCues: Boolean,
        repeat: ServiceRepeat = ServiceRepeat.NONE,
    ) {
        if (dates.isEmpty()) return
        val seriesId = when {
            repeat == ServiceRepeat.NONE -> ""
            service.isInSeries() -> service.seriesId
            else -> UUID.randomUUID().toString()
        }
        val kept = service.items.filter { item ->
            if (item is ScheduleItem.CueItem) includeCues else includeRunOfShow
        }
        val copies = dates.map { date ->
            val rows = copiedRows(kept, service.plannedSeconds, service.timing)
            PlannedService(
                id = UUID.randomUUID().toString(),
                date = storedDate(date),
                name = service.name,
                startTime = service.startTime,
                kind = service.kind,
                items = rows.items,
                plannedSeconds = rows.plannedSeconds,
                timing = rows.timing,
                armed = service.armed,
                seriesId = seriesId,
                repeat = if (seriesId.isEmpty()) "" else repeat.id,
            )
        }
        val source = if (seriesId.isNotEmpty() && !service.isInSeries()) {
            listOf(service.copy(seriesId = seriesId, repeat = repeat.id))
        } else {
            emptyList()
        }
        commit(document.withServices(source + copies))
    }

    // ── Templates ─────────────────────────────────────────────────────────────

    /**
     * Saves [service]'s run of show as a template called [name], replacing one of the same name.
     *
     * Replacing rather than duplicating: saving "Sunday Morning" again after improving it is the
     * common case, and two templates by one name are indistinguishable in the `Start from` list.
     * [includeSections], [includeItems] and [includeCues] are the sheet's Include boxes — the
     * headings alone make a skeleton to fill each week; the items alone, a set list without its
     * structure; the cues, the same automation every week without redoing it.
     */
    fun saveTemplate(
        service: PlannedService,
        name: String,
        includeSections: Boolean = true,
        includeItems: Boolean = true,
        includeCues: Boolean = true,
    ) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        val kept = service.items.filter { item ->
            when (item) {
                is ScheduleItem.LabelItem -> includeSections
                is ScheduleItem.CueItem -> includeCues
                else -> includeItems
            }
        }
        val rows = copiedRows(kept, service.plannedSeconds, service.timing)
        val existing = document.templates.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        commit(
            document.withTemplate(
                SavedTemplate(
                    id = existing?.id ?: UUID.randomUUID().toString(),
                    name = trimmed,
                    startTime = service.startTime,
                    kind = service.kind,
                    items = rows.items,
                    plannedSeconds = rows.plannedSeconds,
                    timing = rows.timing,
                )
            )
        )
    }

    fun deleteTemplate(id: String) {
        commit(document.withoutTemplate(id))
    }

    /** Times every row of the service from the first one -- see [withTimesLaidOut]. */
    fun layOutTimes(serviceId: String) {
        val service = document.serviceById(serviceId) ?: return
        commit(document.withService(service.withTimesLaidOut()))
    }

    // ── Cues ──────────────────────────────────────────────────────────────────

    /** Ticks or unticks one cue — "skip this one" — without touching the rest. */
    fun setCueEnabled(serviceId: String, cueId: String, enabled: Boolean) {
        val service = document.serviceById(serviceId) ?: return
        val cue = service.cueRows().firstOrNull { it.id == cueId } ?: return
        // In place, not re-placed: ticking a cue is not moving it.
        val rows = service.items.map { if (it.id == cueId) cue.copy(enabled = enabled) else it }
        commit(document.withService(service.copy(items = rows)))
    }

    /** Arms or disarms every cue on the service at once. */
    fun setArmed(serviceId: String, armed: Boolean) {
        val service = document.serviceById(serviceId) ?: return
        commit(document.withService(service.copy(armed = armed)))
    }
    // ── Run of show ───────────────────────────────────────────────────────────
    fun addItems(serviceId: String, items: List<ScheduleItem>, at: Int? = null) {
        val service = document.serviceById(serviceId) ?: return
        val next = service.items.toMutableList()
        val index = at?.coerceIn(0, next.size) ?: next.size
        next.addAll(index, items)
        commit(document.withService(service.copy(items = next)))
    }
    /**
     * Swaps the row [itemId] for [items], keeping its place in the order.
     *
     * The planned length is deliberately **not** carried over: the replacement is a different
     * thing, and an estimate measured for the song that was there says nothing about the one that
     * now is. It is dropped rather than silently inherited.
     */
    fun replaceItem(serviceId: String, itemId: String, items: List<ScheduleItem>) {
        val service = document.serviceById(serviceId) ?: return
        val index = service.items.indexOfFirst { it.id == itemId }
        if (index < 0) return
        val next = service.items.toMutableList()
        next.removeAt(index)
        next.addAll(index, items)
        commit(
            document.withService(
                service.copy(items = next, plannedSeconds = service.plannedSeconds - itemId)
            )
        )
    }

    /**
     * Swaps one row for [item], which carries the same id -- a row pointed at a file that moved.
     * Unlike [replaceItem], the planned length and timing stay: it is the same row, corrected.
     */
    fun updateItem(serviceId: String, item: ScheduleItem) {
        val service = document.serviceById(serviceId) ?: return
        val index = service.items.indexOfFirst { it.id == item.id }
        if (index < 0) return
        commit(document.withService(service.copy(items = service.items.toMutableList().also { it[index] = item })))
    }

    fun removeItem(serviceId: String, itemId: String) {
        val service = document.serviceById(serviceId) ?: return
        commit(
            document.withService(
                service.copy(
                    items = service.items.filterNot { it.id == itemId },
                    // Drop the estimate with the row, or the totals keep counting a row nobody sees.
                    plannedSeconds = service.plannedSeconds - itemId,
                )
            )
        )
    }
    /** Moves the row at [from] to [to], both indices into the run of show. */
    fun moveItem(serviceId: String, from: Int, to: Int) {
        val service = document.serviceById(serviceId) ?: return
        if (from !in service.items.indices) return
        val next = service.items.toMutableList()
        val item = next.removeAt(from)
        next.add(to.coerceIn(0, next.size), item)
        commit(document.withService(service.copy(items = next)))
    }
    /** Sets a row's planned length, or clears it when [seconds] is null. */
    /**
     * Sets a row's planned length. On a duration timer the length *is* the timer, so typing `10:00`
     * on one makes it a ten-minute countdown rather than an estimate beside a fifteen-minute one.
     */
    fun setPlannedSeconds(serviceId: String, itemId: String, seconds: Int?) {
        val service = document.serviceById(serviceId) ?: return
        val next = if (seconds == null) {
            service.plannedSeconds - itemId
        } else {
            service.plannedSeconds + (itemId to seconds)
        }
        val rows = service.items.map { row ->
            val timer = (row as? ScheduleItem.AnnouncementItem)?.takeIf { it.id == itemId && it.isDurationTimer() }
            if (seconds != null && timer != null) timer.withTimerSeconds(seconds) else row
        }
        commit(document.withService(service.copy(items = rows, plannedSeconds = next)))
    }

    /** Sets how a row runs -- its start, repeats and end. The default entry is dropped, not stored. */
    fun setTiming(serviceId: String, itemId: String, timing: RowTiming) {
        val service = document.serviceById(serviceId) ?: return
        val next = if (timing.isDefault()) service.timing - itemId else service.timing + (itemId to timing)
        commit(document.withService(service.copy(timing = next)))
    }
    // ── The song library ──────────────────────────────────────────────────────

    /** Every songbook the loaded library holds, for an editor that offers a list of them. */
    fun songbooks(): List<String> = songs.map { it.songbook }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedBy { it.lowercase() }

    /**
     * Writes an edited song back to the library folder and refreshes the list behind the picker.
     *
     * The folder is the app's real song folder — what is written here is what the Songs tab reads
     * on its next scan, which is the same contract the Song Library Manager works under.
     */
    suspend fun saveSong(original: SongItem, edited: SongItem, io: CoroutineDispatcher = Dispatchers.IO) {
        val folder = songFolder ?: return
        withContext(io) {
            runCatching { SongLibrary(folder).save(mapOf(original.sourceFile to original), listOf(edited)) }
        }
        // Re-read rather than patching the in-memory list: saving can move the file (a renumber or
        // a songbook change is a move), so the row's identity may not be what it was.
        songs = withContext(io) { runCatching { SongLibrary(folder).load() }.getOrDefault(songs) }
    }

    // ── Preferences ───────────────────────────────────────────────────────────

    fun updatePreferences(preferences: CalendarPreferences) {
        commit(document.copy(preferences = preferences))
    }

    /**
     * Adds a section, or does nothing if one by that name already exists.
     *
     * Names are the identity — see [CalendarPreferences.sections] — so two entries called `Worship`
     * would be two rules for the same heading, and which one won would depend on list order.
     */
    fun addSection(name: String, colorHex: String) {
        val trimmed = name.trim()
        val existing = document.preferences.sections
        if (trimmed.isEmpty() || existing.any { it.name.equals(trimmed, ignoreCase = true) }) return
        updatePreferences(document.preferences.copy(sections = existing + SectionStyle(trimmed, colorHex)))
    }

    fun setSectionColor(name: String, colorHex: String) {
        val next = document.preferences.sections.map {
            if (it.name == name) it.copy(colorHex = colorHex) else it
        }
        updatePreferences(document.preferences.copy(sections = next))
    }

    /** Renames a section, leaving headings already placed in a run of show untouched. */
    fun renameSection(from: String, to: String) {
        val trimmed = to.trim()
        val existing = document.preferences.sections
        if (trimmed.isEmpty() || existing.any { it.name.equals(trimmed, ignoreCase = true) && it.name != from }) return
        updatePreferences(
            document.preferences.copy(
                sections = existing.map { if (it.name == from) it.copy(name = trimmed) else it },
                pdfExport = document.preferences.pdfExport.withSectionRenamed(from, trimmed),
            )
        )
    }

    fun removeSection(name: String) {
        val next = document.preferences.sections.filterNot { it.name == name }
        updatePreferences(document.preferences.copy(sections = next))
    }

    // ── Persistence ───────────────────────────────────────────────────────────
    /**
     * The one place the document changes, and the one place it is written.
     *
     * Saving here rather than at each call site is what makes "every mutation is saved" true by
     * construction instead of by everyone remembering — and, since this is also the only place a
     * service can change, the one place each one's [PlannedService.updatedAt] is stamped. Two
     * machines sharing this file decide *per service* which copy is newer, so a stamp that was
     * only written sometimes would lose whichever edit forgot it.
     */
    private fun commit(next: CalendarDocument) {
        val stamped = next.stampingChanged(document, now())
        document = stamped
        runCatching { store.save(stamped) }
        onSaved()
    }

    /**
     * Takes what is on disk now and folds it into what is open here — see
     * [CalendarDocument.mergedWith].
     *
     * Called when the file changes underneath, which is what a shared folder does when the other
     * machine saves. The result is written back, so both copies end up holding the merge rather
     * than each holding half of it.
     */
    suspend fun reloadMerging(io: CoroutineDispatcher = Dispatchers.IO) {
        val onDisk = withContext(io) { store.load() }.document.withCuesAsRows().withUniqueRowIds()
        if (onDisk == document) return
        val merged = document.mergedWith(onDisk, now())
        document = merged
        // Only when the merge added something the file did not have: two machines writing the same
        // merge back at each other is a loop, and an identical document is not worth a write.
        if (merged != onDisk) {
            runCatching { store.save(merged) }
            onSaved()
        }
        if (selectedServiceId != null && document.serviceById(selectedServiceId!!) == null) {
            selectedServiceId = servicesOnDay.firstOrNull()?.id
        }
    }
}
