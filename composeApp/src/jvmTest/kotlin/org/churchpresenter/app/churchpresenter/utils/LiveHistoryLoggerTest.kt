package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.churchpresenter.app.churchpresenter.TestSingletons
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [LiveHistoryLogger] is what SongListener reads to learn what was on screen during a service, so a
 * corrupt line, a repeated line or a song row missing from a lyric line all turn into a wrongly cut
 * recording much later. Like [TrainingDataLoggerTest], every line is parsed as JSON rather than
 * matched as a substring.
 *
 * The logger keeps one piece of state across calls — the song row last reported — so every test
 * names songs of its own, and each uses its own session id, which gives it its own file.
 */
class LiveHistoryLoggerTest {

    private val logDir get() = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs")

    @BeforeTest
    fun clearLogDir() {
        TestSingletons.latchToTestHome()
        logDir.listFiles()?.filter { it.name.startsWith(LiveHistoryLogger.PREFIX) }?.forEach { it.delete() }
    }

    @AfterTest
    fun clearSession() {
        TrainingDataLogger.sessionId = null
    }

    private fun useSession(name: String): String {
        TrainingDataLogger.sessionId = name
        return name
    }

    private fun rows(file: File): List<JsonObject> {
        assertTrue(file.exists(), "expected log file ${file.absolutePath}")
        return file.readLines().filter { it.isNotBlank() }.map {
            runCatching { Json.parseToJsonElement(it) as JsonObject }
                .getOrElse { e -> throw AssertionError("emitted invalid JSON: $it", e) }
        }
    }

    private fun rows(session: String): List<JsonObject> =
        rows(File(logDir, "${LiveHistoryLogger.PREFIX}$session.jsonl"))

    /** The rows after the session header. */
    private fun changes(session: String) = rows(session).drop(1)

    private fun JsonObject.str(key: String): String? =
        this[key]?.takeIf { it != JsonNull }?.jsonPrimitive?.content

    private fun lyrics(title: String, number: Int, section: Int = 0, line: Int = 0) = LiveHistoryEntry(
        contentType = "LYRICS",
        songNumber = number,
        songTitle = title,
        sectionIndex = section,
        sectionType = "verse",
        lineIndex = line,
    )

    // ── File format ─────────────────────────────────────────────────────────────

    @Test
    fun `each change is one JSON line and a repeat of the last one writes nothing`() {
        val s = useSession("history-dedupe")
        val verse = LiveHistoryEntry("BIBLE", verseCode = "B043C003V016", reference = "John 3:16")

        LiveHistoryLogger.logLiveState(verse)
        LiveHistoryLogger.logLiveState(verse)
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("NONE"))
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("NONE"))

        val written = changes(s)
        assertEquals(listOf("BIBLE", "NONE"), written.map { it.str("contentType") })
        assertEquals("B043C003V016", written[0].str("verseCode"))
        assertEquals("John 3:16", written[0].str("reference"))
        assertTrue(written.all { it.str("sessionId") == s && it["ts_ms"] != null })
    }

    @Test
    fun `fields that do not apply are left out of the line`() {
        val s = useSession("history-sparse")

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("PICTURES", fileName = "welcome.png"))

        val line = changes(s).single()
        assertEquals(setOf("ts_ms", "sessionId", "contentType", "fileName"), line.keys)
    }

    @Test
    fun `quotes, backslashes and control characters cannot break a line`() {
        val s = useSession("history-escaping")

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("MEDIA", fileName = "a \"b\"\\c\td\ne.mp4"))

        assertEquals("a \"b\"\\c d e.mp4", changes(s).single().str("fileName"))
    }

    // ── File naming and the header ──────────────────────────────────────────────

    @Test
    fun `a session's file is named after it and opens with one header`() {
        val s = useSession("history-header")

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("BIBLE", reference = "Genesis 1:1"))
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("BIBLE", reference = "Genesis 1:2"))
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("NONE"))

        val all = rows(s)
        assertEquals(1, all.count { it.str("type") == "session" }, "one header, not one per row")
        assertEquals("session", all.first().str("type"))
        assertEquals(s, all.first().str("sessionId"))
        assertEquals(4, all.size)
    }

    @Test
    fun `without a session id the file is named after the app's start`() {
        TrainingDataLogger.sessionId = null

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("WEBSITE"))

        val file = logDir.listFiles().orEmpty()
            .singleOrNull { Regex("""live-content-\d{4}-\d\d-\d\d_\d\d-\d\d-\d\d\.jsonl""").matches(it.name) }
        assertNotNull(file, "expected a timestamp-named fallback file")
        val all = rows(file)
        assertEquals(JsonNull, all.first()["sessionId"])
        assertEquals("WEBSITE", all.last().str("contentType"))
    }

    @Test
    fun `a new session starts its own file even with the same thing on screen`() {
        val first = useSession("history-session-a")
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("CANVAS"))
        val second = useSession("history-session-b")
        LiveHistoryLogger.logLiveState(LiveHistoryEntry("CANVAS"))

        assertEquals(listOf("CANVAS"), changes(first).map { it.str("contentType") })
        assertEquals(listOf("CANVAS"), changes(second).map { it.str("contentType") })
    }

    @Test
    fun `an unsafe session id is sanitised into the file name`() {
        useSession("2026/09/30 service")

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("NONE"))

        assertTrue(File(logDir, "live-content-2026_09_30_service.jsonl").exists())
    }

    // ── Song identity ────────────────────────────────────────────────────────────

    @Test
    fun `lyric lines carry the reported song row while its title and number match`() {
        val s = useSession("history-stamp")
        LiveHistoryLogger.noteLiveSong("Hymnal::7", "Hymnal", 7, "Stamp Seven", "schedule")

        LiveHistoryLogger.logLiveState(lyrics("Stamp Seven", 7, section = 0))
        LiveHistoryLogger.logLiveState(lyrics("Stamp Seven", 7, section = 1))
        LiveHistoryLogger.logLiveState(lyrics("Another Song", 8))

        val (first, second, other) = changes(s)
        assertEquals("Hymnal::7", first.str("songId"))
        assertEquals("Hymnal", first.str("songbook"))
        assertEquals("schedule", first.str("source"), "the go-live is where the source belongs")
        assertEquals("Hymnal::7", second.str("songId"))
        assertNull(second.str("source"), "a later section is not that go-live again")
        assertNull(other.str("songId"), "a song nobody reported has no row to name")
        assertEquals("Another Song", other.str("songTitle"))
        assertEquals(8, other.str("songNumber")?.toInt())
    }

    @Test
    fun `a section shown before its row was reported is written again with the row`() {
        val s = useSession("history-late-note")

        LiveHistoryLogger.logLiveState(lyrics("Late Note", 3))
        LiveHistoryLogger.noteLiveSong("Chorus Book::3", "Chorus Book", 3, "Late Note", "manual")
        LiveHistoryLogger.noteLiveSong("Chorus Book::3", "Chorus Book", 3, "Late Note", "manual")

        val written = changes(s)
        assertEquals(listOf(null, "Chorus Book::3"), written.map { it.str("songId") })
        assertEquals("manual", written[1].str("source"))
    }

    @Test
    fun `reporting a song that is not the one on screen writes nothing`() {
        val s = useSession("history-unrelated-note")

        LiveHistoryLogger.logLiveState(LiveHistoryEntry("BIBLE", reference = "Psalms 23:1"))
        LiveHistoryLogger.noteLiveSong("Hymnal::9", "Hymnal", 9, "Not Yet Live", "manual")

        assertEquals(1, changes(s).size)
        assertFalse(changes(s).any { it.str("songId") != null })
    }
}
