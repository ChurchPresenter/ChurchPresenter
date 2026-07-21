package org.churchpresenter.app.churchpresenter.utils

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * [TrainingDataLogger] hand-builds its JSONL rather than serializing, so a missing comma or an
 * unescaped quote silently produces a corrupt line that only surfaces much later, during offline
 * analysis of a real service. These tests parse every emitted line as JSON instead of matching
 * substrings, which is what actually catches that class of bug.
 *
 * The logger resolves its directory from `user.home` in a `by lazy`, i.e. once per JVM — the test
 * task points the whole JVM at `build/test-home` (see build.gradle.kts), so this never touches a
 * real `~/.churchpresenter`. Each test uses a distinct sessionId, which gives it its own file.
 */
class TrainingDataLoggerTest {

    private val logDir = File(System.getProperty("user.home"), ".churchpresenter/bible-stt-logs")

    /**
     * `build/test-home` survives between Gradle runs, so without this the logger would append to
     * the previous run's files and every row-count assertion would drift upward. (It also skips
     * writing a session header into a file that already has content, which would break the
     * header assertions on the second run.)
     */
    @BeforeTest
    fun clearLogDir() {
        logDir.listFiles()?.forEach { it.delete() }
    }

    @AfterTest
    fun clearSession() {
        TrainingDataLogger.sessionId = null
    }

    private fun useSession(name: String): String {
        TrainingDataLogger.sessionId = name
        return name
    }

    /** All rows in the named file, parsed — fails loudly if any line is not valid JSON. */
    private fun rows(prefix: String, suffix: String): List<JsonObject> {
        val file = File(logDir, "$prefix$suffix.jsonl")
        assertTrue(file.exists(), "expected log file ${file.absolutePath}")
        return file.readLines().filter { it.isNotBlank() }.map {
            runCatching { Json.parseToJsonElement(it) as JsonObject }
                .getOrElse { e -> throw AssertionError("emitted invalid JSON: $it", e) }
        }
    }

    private fun JsonObject.str(key: String): String? =
        this[key]?.takeIf { it != JsonNull }?.jsonPrimitive?.content

    private fun JsonObject.isNull(key: String): Boolean = this[key] == JsonNull

    // ── live references ─────────────────────────────────────────────────────────

    @Test
    fun `a live reference row carries the whole reference and is valid JSON`() {
        val s = useSession("live-basic")
        TrainingDataLogger.logLiveReference(
            book = 43, chapter = 3, verseStart = 16, verseEnd = 17,
            source = "manual", segmentId = "seg-1", autoFollow = true, matchType = "explicit",
        )
        val all = rows("live-references-", s)
        val header = all.first()
        assertEquals("session", header.str("type"), "first line should be the one-time session header")

        val row = assertNotNull(all.getOrNull(1), "expected a data row after the header")
        assertEquals("43", row.str("book"))
        assertEquals("3", row.str("chapter"))
        assertEquals("16", row.str("verseStart"))
        assertEquals("17", row.str("verseEnd"))
        assertEquals("manual", row.str("source"))
        assertEquals("seg-1", row.str("segmentId"))
        assertEquals("true", row.str("autoFollow"))
        assertEquals("explicit", row.str("matchType"))
        assertEquals(s, row.str("sessionId"))
        assertTrue(row.str("ts_ms")!!.toLong() > 0)
    }

    @Test
    fun `absent optional fields are emitted as explicit nulls, not omitted`() {
        // Analysis joins on these keys, so a missing key and a null key are not equivalent.
        val s = useSession("live-nulls")
        TrainingDataLogger.logLiveReference(
            book = 1, chapter = 1, verseStart = null, verseEnd = null, source = "auto",
        )
        val row = rows("live-references-", s).last()
        for (key in listOf("verseStart", "verseEnd", "segmentId", "matchType")) {
            assertTrue(row.containsKey(key), "$key should be present")
            assertTrue(row.isNull(key), "$key should be null")
        }
    }

    @Test
    fun `each call appends one row`() {
        val s = useSession("live-append")
        repeat(3) { i ->
            TrainingDataLogger.logLiveReference(
                book = i + 1, chapter = 1, verseStart = 1, verseEnd = 1, source = "manual",
            )
        }
        val all = rows("live-references-", s)
        assertEquals(4, all.size, "1 session header + 3 rows")
        assertEquals(listOf("1", "2", "3"), all.drop(1).map { it.str("book") })
    }

    @Test
    fun `the session header is written once, not once per row`() {
        val s = useSession("live-one-header")
        repeat(3) {
            TrainingDataLogger.logLiveReference(1, 1, 1, 1, "manual")
        }
        val headers = rows("live-references-", s).count { it.str("type") == "session" }
        assertEquals(1, headers, "a re-attached file must not gain a second header")
    }

    // ── escaping and filename safety ────────────────────────────────────────────

    @Test
    fun `quotes backslashes and newlines are escaped rather than breaking the row`() {
        val s = useSession("live-escaping")
        TrainingDataLogger.logLiveReference(
            book = 1, chapter = 1, verseStart = 1, verseEnd = 1, source = "manual",
            // A raw quote or backslash here would produce unparseable JSON.
            segmentId = """seg "quoted" with \backslash and
newline""",
            matchType = "explicit",
        )
        val row = rows("live-references-", s).last() // parsing at all is the assertion
        val segment = assertNotNull(row.str("segmentId"))
        assertTrue(segment.contains("\"quoted\""), "quotes should survive round-trip: $segment")
        assertTrue(segment.contains("\\backslash"), "backslash should survive round-trip: $segment")
        assertTrue('\n' !in segment && '\r' !in segment, "newlines should be flattened to spaces")
    }

