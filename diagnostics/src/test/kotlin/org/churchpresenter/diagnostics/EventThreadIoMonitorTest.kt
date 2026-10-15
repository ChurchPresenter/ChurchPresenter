package org.churchpresenter.diagnostics

import jdk.jfr.FlightRecorder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventThreadIoMonitorTest {

    private val reported = mutableListOf<BlockedCall>()
    private val monitor = EventThreadIoMonitor(onBlocked = { reported += it }, codeRoots = listOf("/app/classes"))

    private val appFrame = "org.churchpresenter.slides.presenter.PicturePresenterKt.ImageContent(line 143)"
    private val jdkFrame = "java.io.FileInputStream.readBytes(line 0)"

    @Test
    fun `a file read on the event thread is reported once per place, with where it happened`() {
        repeat(2) {
            monitor.accept("AWT-EventQueue-0", "jdk.FileRead", "/photos/a.jpg", 2_500_000, listOf(jdkFrame, appFrame))
        }

        val call = reported.single()
        assertEquals("file read", call.kind)
        assertEquals("/photos/a.jpg", call.target)
        assertEquals(2.5, call.durationMs)
        assertEquals(listOf(jdkFrame, appFrame), call.frames)
        assertEquals(listOf(appFrame), call.appFrames)
        assertEquals("file read /photos/a.jpg for 2.5 ms at $appFrame", call.describe())
    }

    @Test
    fun `the same place on another file, or another place, is reported again`() {
        monitor.accept("AWT-EventQueue-0", "jdk.FileRead", "/photos/a.jpg", 0, listOf(jdkFrame, appFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.FileRead", "/photos/b.jpg", 0, listOf(jdkFrame, appFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.FileRead", "/photos/a.jpg", 0, listOf(jdkFrame))

        assertEquals(3, reported.size)
    }

    @Test
    fun `work on any other thread is not the event thread's`() {
        monitor.accept("DefaultDispatcher-worker-1", "jdk.FileRead", "/photos/a.jpg", 0, listOf(appFrame))
        monitor.accept(null, "jdk.FileRead", "/photos/a.jpg", 0, listOf(appFrame))

        assertTrue(reported.isEmpty())
    }

    @Test
    fun `loading code and reading the random device are not the app's work`() {
        listOf("/app/lib/ui.jar", "/app/out/Theme.class", "/app/classes/strings.xml", "/dev/urandom").forEach {
            monitor.accept("AWT-EventQueue-0", "jdk.FileRead", it, 0, listOf(appFrame))
        }

        assertTrue(reported.isEmpty(), reported.toString())
    }

    @Test
    fun `a log line written to the console is not a file`() {
        monitor.accept("AWT-EventQueue-0", "jdk.FileWrite", "", 0, listOf(appFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.FileRead", "", 0, listOf(appFrame))

        assertTrue(reported.isEmpty(), reported.toString())
    }

    @Test
    fun `a sleep, a socket and a write are each reported by their kind`() {
        monitor.accept("AWT-EventQueue-0", "jdk.ThreadSleep", "", 100_000_000, listOf(appFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.SocketRead", "192.168.1.20", 0, listOf(appFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.FileWrite", "/tmp/out.png", 0, listOf(appFrame))

        assertEquals(listOf("sleep", "socket read", "file write"), reported.map { it.kind })
        assertEquals("sleep for 100.0 ms at $appFrame", reported.first().describe())
    }

    @Test
    fun `a call with no frames of the app's own names its top frame, or says it has none`() {
        monitor.accept("AWT-EventQueue-0", "jdk.SocketWrite", "host", 0, listOf(jdkFrame))
        monitor.accept("AWT-EventQueue-0", "jdk.SocketWrite", "host", 0, emptyList())

        assertEquals(listOf(jdkFrame, "an unknown frame"), reported.map { it.place() })
    }

    @Test
    fun `starting twice runs one recording, and closing ends it`() {
        val recorder = FlightRecorder.getFlightRecorder()
        val before = recorder.recordings.size
        val running = EventThreadIoMonitor(onBlocked = {})
        try {
            assertSame(running, running.start())
            assertSame(running, running.start())
            assertEquals(before + 1, recorder.recordings.size)
        } finally {
            running.close()
            running.close()
        }
        assertEquals(before, recorder.recordings.size)
    }

    @Test
    fun `the code roots are the class path's directories and the JDK`() {
        val roots = EventThreadIoMonitor.defaultCodeRoots()

        assertTrue(System.getProperty("java.home") in roots, roots.toString())
        assertTrue(roots.none { it.endsWith(".jar") }, "a jar is matched by its name, not as a root")
    }
}
