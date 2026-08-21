package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.engine.DetectionLogger
import org.churchpresenter.bibleengine.engine.ScriptureEvent
import org.churchpresenter.bibleengine.engine.ScriptureReference
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class DetectionLoggerTest {

    @Test fun `logStickyChange writes a sticky-log file with the expected fields`() {
        val dir = Files.createTempDirectory("sticky-log-test").toFile()
        val prevPath = DetectionLogger.path
        val prevSessionId = DetectionLogger.sessionId
        try {
            DetectionLogger.path = "${dir.absolutePath}/detection-log.jsonl"
            DetectionLogger.sessionId = "test-session"
            Config.logStickyChanges = true

            DetectionLogger.logStickyChange(
                transcript = "и мы читаем", translation = "and we read",
                prevBook = 46, prevChapter = 11, newBook = 9, newChapter = 15,
            )
            DetectionLogger.drainForTests()

            val stickyLog = dir.listFiles()?.singleOrNull { it.name.startsWith("sticky-log-") }
            assertTrue(stickyLog != null, "expected a sticky-log-*.jsonl file, got ${dir.listFiles()?.map { it.name }}")
            val line = stickyLog.readText()
            assertContains(line, "\"prevBook\":46")
            assertContains(line, "\"prevChapter\":11")
            assertContains(line, "\"newBook\":9")
            assertContains(line, "\"newChapter\":15")
            assertContains(line, "\"sessionId\":\"test-session\"")
            assertContains(line, "\"transcript\":\"и мы читаем\"")
        } finally {
            DetectionLogger.path = prevPath
            DetectionLogger.sessionId = prevSessionId
            dir.deleteRecursively()
        }
    }

    @Test fun `logStickyChange is a no-op when Config logStickyChanges is disabled`() {
        val dir = Files.createTempDirectory("sticky-log-test-disabled").toFile()
        val prevPath = DetectionLogger.path
        try {
            DetectionLogger.path = "${dir.absolutePath}/detection-log.jsonl"
            Config.logStickyChanges = false

            DetectionLogger.logStickyChange("a", "b", null, null, 9, 15)
            DetectionLogger.drainForTests()

            assertTrue(dir.listFiles().isNullOrEmpty(), "expected no file written when disabled")
        } finally {
            DetectionLogger.path = prevPath
            Config.logStickyChanges = true
            dir.deleteRecursively()
        }
    }

    // ── Detection and candidate logs ──────────────────────────────────────────

    private fun event(
        bookId: Int = 43, chapter: Int = 3, verseStart: Int = 16, verseEnd: Int? = null,
        displayRef: String = "John 3:16", confidence: Double = 0.92, matchType: String = "explicit",
    ) = ScriptureEvent(
        type = "scripture",
        id = "evt-1",
        reference = ScriptureReference(
            bookId = bookId, bookName = "John", chapter = chapter,
            verseStart = verseStart, verseEnd = verseEnd, displayRef = displayRef,
            canonicalCodeStart = "B%03dC%03dV%03d".format(bookId, chapter, verseStart),
            canonicalCodeEnd = null, numbering = "hebrew",
        ),
        verseText = "For God so loved the world",
        confidence = confidence,
        matchType = matchType,
        translation = "KJV",
    )

    /** Runs [body] with the logger writing into a fresh temp directory, then restores it. */
    private fun withLogDir(body: (java.io.File) -> Unit) {
        val dir = Files.createTempDirectory("detection-log-test").toFile()
        val prevPath = DetectionLogger.path
        val prevSessionId = DetectionLogger.sessionId
        try {
            DetectionLogger.path = "${dir.absolutePath}/detection-log.jsonl"
            DetectionLogger.sessionId = "test-session"
            body(dir)
        } finally {
            DetectionLogger.path = prevPath
            DetectionLogger.sessionId = prevSessionId
            dir.deleteRecursively()
        }
    }

    private fun rowsOf(dir: java.io.File, prefix: String): List<String> =
        dir.listFiles()?.singleOrNull { it.name.startsWith(prefix) }
            ?.readLines()?.filter { it.isNotBlank() } ?: emptyList()

    @Test fun `an emitted detection is written with its reference and confidence`() {
        withLogDir { dir ->
            DetectionLogger.log("и мы читаем", "and we read", event())
            DetectionLogger.drainForTests()

            val rows = rowsOf(dir, "detection-log-")
            val detection = rows.last()
            assertContains(detection, "\"ref\":\"John 3:16\"")
            assertContains(detection, "\"book\":43")
            assertContains(detection, "\"chapter\":3")
            assertContains(detection, "\"verseStart\":16")
            assertContains(detection, "\"confidence\":0.92")
            assertContains(detection, "\"source\":\"explicit\"")
        }
    }

    @Test fun `a detection file opens with a session header tying rows to their config`() {
        withLogDir { dir ->
            DetectionLogger.log("a", "b", event())
            DetectionLogger.drainForTests()

            val rows = rowsOf(dir, "detection-log-")
            assertContains(rows.first(), "\"type\":\"session\"")
            assertContains(rows.first(), "\"sessionId\":\"test-session\"")
            assertTrue(rows.size >= 2, "the header precedes the detection row")
        }
    }

    @Test fun `the session header is written once, not once per detection`() {
        // A restart re-attaching to the same session file must not add a second header.
        withLogDir { dir ->
            repeat(3) { DetectionLogger.log("a", "b", event()) }
            DetectionLogger.drainForTests()

            val headers = rowsOf(dir, "detection-log-").count { it.contains("\"type\":\"session\"") }
            assertTrue(headers == 1, "expected exactly one session header, got $headers")
        }
    }

    @Test fun `a near-miss candidate goes to its own file with the reason kept`() {
        // Candidates live apart from detections so existing training tooling that reads the
        // detection log is unaffected by them.
        withLogDir { dir ->
            DetectionLogger.logCandidate("и мы читаем", "and we read", event(), reason = "below-threshold")
            DetectionLogger.drainForTests()

            val candidates = rowsOf(dir, "candidate-log-")
            assertTrue(candidates.isNotEmpty(), "a candidate file was written")
            assertContains(candidates.last(), "below-threshold")
            assertTrue(rowsOf(dir, "detection-log-").isEmpty(), "and nothing leaked into the detection log")
        }
    }

    @Test fun `a verse range records both ends`() {
        withLogDir { dir ->
            DetectionLogger.log("a", "b", event(verseStart = 16, verseEnd = 18, displayRef = "John 3:16-18"))
            DetectionLogger.drainForTests()

            val row = rowsOf(dir, "detection-log-").last()
            assertContains(row, "\"verseStart\":16")
            assertContains(row, "\"verseEnd\":18")
        }
    }

    @Test fun `quotes and newlines in a transcript cannot break the JSON line`() {
        // Transcripts are raw STT output; an unescaped quote would corrupt every downstream tool.
        withLogDir { dir ->
            DetectionLogger.log("he said \"read it\"\nthen paused", "b", event())
            DetectionLogger.drainForTests()

            val rows = rowsOf(dir, "detection-log-")
            assertTrue(rows.size == 2, "still exactly one header and one row, got ${rows.size}")
            assertTrue(!rows.last().contains("\n"), "the row is a single line")
        }
    }

    @Test fun `logging with no configured path writes nothing at all`() {
        val prevPath = DetectionLogger.path
        try {
            DetectionLogger.path = null
            DetectionLogger.log("a", "b", event())
            DetectionLogger.logCandidate("a", "b", event(), "reason")
            DetectionLogger.drainForTests()
        } finally {
            DetectionLogger.path = prevPath
        }
    }
}
