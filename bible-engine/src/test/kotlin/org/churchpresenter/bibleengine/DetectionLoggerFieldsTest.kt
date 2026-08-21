package org.churchpresenter.bibleengine

import org.churchpresenter.bibleengine.engine.DetectionLogger
import org.churchpresenter.bibleengine.engine.ScriptureEvent
import org.churchpresenter.bibleengine.engine.ScriptureReference
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DetectionLoggerFieldsTest {

    private lateinit var dir: File
    private var savedPath: String? = null
    private var savedSession: String? = null
    private val savedSticky = Config.logStickyChanges

    @BeforeTest
    fun useTempLog() {
        dir = Files.createTempDirectory("detection-log-fields").toFile()
        savedPath = DetectionLogger.path
        savedSession = DetectionLogger.sessionId
        DetectionLogger.path = "${dir.absolutePath}/detection-log.jsonl"
        DetectionLogger.sessionId = null
    }

    @AfterTest
    fun restore() {
        DetectionLogger.drainForTests()
        DetectionLogger.path = savedPath
        DetectionLogger.sessionId = savedSession
        Config.logStickyChanges = savedSticky
        dir.deleteRecursively()
    }

    private fun event(
        verseEnd: Int? = null,
        sessionId: String? = null,
        segmentId: String? = null,
        sttStartTime: Double? = null,
        bm25Score: Double? = null,
        bm25Ratio: Double? = null,
    ) = ScriptureEvent(
        type = "scripture.detected",
        id = "live",
        reference = ScriptureReference(
            bookId = 43, bookName = "John", chapter = 3, verseStart = 16, verseEnd = verseEnd,
            displayRef = "John 3:16", canonicalCodeStart = "B043C003V016",
            canonicalCodeEnd = verseEnd?.let { "B043C003V%03d".format(it) }, numbering = "hebrew",
        ),
        verseText = "For God so loved the world",
        confidence = 0.95,
        matchType = "explicit",
        translation = "KJV",
        sessionId = sessionId,
        segmentId = segmentId,
        sttStartTime = sttStartTime,
        bm25Score = bm25Score,
        bm25Ratio = bm25Ratio,
    )

    private fun logLines(prefix: String): List<String> {
        DetectionLogger.drainForTests()
        val file = assertNotNull(
            dir.listFiles()?.firstOrNull { it.name.startsWith(prefix) },
            "no $prefix file; saw ${dir.listFiles()?.map { it.name }}",
        )
        return file.readLines().filter { it.isNotBlank() }
    }

    private fun detectionRow(): String = logLines("detection-log-").last()

    @Test
    fun `absent correlation fields are written as explicit nulls, not dropped`() {
        DetectionLogger.log("spoken", "", event())

        val row = detectionRow()
        assertTrue(row.contains("\"sessionId\":null"), row)
        assertTrue(row.contains("\"segmentId\":null"), row)
        assertTrue(row.contains("\"sttStartTime\":null"), row)
    }

    @Test
    fun `absent bm25 scores are omitted entirely`() {
        DetectionLogger.log("spoken", "", event())

        val row = detectionRow()
        assertFalse(row.contains("\"bm25Score\""), row)
        assertFalse(row.contains("\"bm25Ratio\""), row)
    }

    @Test
    fun `optional correlation fields are written when present`() {
        DetectionLogger.log(
            "spoken", "",
            event(sessionId = "S01", segmentId = "seg-7", sttStartTime = 12.5, bm25Score = 3.5, bm25Ratio = 2.0),
        )

        val row = detectionRow()
        assertTrue(row.contains("\"sessionId\":\"S01\""), row)
        assertTrue(row.contains("\"segmentId\":\"seg-7\""), row)
        assertTrue(row.contains("\"sttStartTime\":12.5"), row)
        assertTrue(row.contains("\"bm25Score\":3.5"), row)
        assertTrue(row.contains("\"bm25Ratio\":2.0"), row)
    }

    @Test
    fun `a verse range records the canonical end code`() {
        DetectionLogger.log("spoken", "", event(verseEnd = 18))

        assertTrue(detectionRow().contains("B043C003V018"), detectionRow())
    }

    @Test
    fun `a single verse records an empty canonical end`() {
        DetectionLogger.log("spoken", "", event())

        assertTrue(detectionRow().contains("\"canonicalEnd\":\"\""), detectionRow())
    }

    @Test
    fun `a sticky change with no previous context writes nulls`() {
        Config.logStickyChanges = true

        DetectionLogger.logStickyChange("spoken", "", null, null, 43, 3)

        val row = logLines("sticky-log-").last()
        assertTrue(row.contains("\"prevBook\":null"), row)
        assertTrue(row.contains("\"prevChapter\":null"), row)
        assertTrue(row.contains("\"newBook\":43"), row)
        assertTrue(row.contains("\"newChapter\":3"), row)
    }

    @Test
    fun `a sticky change clearing the context writes nulls for the new side`() {
        Config.logStickyChanges = true

        DetectionLogger.logStickyChange("spoken", "", 43, 3, null, null)

        val row = logLines("sticky-log-").last()
        assertTrue(row.contains("\"newBook\":null"), row)
        assertTrue(row.contains("\"newChapter\":null"), row)
    }

    @Test
    fun `a sticky row records a null session id when none is set`() {
        Config.logStickyChanges = true
        DetectionLogger.sessionId = null

        DetectionLogger.logStickyChange("spoken", "", null, null, 43, 3)

        assertTrue(logLines("sticky-log-").last().contains("\"sessionId\":null"), logLines("sticky-log-").last())
    }

    @Test
    fun `a session id is sanitized into the file name`() {
        DetectionLogger.sessionId = "S 01/../weird*name"

        DetectionLogger.log("spoken", "", event())
        DetectionLogger.drainForTests()

        val name = assertNotNull(dir.listFiles()?.firstOrNull { it.name.startsWith("detection-log-") }).name
        assertFalse(name.contains("/"), name)
        assertFalse(name.contains("*"), name)
    }

    @Test
    fun `a new session id opens its own file`() {
        DetectionLogger.sessionId = "SESSA"
        DetectionLogger.log("spoken", "", event())
        DetectionLogger.sessionId = "SESSB"
        DetectionLogger.log("spoken", "", event())
        DetectionLogger.drainForTests()

        val files = dir.listFiles()!!.filter { it.name.startsWith("detection-log-") }.map { it.name }
        assertEquals(2, files.size, "expected one file per session, got $files")
    }

    @Test
    fun `a candidate row keeps its reason`() {
        DetectionLogger.logCandidate("spoken", "", event(), "low-agreement")

        assertTrue(logLines("candidate-log-").last().contains("low-agreement"))
    }

    @Test
    fun `sticky logging is skipped when disabled`() {
        Config.logStickyChanges = false

        DetectionLogger.logStickyChange("spoken", "", null, null, 43, 3)
        DetectionLogger.drainForTests()

        assertTrue(dir.listFiles()?.none { it.name.startsWith("sticky-log-") } != false)
    }

    @Test
    fun `nothing is written when no path is configured`() {
        DetectionLogger.path = null

        DetectionLogger.log("spoken", "", event())
        DetectionLogger.logCandidate("spoken", "", event(), "low-agreement")
        DetectionLogger.logStickyChange("spoken", "", null, null, 43, 3)
        DetectionLogger.drainForTests()

        assertTrue(dir.listFiles().isNullOrEmpty())
    }
}
