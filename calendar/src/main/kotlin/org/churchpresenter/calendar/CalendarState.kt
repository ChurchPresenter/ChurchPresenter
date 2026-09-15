package org.churchpresenter.calendar
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.churchpresenter.calendar.CalendarBibleBook
import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.CalendarPreferences
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.parseStoredDate
import org.churchpresenter.calendar.model.storedDate
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.calendar.model.withNewId
import org.churchpresenter.calendar.model.withUniqueRowIds
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import java.io.File
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

    /** The primary Bible's books, as the host supplies them. Read once — see [loadBibleBooks]. */
    var bibleBooks by mutableStateOf<List<CalendarBibleBook>>(emptyList())
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
        document = loaded.document.withUniqueRowIds()
        source = loaded.source
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
        val recentByKind = ServiceKind.entries.mapNotNull { kind ->
            document.services
                .filter { it.kind == kind.id && (parseStoredDate(it.date) ?: LocalDate.MAX) < selectedDate }
                .maxByOrNull { it.date + it.startTime }
        }
        return listOf(ServiceTemplate.Blank) + recentByKind.map { ServiceTemplate.CopyOf(it) }
    }

    /** Adds a service to [selectedDate] and opens it. Returns the new service. */
    fun addService(
        name: String,
        startTime: String,
        kind: ServiceKind,
        template: ServiceTemplate = ServiceTemplate.Blank,
    ): PlannedService {
        val source = (template as? ServiceTemplate.CopyOf)?.service
        // Copied rows are re-keyed, or the new service and the one it came from share row ids — and
        // plannedSeconds is keyed by them, so editing one estimate would move the other's too.
        val copied = source?.items?.map { it.withNewId() }.orEmpty()
        val plannedByIndex = source?.items?.mapIndexedNotNull { index, item ->
            source.plannedSeconds[item.id]?.let { index to it }
        }.orEmpty()
        val service = PlannedService(
            id = UUID.randomUUID().toString(),
            date = storedDate(selectedDate),
            name = name,
            startTime = startTime,
            kind = kind.id,
            items = copied,
            plannedSeconds = plannedByIndex.associate { (index, seconds) -> copied[index].id to seconds },
            armed = document.preferences.armByDefault,
        )
        commit(document.withService(service))
        selectedServiceId = service.id
        return service
    }
    fun updateService(service: PlannedService) {
        commit(document.withService(service))
    }
    fun deleteService(id: String) {
        commit(document.withoutService(id))
        if (selectedServiceId == id) {
            selectedServiceId = servicesOnDay.firstOrNull()?.id
        }
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
    fun setPlannedSeconds(serviceId: String, itemId: String, seconds: Int?) {
        val service = document.serviceById(serviceId) ?: return
        val next = if (seconds == null) {
            service.plannedSeconds - itemId
        } else {
            service.plannedSeconds + (itemId to seconds)
        }
        commit(document.withService(service.copy(plannedSeconds = next)))
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
                sections = existing.map { if (it.name == from) it.copy(name = trimmed) else it }
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
     * construction instead of by everyone remembering.
     */
    private fun commit(next: CalendarDocument) {
        document = next
        runCatching { store.save(next) }
    }
}
