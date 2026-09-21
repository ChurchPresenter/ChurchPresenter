package org.churchpresenter.calendar

import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardWatchEventKinds
import java.nio.file.WatchEvent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CalendarFileWatcherTest {

    private val folder: File = Files.createTempDirectory("calendar-watch").toFile()

    @AfterTest
    fun cleanUp() {
        folder.deleteRecursively()
    }

    /** One event as the watch service would deliver it, by file name alone. */
    private class Event(
        private val name: String?,
        private val kind: WatchEvent.Kind<*> = StandardWatchEventKinds.ENTRY_MODIFY,
    ) : WatchEvent<Any?> {
        @Suppress("UNCHECKED_CAST")
        override fun kind(): WatchEvent.Kind<Any?> = kind as WatchEvent.Kind<Any?>
        override fun count(): Int = 1
        override fun context(): Any? = name?.let { File(it).toPath() }
    }

    private class Heard {
        var calendar = 0
        var presets = 0
    }

    private suspend fun CalendarFileWatcher.hear(vararg events: WatchEvent<*>): Heard {
        val heard = Heard()
        report(events.toList(), onChanged = { heard.calendar++ }, onPresetsChanged = { heard.presets++ })
        return heard
    }

    @Test
    fun `a folder that is not there is watched by doing nothing`() = runTest {
        var heard = false

        CalendarFileWatcher(File(folder, "missing")).run(onChanged = { heard = true })

        assertFalse(heard)
    }

    @Test
    fun `the watch stops when the window closes`() = runTest {
        val job = launch { CalendarFileWatcher(folder).run(onChanged = {}) }
        yield()

        job.cancelAndJoin()

        assertTrue(job.isCancelled)
    }

    @Test
    fun `a change to the calendar file is reported`() = runTest {
        File(folder, CALENDAR_FILE).writeText("{}")

        val heard = CalendarFileWatcher(folder).hear(Event(CALENDAR_FILE))

        assertEquals(1, heard.calendar)
        assertEquals(0, heard.presets)
    }

    @Test
    fun `a change to the presets file goes to its own callback`() = runTest {
        File(folder, PRESET_FILE).writeText("{}")

        val heard = CalendarFileWatcher(folder).hear(Event(PRESET_FILE))

        assertEquals(0, heard.calendar)
        assertEquals(1, heard.presets)
    }

    @Test
    fun `this window's own save is not read back as somebody else's`() = runTest {
        val watcher = CalendarFileWatcher(folder)
        File(folder, CALENDAR_FILE).writeText("{}")
        watcher.savedHere()

        assertEquals(0, watcher.hear(Event(CALENDAR_FILE)).calendar, "what we just wrote")

        File(folder, CALENDAR_FILE).writeText("""{"services":[]}""")

        assertEquals(1, watcher.hear(Event(CALENDAR_FILE)).calendar, "what somebody else wrote")
    }

    @Test
    fun `the presets file remembers its own save the same way`() = runTest {
        val watcher = CalendarFileWatcher(folder)
        File(folder, PRESET_FILE).writeText("{}")
        watcher.savedPresetsHere()

        assertEquals(0, watcher.hear(Event(PRESET_FILE)).presets)
    }

    @Test
    fun `what a sync client writes on the way in counts as the file`() = runTest {
        File(folder, CALENDAR_FILE).writeText("{}")

        assertEquals(1, CalendarFileWatcher(folder).hear(Event("$CALENDAR_FILE.sync-conflict")).calendar)
        assertEquals(1, CalendarFileWatcher(folder).hear(Event("incoming.tmp")).calendar)
    }

    @Test
    fun `an overflow is taken as a change to both files`() = runTest {
        File(folder, CALENDAR_FILE).writeText("{}")
        File(folder, PRESET_FILE).writeText("{}")

        val heard = CalendarFileWatcher(folder).hear(Event(null, StandardWatchEventKinds.OVERFLOW))

        assertEquals(1, heard.calendar)
        assertEquals(1, heard.presets)
    }

    @Test
    fun `the backups and anything else in the folder are not changes`() = runTest {
        val heard = CalendarFileWatcher(folder).hear(
            Event("$CALENDAR_FILE.bak1"),
            Event("$CALENDAR_FILE.corrupt-20260920"),
            Event("notes.txt"),
            Event(null),
        )

        assertEquals(0, heard.calendar)
        assertEquals(0, heard.presets)
    }
}