    @Test
    fun `an unsafe session id is sanitised into the filename`() {
        // Session ids come from STT (a db base name), so they can contain path separators.
        TrainingDataLogger.sessionId = "a/b:c*d?e session"
        TrainingDataLogger.logLiveReference(1, 1, 1, 1, "manual")

        val expected = File(logDir, "live-references-a_b_c_d_e_session.jsonl")
        assertTrue(expected.exists(), "expected sanitised filename ${expected.name}")
        // The row keeps the ORIGINAL id -- only the filename is sanitised.
        val row = expected.readLines().last().let { Json.parseToJsonElement(it) as JsonObject }
        assertEquals("a/b:c*d?e session", row.str("sessionId"))
    }

    @Test
    fun `with no session id the row records sessionId null`() {
        TrainingDataLogger.sessionId = null
        TrainingDataLogger.logLiveReference(1, 1, 1, 1, "manual")
        // Falls back to the process-start timestamp filename; find it by prefix.
        val file = assertNotNull(
            logDir.listFiles()?.filter { it.name.startsWith("live-references-") }
                ?.maxByOrNull { it.lastModified() },
            "expected a timestamp-named fallback file",
        )
        val row = file.readLines().last().let { Json.parseToJsonElement(it) as JsonObject }
        assertTrue(row.isNull("sessionId"))
    }

    // ── suggestion outcomes ─────────────────────────────────────────────────────

    @Test
    fun `a suggestion outcome records the suggestion, the action and the tier`() {
        val s = useSession("outcome-basic")
        TrainingDataLogger.logSuggestionOutcome(
            suggestedBook = 19, suggestedChapter = 23, suggestedVerse = 1,
            action = "accepted", matchType = "chapter-history",
        )
        val row = rows("suggestion-outcomes-", s).last()
        assertEquals("19", row.str("suggestedBook"))
        assertEquals("23", row.str("suggestedChapter"))
        assertEquals("1", row.str("suggestedVerse"))
        assertEquals("accepted", row.str("action"))
        assertEquals("chapter-history", row.str("matchType"))
        assertEquals(s, row.str("sessionId"))
    }

    @Test
    fun `a corrected outcome carries what actually went live`() {
        val s = useSession("outcome-corrected")
        TrainingDataLogger.logSuggestionOutcome(
            suggestedBook = 19, suggestedChapter = 23, suggestedVerse = 1,
            action = "corrected", correctedRef = "Psalm 23:4", matchType = "reverse",
        )
        val row = rows("suggestion-outcomes-", s).last()
        assertEquals("corrected", row.str("action"))
        assertEquals("Psalm 23:4", row.str("correctedRef"))
    }

    @Test
    fun `correctedRef is omitted entirely when absent`() {
        // Unlike the null-carrying fields, this one is genuinely optional in the schema.
        val s = useSession("outcome-nocorrection")
        TrainingDataLogger.logSuggestionOutcome(1, 1, 1, action = "ignored")
        val row = rows("suggestion-outcomes-", s).last()
        assertTrue("correctedRef" !in row)
        assertTrue(row.isNull("matchType"))
        assertTrue(row.isNull("suggestedVerse") || row.str("suggestedVerse") == "1")
    }

    // ── operator flags ──────────────────────────────────────────────────────────

    @Test
    fun `an operator flag records the flagged reference`() {
        val s = useSession("flag-basic")
        TrainingDataLogger.logOperatorFlag(
            kind = "wrong_passage", book = 40, chapter = 5, verseStart = 3, verseEnd = 4,
            segmentId = "seg-9", matchType = "reverse",
        )
        val row = rows("operator-flags-", s).last()
        assertEquals("wrong_passage", row.str("kind"))
        assertEquals("40", row.str("book"))
        assertEquals("5", row.str("chapter"))
        assertEquals("3", row.str("verseStart"))
        assertEquals("4", row.str("verseEnd"))
        assertEquals("seg-9", row.str("segmentId"))
        assertEquals("reverse", row.str("matchType"))
    }

    @Test
    fun `a missed-passage flag has no reference to anchor to and nulls every field`() {
        val s = useSession("flag-missed")
        TrainingDataLogger.logOperatorFlag(kind = "missed_passage")
        val row = rows("operator-flags-", s).last()
        assertEquals("missed_passage", row.str("kind"))
        for (key in listOf("book", "chapter", "verseStart", "verseEnd", "segmentId", "matchType")) {
            assertTrue(row.containsKey(key) && row.isNull(key), "$key should be present and null")
        }
    }

    // ── file separation ─────────────────────────────────────────────────────────

    @Test
    fun `the three log kinds go to three separate files`() {
        val s = useSession("three-kinds")
        TrainingDataLogger.logLiveReference(1, 1, 1, 1, "manual")
        TrainingDataLogger.logSuggestionOutcome(1, 1, 1, "accepted")
        TrainingDataLogger.logOperatorFlag("premature", book = 1, chapter = 1)

        assertEquals(1, rows("live-references-", s).count { it.str("type") != "session" })
        assertEquals(1, rows("suggestion-outcomes-", s).count { it.str("type") != "session" })
        assertEquals(1, rows("operator-flags-", s).count { it.str("type") != "session" })
    }
}
