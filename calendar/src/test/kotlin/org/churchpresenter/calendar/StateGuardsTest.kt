package org.churchpresenter.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.SectionStyle
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.RowTiming
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the state does when it is asked for something that is not there.
 *
 * Every mutation takes a service id, and the window holds one across reloads — a service deleted in
 * another copy of the app, or a run of show edited while a dialog was open, leaves the id pointing
 * at nothing. The rule is that it changes nothing and writes nothing; a guard that returns the
 * document unchanged and a guard that saves an empty one are indistinguishable until this is run.
 */
class StateGuardsTest {

    private val folder: File = Files.createTempDirectory("calendar-guards").toFile()
    private val today = LocalDate.of(2026, 9, 20)

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun state(songFolder: File? = null, presets: PresetStore? = null) =
        CalendarState(CalendarStore(folder), songFolder = songFolder, today = today, presetStore = presets)

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song", "Hymns", "Hymns::1")

    private fun CalendarState.sunday(): PlannedService {
        select(today)
        addService("Sunday Morning", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
        return document.services.first()
    }

    @Test
    fun `every mutation of a service that is gone leaves the document alone`() {
        val state = state()
        val service = state.sunday()
        state.deleteService(service.id)
        val before = state.document

        state.layOutTimes(service.id)
        state.setArmed(service.id, true)
        state.setCueEnabled(service.id, "cue", false)
        state.addItems(service.id, listOf(song("x")))
        state.replaceItem(service.id, "x", listOf(song("y")))
        state.removeItem(service.id, "x")
        state.moveItem(service.id, 0, 1)
        state.setPlannedSeconds(service.id, "x", 60)
        state.setTiming(service.id, "x", RowTiming(startAt = "10:00"))

        assertEquals(before, state.document)
        assertTrue(CalendarStore(folder).load().document.services.isEmpty())
    }

    @Test
    fun `saving a service the document has lost puts it back as it stands`() {
        val state = state()
        val service = state.sunday()
        state.deleteService(service.id)

        state.updateService(service.copy(name = "Renamed"))

        // There is no old start time to move its pinned rows from, so it is stored as given --
        // which is what makes the edit sheet's Save the one path, open on a service or not.
        assertEquals("Renamed", state.document.services.single().name)
    }

    @Test
    fun `a row that is not in the service is not replaced or moved`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))
        val before = state.document

        state.replaceItem(service.id, "missing", listOf(song("b")))
        state.moveItem(service.id, 7, 0)

        assertEquals(before, state.document)
    }

    @Test
    fun `a cue that is not in the service is not ticked`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(ScheduleItem.CueItem(id = "c", action = CueAction.BLANK)))
        val before = state.document

        state.setCueEnabled(service.id, "other", false)

        assertEquals(before, state.document)
    }

    @Test
    fun `an estimate can be set and cleared`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))

        state.setPlannedSeconds(service.id, "a", 300)
        assertEquals(300, state.document.services.first().plannedSeconds["a"])

        state.setPlannedSeconds(service.id, "a", null)
        assertNull(state.document.services.first().plannedSeconds["a"])
    }

    @Test
    fun `typing a length on a duration timer sets the timer itself`() {
        val state = state()
        val service = state.sunday()
        val timer = ScheduleItem.AnnouncementItem(id = "t", text = "", isTimer = true, timerMinutes = 5)
        state.addItems(service.id, listOf(timer))

        state.setPlannedSeconds(service.id, "t", 630)

        val saved = state.document.services.first().items.first() as ScheduleItem.AnnouncementItem
        assertEquals(10, saved.timerMinutes, "ten minutes and a half")
        assertEquals(30, saved.timerSeconds)
    }

    @Test
    fun `a default timing is dropped rather than stored`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))

        state.setTiming(service.id, "a", RowTiming(startAt = "10:00"))
        assertEquals(1, state.document.services.first().timing.size)

        state.setTiming(service.id, "a", RowTiming())
        assertTrue(state.document.services.first().timing.isEmpty(), "nothing to say is not stored")
    }

    @Test
    fun `rows are added where they are asked for, however far out the index is`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a"), song("b")))

        state.addItems(service.id, listOf(song("c")), at = 0)
        state.addItems(service.id, listOf(song("d")), at = 99)

        assertEquals(listOf("c", "a", "b", "d"), state.document.services.first().items.map { it.id })
    }

    @Test
    fun `a move past the end lands at the end`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a"), song("b"), song("c")))

        state.moveItem(service.id, 0, 99)

        assertEquals(listOf("b", "c", "a"), state.document.services.first().items.map { it.id })
    }

    // ── Sections ────────────────────────────────────────────────────────────────

    @Test
    fun `a section is not added twice, whatever its case`() {
        val state = state()
        val before = state.document.preferences.sections.size

        state.addSection("Baptism", "#111111")
        state.addSection("baptism", "#222222")
        state.addSection("   ", "#333333")

        assertEquals(before + 1, state.document.preferences.sections.size)
    }

    @Test
    fun `a rename onto an existing name is refused`() {
        val state = state()
        state.updatePreferences(
            state.document.preferences.copy(
                sections = listOf(SectionStyle("Baptism", "#111111"), SectionStyle("Word", "#222222")),
            )
        )

        state.renameSection("Baptism", "Word")
        state.renameSection("Baptism", "  ")

        assertEquals(listOf("Baptism", "Word"), state.document.preferences.sections.map { it.name })
    }

    @Test
    fun `a section can be renamed, recolored and removed`() {
        val state = state()
        state.addSection("Baptism", "#111111")

        state.renameSection("Baptism", "Praise")
        state.setSectionColor("Praise", "#00FF00")
        assertEquals(
            SectionStyle("Praise", "#00FF00"),
            state.document.preferences.sections.first { it.name == "Praise" },
        )

        state.removeSection("Praise")
        assertTrue(state.document.preferences.sections.none { it.name == "Praise" })
    }

    // ── What the window offers ──────────────────────────────────────────────────

    @Test
    fun `selecting a day with nothing on it opens no service`() {
        val state = state()
        state.sunday()

        state.select(today.plusDays(1))

        assertNull(state.selectedServiceId)
        assertEquals(today.plusDays(1), state.selectedDate)
    }

    @Test
    fun `selecting a date in another month brings that month into view`() {
        val state = state()

        state.select(today.plusMonths(2))

        assertEquals(java.time.YearMonth.from(today.plusMonths(2)), state.visibleMonth)
    }

    @Test
    fun `the visible month is counted, not the whole year`() {
        val state = state()
        state.sunday()
        state.select(today.plusMonths(1))
        state.addService("Next month", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)

        state.showMonth(java.time.YearMonth.from(today))
        assertEquals(1, state.servicesInVisibleMonth().size)

        state.showNextMonth()
        assertEquals(1, state.servicesInVisibleMonth().size)

        state.showPreviousMonth()
        state.goToToday()
        assertEquals(today, state.selectedDate)
    }

    @Test
    fun `the most recent service is the last one before the day being planned`() {
        val state = state()
        state.select(today.minusDays(14))
        state.addService("Two weeks ago", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
        state.select(today.minusDays(7))
        state.addService("Last week", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
        state.select(today.plusDays(7))
        state.addService("Next week", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)

        state.select(today)

        assertEquals("Last week", state.mostRecentServiceBefore()?.name)
    }

    @Test
    fun `a day with no service before it has nothing to copy`() {
        val state = state()
        state.select(today)

        assertNull(state.mostRecentServiceBefore())
        assertEquals(listOf(ServiceTemplate.Blank), state.templateOptions())
    }

    @Test
    fun `what a new service can start from is blank, the saved templates and each kind's last`() {
        val state = state()
        state.select(today.minusDays(7))
        val last = state.document.let { _ ->
            state.addService("Last week", "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
            state.document.services.first()
        }
        state.saveTemplate(last, "Sunday Morning")
        state.select(today)

        val options = state.templateOptions()

        assertEquals(ServiceTemplate.Blank, options.first())
        assertTrue(options.any { it is ServiceTemplate.Saved }, "the template just saved")
        assertTrue(options.any { it is ServiceTemplate.CopyOf }, "and last Sunday itself")
    }

    // ── The song library and the presets beside it ──────────────────────────────

    @Test
    fun `songbooks are listed once each, in order, and blanks are not songbooks`() = runTest {
        val songs = Files.createTempDirectory("calendar-guard-songs").toFile()
        try {
            val library = SongLibrary(songs)
            library.writeNew(SongItem(number = "1", title = "A", songbook = "Hymns"))
            library.writeNew(SongItem(number = "2", title = "B", songbook = "Anthems"))
            library.writeNew(SongItem(number = "3", title = "C", songbook = ""))
            val state = state(songFolder = songs)

            state.loadSongsAsync(io = Dispatchers.Unconfined)

            assertTrue(state.songsLoaded)
            assertEquals(listOf("Anthems", "Hymns"), state.songbooks())
        } finally {
            songs.deleteRecursively()
        }
    }

    @Test
    fun `with no song library there is nothing to load and the picker is told so`() = runTest {
        val state = state(songFolder = null)

        state.loadSongsAsync(io = Dispatchers.Unconfined)

        assertTrue(state.songsLoaded, "the picker draws \"no songs\", not a spinner for ever")
        assertTrue(state.songs.isEmpty())
    }

    @Test
    fun `saving a song without a library folder does nothing rather than throwing`() = runTest {
        val state = state(songFolder = null)
        val song = SongItem(number = "1", title = "A", songbook = "Hymns")

        state.saveSong(song, song.copy(title = "B"), io = Dispatchers.Unconfined)

        assertTrue(state.songs.isEmpty())
    }

    @Test
    fun `presets cannot be deleted without a store to delete them from`() {
        val state = state(presets = null)

        state.deletePreset("p1")

        assertTrue(state.presets.isEmpty())
    }

    @Test
    fun `a preset is removed from the store it came from`() = runTest {
        val store = PresetStore(folder)
        store.save(
            PresetDocument(
                presets = listOf(
                    ItemPreset(id = "p1", name = "Countdown", item = song("t")),
                    ItemPreset(id = "p2", name = "Clip", item = song("m")),
                )
            )
        )
        val state = state(presets = store)
        state.reloadPresets(io = Dispatchers.Unconfined)

        state.deletePreset("p1")

        assertEquals(listOf("p2"), state.presets.map { it.id })
        assertEquals(listOf("p2"), PresetStore(folder).load().presets.map { it.id })
    }

    @Test
    fun `the Bible books the app loaded are what the picker browses`() {
        val state = state()

        state.loadBibleBooks(listOf(CalendarBibleBook(bookId = 1, name = "Genesis", verseCounts = listOf(31))))

        assertEquals("Genesis", state.bibleBooks.single().name)
    }

    @Test
    fun `a service opened by id stays opened`() {
        val state = state()
        val service = state.sunday()

        state.selectService(service.id)

        assertEquals(service.id, state.selectedServiceId)
        assertNotNull(state.selectedService)
        assertTrue(state.hasServices(today))
        assertEquals(1, state.servicesOn(today).size)
        assertEquals(1, state.servicesOnSelectedDate.size)
    }
}
