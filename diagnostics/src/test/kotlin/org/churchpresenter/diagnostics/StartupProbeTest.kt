package org.churchpresenter.diagnostics

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The startup probe's own steps: what a launch writes, where resident memory is read from, and that
 * it stays out of the way when it is not asked for.
 */
class StartupProbeTest {

    private val dir = Files.createTempDirectory("startup-probe").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        System.clearProperty(StartupProbe.PROPERTY)
    }

    @Test
    fun `a launch is written as one line of JSON, an unknown resident set as null`() {
        val file = File(dir, "nested/startup-1.json")
        StartupProbe.write(
            file,
            StartupTimes(toMainMs = 412, toFirstFrameMs = 2_310, idleHeapMb = 180.25, idleRssMb = null),
        )
        assertEquals(
            "{\"toMainMs\": 412, \"toFirstFrameMs\": 2310, \"idleHeapMb\": 180.3, \"idleRssMb\": null}\n",
            file.readText(),
        )
    }

    @Test
    fun `resident memory comes from proc where there is one, and from ps where there is not`() {
        val proc = File(dir, "status").apply { writeText("Name:\tjava\nVmRSS:\t  524288 kB\n") }
        assertEquals(512.0, StartupProbe.residentMb(proc = proc, ps = { error("not asked") }))
        assertEquals(256.0, StartupProbe.residentMb(proc = File(dir, "absent"), ps = { " 262144\n" }))
        assertNull(StartupProbe.residentMb(proc = File(dir, "absent"), ps = { null }))
        assertNull(StartupProbe.residentMb(proc = File(dir, "absent"), ps = { "ps: no such process" }))
        val noRss = File(dir, "status-without").apply { writeText("Name:\tjava\n") }
        assertNull(StartupProbe.residentMb(proc = noRss, ps = { error("not asked") }))
    }

    @Test
    fun `the real ps answers with this process's resident set where it exists`() {
        val kb = StartupProbe.psResidentKb()?.trim()?.toDoubleOrNull()
        assertTrue(kb == null || kb > 0, "ps said $kb")
    }

    @Test
    fun `a blank target is off, and an idle time that is not a number falls back to thirty seconds`() {
        System.setProperty(StartupProbe.PROPERTY, " ")
        System.setProperty(StartupProbe.IDLE_PROPERTY, "soon")
        try {
            assertNull(StartupProbe.target)
            assertEquals(30, StartupProbe.idleSeconds)
        } finally {
            System.clearProperty(StartupProbe.IDLE_PROPERTY)
        }
    }

    @Test
    fun `a launch whose main was never marked says so, and carries the resident set it read`() {
        val times = StartupTimes(toMainMs = -1, toFirstFrameMs = 2_000, idleHeapMb = 100.0, idleRssMb = 400.0)
        assertEquals(400.0, times.idleRssMb)
        assertTrue(times.toJson().endsWith("\"idleRssMb\": 400.0}"), times.toJson())
    }

    @Test
    fun `off unless asked for, a first frame starts nothing`() {
        var exited = false
        StartupProbe.mainStarted()
        StartupProbe.firstFrame(exit = { exited = true })
        assertNull(StartupProbe.target)
        assertFalse(exited)
    }

    @Test
    fun `asked for, the first frame waits out the idle time, writes the launch and exits, once`() {
        val file = File(dir, "launch.json")
        System.setProperty(StartupProbe.PROPERTY, file.path)
        System.setProperty(StartupProbe.IDLE_PROPERTY, "0")
        try {
            val exited = CountDownLatch(1)
            StartupProbe.mainStarted(now = 200)
            StartupProbe.firstFrame(exit = exited::countDown, now = 900)
            StartupProbe.firstFrame(exit = { error("a second first frame must do nothing") }, now = 950)
            assertTrue(exited.await(5, TimeUnit.SECONDS), "the probe never exited")
            assertTrue(file.readText().startsWith("{\"toMainMs\": 200, \"toFirstFrameMs\": 900,"), file.readText())
        } finally {
            System.clearProperty(StartupProbe.IDLE_PROPERTY)
        }
    }

    @Test
    fun `asked for, the launch so far carries when main started`() {
        System.setProperty(StartupProbe.PROPERTY, File(dir, "x.json").path)
        StartupProbe.mainStarted(now = 321)
        val times = StartupProbe.times(firstFrameMs = 1_500)
        assertEquals(321, times.toMainMs)
        assertEquals(1_500, times.toFirstFrameMs)
        assertTrue(times.idleHeapMb > 0)
    }
}
