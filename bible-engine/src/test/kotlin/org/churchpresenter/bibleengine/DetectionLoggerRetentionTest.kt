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
import kotlin.test.assertTrue

class DetectionLoggerRetentionTest {

    private lateinit var dir: File
    private val savedPath = DetectionLogger.path
    private val savedSession = DetectionLogger.sessionId
    private val cutoff = 1_000_000_000_000L

    @BeforeTest
    fun setUp() {
        dir = Files.createTempDirectory("detection-log-retention").toFile()
    }

    @AfterTest
    fun tearDown() {
        DetectionLogger.path = savedPath
        DetectionLogger.sessionId = savedSession
        dir.deleteRecursively()
    }

    private fun file(name: String, modified: Long = cutoff - 1): File =
        File(dir, name).apply {
            writeText("{}\n")
            setLastModified(modified)
        }

    @Test
    fun `an old detection, candidate or sticky log has expired`() {
        assertTrue(DetectionLogger.isExpiredLog(file("detection-log-a.jsonl"), cutoff))
        assertTrue(DetectionLogger.isExpiredLog(file("candidate-log-a.jsonl"), cutoff))
        assertTrue(DetectionLogger.isExpiredLog(file("sticky-log-a.jsonl"), cutoff))
    }

    @Test
    fun `a recent log has not expired`() {
        assertFalse(DetectionLogger.isExpiredLog(file("detection-log-new.jsonl", cutoff + 1_000), cutoff))
    }

    @Test
    fun `an old file that is not one of the logger's logs is kept`() {
        assertFalse(DetectionLogger.isExpiredLog(file("live-references-a.jsonl"), cutoff))
        assertFalse(DetectionLogger.isExpiredLog(file("detection-log-a.txt"), cutoff))
    }

    @Test
    fun `a directory named like a log is kept`() {
        val folder = File(dir, "detection-log-folder.jsonl").apply { mkdirs() }
        folder.setLastModified(cutoff - 1)

        assertFalse(DetectionLogger.isExpiredLog(folder, cutoff))
    }

    private fun event() = ScriptureEvent(
        type = "scripture.detected",
        id = "live",
        reference = ScriptureReference(43, "John", 3, 16, null, "John 3:16", "B043C003V016", null, "hebrew"),
        verseText = "For God so loved the world",
        confidence = 0.95,
        matchType = "explicit",
        translation = "KJV",
        speechType = "Speaking",
    )

    @Test
    fun `re-attaching to a session file that has rows adds no second header`() {
        DetectionLogger.path = File(dir, "detection-log.jsonl").absolutePath
        DetectionLogger.sessionId = "reattach-${System.nanoTime()}"
        val existing = File(dir, "detection-log-${DetectionLogger.sessionId}.jsonl")
        existing.writeText("{\"type\":\"session\"}\n")

        DetectionLogger.log("john 3 16", "", event())
        DetectionLogger.drainForTests()

        val lines = existing.readLines()
        assertEquals(1, lines.count { it.contains("\"type\":\"session\"") })
        assertEquals(2, lines.size)
        assertTrue(lines.last().contains("\"speechType\":\"Speaking\""))
    }

    @Test
    fun `an empty session file still gets its header`() {
        DetectionLogger.path = File(dir, "detection-log.jsonl").absolutePath
        DetectionLogger.sessionId = "empty-${System.nanoTime()}"
        val existing = File(dir, "detection-log-${DetectionLogger.sessionId}.jsonl")
        existing.writeText("")

        DetectionLogger.log("john 3 16", "", event())
        DetectionLogger.drainForTests()

        val lines = existing.readLines()
        assertTrue(lines.first().contains("\"type\":\"session\""))
        assertEquals(2, lines.size)
    }
}
