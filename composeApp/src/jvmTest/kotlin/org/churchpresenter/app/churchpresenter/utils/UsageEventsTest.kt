package org.churchpresenter.app.churchpresenter.utils

import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UsageEventsTest {

    private lateinit var dir: File
    private lateinit var store: UsageEventStore

    private val event = UsageEvent.SONG_DUAL_LANGUAGE
    private fun file() = File(dir, "nested/usage-events.json")

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("cp-usage-test").toFile()
        store = UsageEventStore(::file)
    }

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `an install that has done nothing has nothing to report`() {
        assertTrue(store.unreported().isEmpty())
    }

    @Test
    fun `recorded events are reported once and then not again`() {
        repeat(3) { store.record(event) }
        assertEquals(mapOf(event to 3), store.unreported())

        store.markReported(store.unreported())
        assertTrue(store.unreported().isEmpty(), "delivered events must not be sent a second time")
    }

    @Test
    fun `only what happened after the last delivery is reported next time`() {
        repeat(2) { store.record(event) }
        store.markReported(store.unreported())

        repeat(5) { store.record(event) }
        assertEquals(mapOf(event to 5), store.unreported(), "the earlier 2 are not resent")
    }

    @Test
    fun `an undelivered ping leaves the events pending for the next launch`() {
        repeat(4) { store.record(event) }
        // markReported is simply never called, which is what a failed ping does.
        assertEquals(mapOf(event to 4), UsageEventStore(::file).unreported())
    }

    @Test
    fun `events recorded while a ping is in flight are not marked as delivered`() {
        repeat(2) { store.record(event) }
        val inFlight = store.unreported()

        // The service carries on while the request is out.
        repeat(3) { store.record(event) }
        store.markReported(inFlight)

        assertEquals(mapOf(event to 3), store.unreported(), "only the 2 that were actually sent are cleared")
    }

    @Test
    fun `counts survive a restart, since a new store reads the same file`() {
        store.record(event)
        assertEquals(mapOf(event to 1), UsageEventStore(::file).unreported())
    }

    @Test
    fun `the file holds counts and nothing about the occurrence`() {
        repeat(2) { store.record(event) }
        val text = file().readText().replace(" ", "")

        assertTrue("\"${event.name}\":2" in text, text)
        assertFalse("timestamp" in text.lowercase(), text)
    }

    @Test
    fun `a corrupt file reports nothing and repairs itself on the next record`() {
        file().parentFile?.mkdirs()
        file().writeText("not json")

        assertTrue(store.unreported().isEmpty(), "unreadable state must not throw at launch")
        store.record(event)
        assertEquals(mapOf(event to 1), store.unreported())
    }

    @Test
    fun `a store whose file cannot be written keeps working`() {
        val unwritable = UsageEventStore { File(dir, "blocked").apply { mkdirs() } }
        unwritable.record(event)
        assertTrue(unwritable.unreported().isEmpty(), "a failed write costs the count, never a crash")
    }

    @Test
    fun `a session length is kept until it is reported, then cleared`() {
        assertEquals(0, store.lastSessionMinutes(), "an install with no previous run reports nothing")

        store.recordSessionMinutes(95)
        assertEquals(95, UsageEventStore(::file).lastSessionMinutes(), "it must survive to the next launch")

        store.clearSessionMinutes()
        assertEquals(0, store.lastSessionMinutes(), "reported once, not on every launch after it")
    }

    @Test
    fun `a run shorter than a minute is not recorded as a session`() {
        store.recordSessionMinutes(0)
        assertEquals(0, store.lastSessionMinutes())
    }

    @Test
    fun `session length and event counts do not overwrite each other`() {
        store.record(event)
        store.recordSessionMinutes(42)

        val reopened = UsageEventStore(::file)
        assertEquals(42, reopened.lastSessionMinutes())
        assertEquals(mapOf(event to 1), reopened.unreported())
    }

    @Test
    fun `a milestone is recorded once for the life of the install`() {
        val milestone = UsageEvent.FIRST_LIVE_ON_SCREEN
        repeat(3) { store.recordOncePerInstall(milestone) }
        assertEquals(1, store.unreported()[milestone])

        store.markReported(store.unreported())
        UsageEventStore(::file).recordOncePerInstall(milestone)
        assertTrue(store.unreported().isEmpty(), "already reported once — a later run must not send it again")
    }

    @Test
    fun `the output-kind events keep the wire names the server counts`() {
        // All three audience output kinds are counted the same way, and the server adds them up by
        // these exact strings — renaming a constant must never quietly rename one of them.
        assertEquals("decklinkOutput", UsageEvent.DECKLINK_OUTPUT.param)
        assertEquals("ndiOutput", UsageEvent.NDI_OUTPUT.param)
        assertEquals("browserSourceOutput", UsageEvent.BROWSER_SOURCE_OUTPUT.param)
    }

    @Test
    fun `every event has its own wire name, and they are distinct`() {
        val params = UsageEvent.entries.map { it.param }
        assertTrue(params.all { it.isNotBlank() })
        assertEquals(params.size, params.toSet().size, "two events sharing a param would merge server-side")
    }
}
