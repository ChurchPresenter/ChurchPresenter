package org.churchpresenter.calendar

import org.churchpresenter.calendar.model.CalendarDocument
import org.churchpresenter.calendar.model.PlannedService
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** `calendar.json`, its three backups, and what happens when it cannot be read. */
class CalendarStoreTest {

    private val folder: File = Files.createTempDirectory("calendar-store").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    private fun store() = CalendarStore(folder)

    private fun document(name: String) = CalendarDocument(
        services = listOf(
            PlannedService(id = "s", date = "2026-09-20", name = name, startTime = "10:00"),
        ),
    )

    @Test
    fun `an empty folder opens on a new calendar`() {
        val load = store().load()

        assertEquals(CalendarSource.NEW, load.source)
        assertTrue(load.document.services.isEmpty())
    }

    @Test
    fun `what is saved is what is read back`() {
        store().save(document("Sunday Morning"))

        val load = store().load()

        assertEquals(CalendarSource.FILE, load.source)
        assertEquals("Sunday Morning", load.document.services.single().name)
    }

    @Test
    fun `each save rotates the previous content into the backups`() {
        val store = store()
        store.save(document("first"))
        store.save(document("second"))
        store.save(document("third"))

        assertTrue(store.backupFile(1).readText().contains("second"), "the most recent previous")
        assertTrue(store.backupFile(2).readText().contains("first"))
    }

    @Test
    fun `an unreadable file is set aside and the newest backup opened`() {
        val store = store()
        store.save(document("good"))
        store.save(document("also good"))
        store.file.writeText("{ this is not json")

        val load = store.load()

        assertEquals(CalendarSource.RECOVERED, load.source)
        assertEquals("good", load.document.services.single().name, "the backup, not the wreck")
        assertTrue(
            folder.listFiles().orEmpty().any { it.name.startsWith("calendar.json.corrupt-") },
            "the unreadable file is kept to be looked at, not deleted",
        )
    }

    @Test
    fun `when nothing can be read it still opens`() {
        val store = store()
        store.file.writeText("nonsense")

        val load = store.load()

        assertEquals(CalendarSource.LOST, load.source)
        assertTrue(load.document.services.isEmpty())
    }

    @Test
    fun `a calendar written by a later version still opens`() {
        store().file.writeText("""{"version":99,"services":[],"somethingNew":true}""")

        assertEquals(CalendarSource.FILE, store().load().source)
    }
}
