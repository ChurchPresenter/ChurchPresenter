package org.churchpresenter.liveoutput

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The OMT library's log keeps this run and the one before it, never more. */
class OmtLogRotationTest {

    private val dir = createTempDirectory("omt-log").toFile()

    @AfterTest
    fun tearDown() {
        dir.deleteRecursively()
    }

    @Test
    fun `last run's log moves aside and this run starts empty`() {
        val log = File(dir, "omt.log").apply { writeText("run 1") }
        rotateOmtLog(log)
        assertFalse(log.exists())
        assertEquals("run 1", File(dir, "omt.log.1").readText())
    }

    @Test
    fun `only one previous run is kept`() {
        val log = File(dir, "omt.log")
        File(dir, "omt.log.1").writeText("run 1")
        log.writeText("run 2")
        rotateOmtLog(log)
        assertEquals("run 2", File(dir, "omt.log.1").readText())
        assertEquals(listOf("omt.log.1"), dir.list()!!.sorted())
    }

    @Test
    fun `a first run creates the folder and moves nothing`() {
        val log = File(dir, "nested/omt.log")
        rotateOmtLog(log)
        assertTrue(log.parentFile.isDirectory)
        assertFalse(File(log.parentFile, "omt.log.1").exists())
    }
}
