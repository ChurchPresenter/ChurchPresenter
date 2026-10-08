package org.churchpresenter.diagnostics

import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * One measured launch, driven directly: what it writes once its window is up, what it writes when
 * the window never comes, where resident memory is read from, and that the probe is off unless
 * asked for. Each test builds its own [StartupRun], so none shares a launch with another.
 */
class StartupProbeTest {

    private val dir = Files.createTempDirectory("startup-probe").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
        listOf(StartupProbe.PROPERTY, StartupProbe.IDLE_PROPERTY, StartupProbe.DEADLINE_PROPERTY)
            .forEach(System::clearProperty)
    }

    @Test
    fun `a launch is written as one line of JSON, an unknown resident set as null`() {
        val times = StartupTimes(toMainMs = 412, toFirstFrameMs = 2_310, idleHeapMb = 180.25, idleRssMb = null)
        assertEquals(
            "{\"toMainMs\": 412, \"toFirstFrameMs\": 2310, \"idleHeapMb\": 180.3, \"idleRssMb\": null}",
            times.toJson(),
        )
        assertTrue(times.copy(idleRssMb = 400.0).toJson().endsWith("\"idleRssMb\": 400.0}"))
    }

    @Test
    fun `once the window is up it waits out the idle time, writes the launch and exits, once`() {
        val file = File(dir, "nested/launch.json")
        val run = StartupRun(file, idleSeconds = 0, deadlineSeconds = 60)
        val exited = CountDownLatch(1)
        run.mainStarted(now = 200) { }
        run.firstFrame(now = 900, exit = exited::countDown)
        run.firstFrame(now = 950) { error("a second first frame must do nothing") }

        assertTrue(exited.await(5, TimeUnit.SECONDS), "the launch never exited")
        assertTrue(file.readText().startsWith("{\"toMainMs\": 200, \"toFirstFrameMs\": 900,"), file.readText())
    }

    @Test
    fun `a launch that never draws its window writes so and exits at the deadline`() {
        val file = File(dir, "stuck.json")
        val exited = CountDownLatch(1)
        StartupRun(file, idleSeconds = 60, deadlineSeconds = 0).mainStarted(now = 100, exit = exited::countDown)

        assertTrue(exited.await(5, TimeUnit.SECONDS), "the deadline never fired")
        assertEquals(StartupRun.NO_FIRST_FRAME + "\n", file.readText())
    }

    @Test
    fun `the sample carries when main started and a heap that was measured`() {
        val run = StartupRun(File(dir, "x.json"), idleSeconds = 60, deadlineSeconds = 60)
        run.mainStarted(now = 321) { }
        val times = run.times(firstFrameMs = 1_500)
        assertEquals(321, times.toMainMs)
        assertEquals(1_500, times.toFirstFrameMs)
        assertTrue(times.idleHeapMb > 0)
    }

    @Test
    fun `resident memory comes from proc where there is one, and from ps where there is not`() {
        val proc = File(dir, "status").apply { writeText("Name:\tjava\nVmRSS:\t  524288 kB\n") }
        assertEquals(512.0, residentMb(proc = proc, ps = { error("not asked") }))
        assertEquals(256.0, residentMb(proc = File(dir, "absent"), ps = { " 262144\n" }))
        assertNull(residentMb(proc = File(dir, "absent"), ps = { null }))
        assertNull(residentMb(proc = File(dir, "absent"), ps = { "ps: no such process" }))
        val noRss = File(dir, "status-without").apply { writeText("Name:\tjava\n") }
        assertNull(residentMb(proc = noRss, ps = { error("not asked") }))
    }

    @Test
    fun `the real ps answers with this process's resident set where it exists`() {
        val kb = psResidentKb()?.trim()?.toDoubleOrNull()
        assertTrue(kb == null || kb > 0, "ps said $kb")
    }

    @Test
    fun `off unless a target is given, and a bad number falls back to the default`() {
        assertNull(StartupProbe.fromProperties())
        System.setProperty(StartupProbe.PROPERTY, " ")
        assertNull(StartupProbe.fromProperties())
        System.setProperty(StartupProbe.PROPERTY, File(dir, "y.json").path)
        System.setProperty(StartupProbe.IDLE_PROPERTY, "soon")
        System.setProperty(StartupProbe.DEADLINE_PROPERTY, "later")
        assertNotNull(StartupProbe.fromProperties())
    }

    @Test
    fun `the app's own calls do nothing while the probe is off`() {
        StartupProbe.mainStarted { error("off, so nothing may exit") }
        StartupProbe.firstFrame { error("off, so nothing may exit") }
        assertTrue(StartupProbe.sinceJvmStart() > 0)
    }
}
