package org.churchpresenter.calendar

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import org.churchpresenter.calendar.model.PresetDocument
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarStoreRecoveryTest {

    private val folder: File = Files.createTempDirectory("calendar-recover").toFile()
    private val other: File = Files.createTempDirectory("calendar-seed").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
        other.deleteRecursively()
    }

    private fun document(name: String) = CalendarDocument(
        services = listOf(PlannedService(id = "s", date = "2026-09-20", name = name, startTime = "10:00")),
    )

    private fun quarantined(): List<String> =
        folder.listFiles().orEmpty().map { it.name }.filter { ".corrupt-" in it }

    @Test
    fun `a backup that is unreadable too is set aside and the next one tried`() {
        val store = CalendarStore(folder)
        store.save(document("oldest"))
        store.save(document("middle"))
        store.save(document("newest"))
        store.file.writeText("broken")
        store.backupFile(1).writeText("also broken")

        val load = store.load()

        assertEquals(CalendarSource.RECOVERED, load.source)
        assertEquals("oldest", load.document.services.single().name)
        assertEquals(2, quarantined().size, "the file and the bad backup")
    }

    @Test
    fun `a backup that is missing is skipped rather than failing the load`() {
        val store = CalendarStore(folder)
        store.save(document("kept"))
        store.save(document("newer"))
        store.backupFile(1).renameTo(store.backupFile(2))
        store.file.writeText("broken")

        val load = store.load()

        assertEquals(CalendarSource.RECOVERED, load.source)
        assertEquals("kept", load.document.services.single().name)
    }

    @Test
    fun `a new folder is seeded with the calendar and the presets`() {
        CalendarStore(folder).save(document("mine"))
        PresetStore(folder).save(PresetDocument())
        val target = File(other, "shared")

        assertTrue(seedCalendarFolder(folder, target))

        assertEquals(CalendarSource.FILE, CalendarStore(target).load().source)
        assertTrue(File(target, PRESET_FILE).isFile)
    }

    @Test
    fun `a folder that already has a calendar keeps it`() {
        CalendarStore(folder).save(document("mine"))
        CalendarStore(other).save(document("theirs"))

        assertFalse(seedCalendarFolder(folder, other))

        assertEquals("theirs", CalendarStore(other).load().document.services.single().name)
    }

    @Test
    fun `only the file the folder lacks is copied`() {
        CalendarStore(folder).save(document("mine"))
        PresetStore(folder).save(PresetDocument())
        CalendarStore(other).save(document("theirs"))

        assertTrue(seedCalendarFolder(folder, other), "the presets were copied")

        assertEquals("theirs", CalendarStore(other).load().document.services.single().name)
        assertTrue(File(other, PRESET_FILE).isFile)
    }

    @Test
    fun `a folder seeded from itself is left alone`() {
        CalendarStore(folder).save(document("mine"))

        assertFalse(seedCalendarFolder(folder, folder))
    }

    @Test
    fun `with nothing to seed from nothing is created`() {
        val target = File(other, "never")

        assertFalse(seedCalendarFolder(folder, target))

        assertFalse(target.exists())
    }
}
