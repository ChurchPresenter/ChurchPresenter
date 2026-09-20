package org.churchpresenter.calendar

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.churchpresenter.calendar.model.ItemPreset
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import org.churchpresenter.calendar.model.ServiceKind
import org.churchpresenter.calendar.model.ServiceRepeat
import org.churchpresenter.calendar.model.ServiceTemplate
import org.churchpresenter.core.models.schedule.CueAction
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The state against another machine writing the same folder, and the edges of its guards. */
class StateSyncTest {

    private val folder: File = Files.createTempDirectory("calendar-sync").toFile()
    private val songs: File = Files.createTempDirectory("calendar-sync-songs").toFile()
    private val today = LocalDate.of(2026, 9, 20)
    private var saves = 0
    private var clock = Instant.parse("2026-09-01T00:00:00Z")

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
        songs.deleteRecursively()
    }

    private fun state(songFolder: File? = null, presets: PresetStore? = null) = CalendarState(
        CalendarStore(folder),
        songFolder = songFolder,
        today = today,
        presetStore = presets,
        now = { clock.also { clock = clock.plusSeconds(1) } },
        onSaved = { saves++ },
    )

    private fun song(id: String) = ScheduleItem.SongItem(id, 1, "Song $id", "Hymns", "Hymns::1")

    private fun heading(id: String) =
        ScheduleItem.LabelItem(id = id, text = "Worship", textColor = "#FFFFFF", backgroundColor = "#5B9DF5")

    private fun cue(id: String) = ScheduleItem.CueItem(id = id, action = CueAction.BLANK, offsetMinutes = -5)

    private fun CalendarState.sunday(name: String = "Sunday Morning", on: LocalDate = today): PlannedService {
        select(on)
        return addService(name, "10:00", ServiceKind.SUNDAY, ServiceTemplate.Blank)
    }

    // ── Presets ─────────────────────────────────────────────────────────────────

    @Test
    fun `presets are listed newest first once the calendar has loaded`() = runTest {
        val store = PresetStore(folder)
        store.save(
            PresetDocument(
                presets = listOf(
                    ItemPreset("old", "Old", song("a"), savedAt = "2026-08-01T00:00:00Z"),
                    ItemPreset("new", "New", song("b"), savedAt = "2026-09-01T00:00:00Z"),
                )
            )
        )
        val state = state(presets = store)

        state.loadAsync(io = Dispatchers.Unconfined)

        assertEquals(listOf("new", "old"), state.presets.map { it.id })
    }

    @Test
    fun `a preset the other machine saved arrives on a merging reload`() = runTest {
        val store = PresetStore(folder, now = { clock })
        store.save(
            PresetDocument(presets = listOf(ItemPreset("mine", "Mine", song("a"), savedAt = "2026-08-01T00:00:00Z")))
        )
        val state = state(presets = store)
        state.reloadPresets(io = Dispatchers.Unconfined)
        PresetStore(folder).save(
            PresetDocument(
                presets = listOf(ItemPreset("theirs", "Theirs", song("b"), savedAt = "2026-08-02T00:00:00Z")),
            )
        )

        state.reloadPresetsMerging(io = Dispatchers.Unconfined)

        assertEquals(listOf("theirs", "mine"), state.presets.map { it.id })
        assertEquals(2, PresetStore(folder).load().presets.size, "the merge is written back for the other side")
    }

    @Test
    fun `a merging reload with no preset store does nothing`() = runTest {
        val state = state(presets = null)

        state.reloadPresetsMerging(io = Dispatchers.Unconfined)
        state.reloadPresets()

        assertTrue(state.presets.isEmpty())
    }

    // ── The song library ────────────────────────────────────────────────────────

    @Test
    fun `an edited song is written back to the library and the list re-read`() = runTest {
        val library = SongLibrary(songs)
        val original = library.writeNew(SongItem(number = "1", title = "Before", songbook = "Hymns"))
        val state = state(songFolder = songs)
        state.loadSongsAsync()

        state.saveSong(original, original.copy(title = "After"))

        assertEquals(listOf("After"), state.songs.map { it.title })
        assertEquals(listOf("After"), SongLibrary(songs).load().map { it.title })
    }

    @Test
    fun `the Bible books are taken once and not replaced`() {
        val state = state()
        state.loadBibleBooks(listOf(CalendarBibleBook(1, "Genesis", listOf(31))))

        state.loadBibleBooks(listOf(CalendarBibleBook(2, "Exodus", listOf(22))))

        assertEquals("Genesis", state.bibleBooks.single().name)
    }

    // ── Dates that do not parse ─────────────────────────────────────────────────

    @Test
    fun `a service whose date is unreadable is neither counted nor offered`() {
        val state = state()
        val service = state.sunday()
        state.updateService(service.copy(date = "someday"))

        assertTrue(state.servicesInVisibleMonth().isEmpty())
        state.select(today.plusDays(7))
        assertNull(state.mostRecentServiceBefore())
        assertTrue(state.templateOptions().none { it is ServiceTemplate.CopyOf })
    }

    // ── Deleting and copying ────────────────────────────────────────────────────

    @Test
    fun `deleting a service that is gone writes nothing`() {
        val state = state()
        state.sunday()
        saves = 0

        state.deleteService("nope")

        assertEquals(0, saves)
        assertEquals(1, state.document.services.size)
    }

    @Test
    fun `deleting a lone service as a series deletes just it`() {
        val state = state()
        val service = state.sunday()
        state.sunday("Evening")

        state.deleteService(service.id, wholeSeries = true)

        assertEquals(listOf("Evening"), state.document.services.map { it.name })
    }

    @Test
    fun `a copy to no dates is no copy`() {
        val state = state()
        val service = state.sunday()
        saves = 0

        state.copyService(service, emptyList(), includeRunOfShow = true, includeCues = true)

        assertEquals(0, saves)
    }

    @Test
    fun `a service already in a series is copied into that series`() {
        val state = state()
        val service = state.sunday()
        state.copyService(service, listOf(today.plusDays(7)), true, true, ServiceRepeat.WEEKLY)
        val inSeries = state.document.serviceById(service.id)!!
        val seriesId = inSeries.seriesId

        state.copyService(inSeries, listOf(today.plusDays(14)), true, true, ServiceRepeat.WEEKLY)

        assertEquals(3, state.document.servicesInSeries(seriesId).size)
        assertEquals(seriesId, state.document.serviceById(service.id)!!.seriesId, "not re-keyed")
    }

    @Test
    fun `a copy keeps the cues and drops the rest when asked`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(heading("h"), song("a"), cue("c")))

        state.copyService(
            state.document.serviceById(service.id)!!, listOf(today.plusDays(7)),
            includeRunOfShow = false, includeCues = true,
        )

        val copy = state.servicesOn(today.plusDays(7)).single()
        assertEquals(listOf(CueAction.BLANK), copy.items.map { (it as ScheduleItem.CueItem).action })
    }

    // ── Templates ───────────────────────────────────────────────────────────────

    @Test
    fun `a template needs a name`() {
        val state = state()
        val service = state.sunday()

        state.saveTemplate(service, "   ")

        assertTrue(state.document.templates.isEmpty())
    }

    @Test
    fun `a template can keep only the headings, only the items, or only the cues`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(heading("h"), song("a"), cue("c")))
        val full = state.document.serviceById(service.id)!!

        state.saveTemplate(full, "Skeleton", includeItems = false, includeCues = false)
        state.saveTemplate(full, "Set list", includeSections = false, includeCues = false)
        state.saveTemplate(full, "Automation", includeSections = false, includeItems = false)

        val byName = state.document.templates.associateBy { it.name }
        assertTrue(byName.getValue("Skeleton").items.single() is ScheduleItem.LabelItem)
        assertTrue(byName.getValue("Set list").items.single() is ScheduleItem.SongItem)
        assertTrue(byName.getValue("Automation").items.single() is ScheduleItem.CueItem)
    }

    @Test
    fun `saving a template again under the same name replaces it`() {
        val state = state()
        val service = state.sunday()
        state.saveTemplate(service, "Standard")
        state.addItems(service.id, listOf(song("a")))

        state.saveTemplate(state.document.serviceById(service.id)!!, "standard")

        assertEquals(1, state.document.templates.size)
        assertEquals(1, state.document.templates.single().items.size)
    }

    // ── Rows ────────────────────────────────────────────────────────────────────

    @Test
    fun `a service added without a template starts blank`() {
        val state = state()
        state.select(today)

        val added = state.addService("Plain", "09:00", ServiceKind.MIDWEEK)

        assertTrue(added.items.isEmpty())
        assertEquals(ServiceKind.MIDWEEK.id, added.kind)
        assertTrue(added.armed, "armed by default, as the preference says")
    }

    @Test
    fun `deleting another service leaves the selection where it was`() {
        val state = state()
        val morning = state.sunday("Morning")
        val evening = state.sunday("Evening")
        state.selectService(morning.id)

        state.deleteService(evening.id)

        assertEquals(morning.id, state.selectedServiceId)
    }

    @Test
    fun `a move from a negative index does nothing`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a"), song("b")))

        state.moveItem(service.id, from = -1, to = 1)

        assertEquals(listOf("a", "b"), state.document.serviceById(service.id)!!.items.map { it.id })
    }

    @Test
    fun `a move from outside the list does nothing`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a"), song("b")))
        saves = 0

        state.moveItem(service.id, from = 5, to = 0)

        assertEquals(0, saves)
        assertEquals(listOf("a", "b"), state.document.serviceById(service.id)!!.items.map { it.id })
    }

    @Test
    fun `a length typed on a plain row is an estimate and leaves the row alone`() {
        val state = state()
        val service = state.sunday()
        state.addItems(service.id, listOf(song("a")))

        state.setPlannedSeconds(service.id, "a", 200)

        val after = state.document.serviceById(service.id)!!
        assertEquals(200, after.plannedSeconds["a"])
        assertEquals(song("a"), after.items.single())
    }

    @Test
    fun `renaming a section to a different case of its own name is allowed`() {
        val state = state()

        state.renameSection("Worship", "WORSHIP")

        assertTrue(state.document.preferences.sections.any { it.name == "WORSHIP" })
        assertFalse(state.document.preferences.sections.any { it.name == "Worship" })
    }

    // ── The other machine's calendar ────────────────────────────────────────────

    @Test
    fun `reloading an unchanged file changes and writes nothing`() = runTest {
        val state = state()
        state.sunday()
        val before = state.document
        saves = 0

        state.reloadMerging(io = Dispatchers.Unconfined)

        assertEquals(before, state.document)
        assertEquals(0, saves)
    }

    @Test
    fun `a service the other machine added arrives and the merge is written back`() = runTest {
        val state = state()
        state.sunday("Mine")
        val theirs = CalendarStore(folder).load().document.let { onDisk ->
            onDisk.withService(
                PlannedService(id = "theirs", date = today.plusDays(3).toString(), name = "Theirs", startTime = "19:00")
            )
        }
        // The other side never saw ours: what it wrote holds only its own service.
        CalendarStore(folder).save(theirs.copy(services = theirs.services.filter { it.id == "theirs" }))
        saves = 0

        state.reloadMerging(io = Dispatchers.Unconfined)

        assertEquals(setOf("Mine", "Theirs"), state.document.services.map { it.name }.toSet())
        assertEquals(1, saves, "the file lacked ours")
        assertEquals(2, CalendarStore(folder).load().document.services.size)
    }

    @Test
    fun `a file that already holds the merge is not written again`() = runTest {
        val state = state()
        state.sunday("Mine")
        val onDisk = CalendarStore(folder).load().document
        CalendarStore(folder).save(
            onDisk.withService(
                PlannedService(id = "theirs", date = today.toString(), name = "Theirs", startTime = "19:00"),
            )
        )
        saves = 0

        state.reloadMerging()

        assertEquals(2, state.document.services.size)
        assertEquals(0, saves)
    }

    @Test
    fun `a selected service deleted elsewhere hands the selection to the day's next`() = runTest {
        val state = state()
        val morning = state.sunday("Morning")
        val evening = state.sunday("Evening")
        state.selectService(morning.id)
        val onDisk = CalendarStore(folder).load().document
        CalendarStore(folder).save(onDisk.withoutService(morning.id, clock.plusSeconds(60)))

        state.reloadMerging(io = Dispatchers.Unconfined)

        assertNull(state.document.serviceById(morning.id))
        assertEquals(evening.id, state.selectedServiceId)
    }

    @Test
    fun `a recovered calendar's note is cleared once acknowledged`() {
        val state = state()
        state.sunday()
        val before = state.source

        state.acknowledgeSource()

        assertEquals(CalendarSource.FILE, state.source)
        assertNotEquals(CalendarSource.RECOVERED, before)
    }
}
