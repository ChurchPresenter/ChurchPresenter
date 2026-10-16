package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OutputGuardLinkTest {

    private val dir: File = Files.createTempDirectory("guard-link").toFile()
    private val files = GuardFiles(dir)
    private val now = AtomicLong(1_000)
    private val starts = AtomicInteger()
    private val guard = FakeProcess()
    private val links = mutableListOf<OutputGuardLink>()

    private fun link(startGuard: (GuardFiles) -> Process? = { starts.incrementAndGet(); guard }) =
        OutputGuardLink(
            files,
            startGuard = startGuard,
            relaunchCommand = listOf("app", "--flag"),
            pid = APP,
            clock = now::get,
            beatMs = 5,
        ).also { links += it }

    private fun state() = GuardState.open(files.state)

    @AfterTest
    fun cleanUp() {
        links.forEach(OutputGuardLink::stop)
        dir.deleteRecursively()
    }

    @Test
    fun `a new link starts the state over for this app and says how to start it again`() {
        link()

        val state = state()
        assertTrue(state.isCurrent)
        assertEquals(APP, state.appPid)
        assertEquals(1_000, state.heartbeatAt)
        assertEquals(listOf("app", "--flag"), files.readRelaunchCommand())
    }

    @Test
    fun `an output keeps its id, and a new one gets the next`() {
        val link = link()

        val first = link.register("a", { true }, { Rectangle() })
        assertEquals(first, link.register("a", { false }, { Rectangle(1, 1, 1, 1) }))
        val second = link.register("b", { true }, { Rectangle() })
        assertNotEquals(first, second)
        assertEquals(mapOf(first to "a", second to "b"), link.outputs())
    }

    @Test
    fun `the heartbeat reports the showing outputs with their latest still, and their being up`() {
        val link = link()
        val shown = link.register("shown", { true }, { Rectangle(0, 0, 1920, 1080) })
        link.register("hidden", { false }, { Rectangle(1920, 0, 1280, 720) })
        link.snapshot(shown, width = 1, height = 1, bgra = ByteArray(4))
        link.snapshot(shown, width = 1, height = 1, bgra = ByteArray(4))
        now.set(5_000)

        link.start()

        awaitTrue("the outputs reported") {
            state().outputs == listOf(GuardedOutput(shown, Rectangle(0, 0, 1920, 1080), snapshotSeq = 2))
        }
        val state = state()
        assertTrue(state.outputsUp)
        assertEquals(5_000, state.heartbeatAt)
        awaitTrue("the event thread's tick") { state().uiTickAt == 5_000L }
        assertNotNull(files.readSnapshot(shown))
        assertEquals(1, starts.get())
    }

    @Test
    fun `an output that goes is dropped from the report and its still deleted`() {
        val link = link()
        val id = link.register("gone", { true }, { Rectangle(0, 0, 10, 10) })
        link.snapshot(id, width = 1, height = 1, bgra = ByteArray(4))
        link.start()
        awaitTrue("the output reported") { state().outputs.size == 1 }

        link.unregister("gone")
        link.unregister("never registered")

        awaitTrue("the output dropped") { state().outputs.isEmpty() }
        awaitTrue("its still deleted") { files.readSnapshot(id) == null }
    }

    @Test
    fun `a guard that dies is started again, but not more often than every few seconds`() {
        val link = link()
        link.start()
        guard.alive = false
        now.set(2_000)

        // A beat that wrote 2 000 came after the guard died, and saw it dead.
        awaitTrue("a beat after the guard died") { state().heartbeatAt == 2_000L }
        assertEquals(1, starts.get(), "too soon after the last start")

        now.set(1_000 + 5_000)
        awaitTrue("started again") { starts.get() == 2 }
    }

    @Test
    fun `a guard that cannot start leaves the app running`() {
        val link = link(startGuard = { error("no launcher") })

        link.start()
        link.register("out", { true }, { Rectangle(0, 0, 10, 10) })

        awaitTrue("the heartbeat still reports") { state().outputs.size == 1 }
    }

    @Test
    fun `the live and hang switches are the guard's to read`() {
        val link = link()

        link.live = true
        link.takeOverOnHang = true

        assertTrue(link.live)
        assertTrue(link.takeOverOnHang)
        assertTrue(state().live)
        assertTrue(state().takeOverOnHang)
    }

    @Test
    fun `the live state is saved for a relaunched app`() {
        link().saveLiveState("""{"mode":"LYRICS"}""")

        assertEquals("""{"mode":"LYRICS"}""", files.liveState.readText())
    }

    @Test
    fun `stopping tells the guard the quit is on purpose, and nothing more is written`() {
        val link = link()
        link.start()

        link.stop()
        link.stop()
        link.snapshot(id = 9, width = 1, height = 1, bgra = ByteArray(4))
        link.saveLiveState("late")

        assertTrue(state().closing)
        assertNull(files.readSnapshot(9))
        assertFalse(files.liveState.exists())

        link.start()
        assertEquals(1, starts.get(), "a stopped link starts no guard")
    }

    @Test
    fun `starting twice starts one guard`() {
        val link = link()

        link.start()
        link.start()

        assertEquals(1, starts.get())
    }

    @Test
    fun `by default the link speaks for this process`() {
        val link = OutputGuardLink(files, startGuard = { null }, relaunchCommand = emptyList()).also { links += it }

        assertEquals(ProcessHandle.current().pid(), state().appPid)
        assertFalse(link.live)
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            Thread.yield()
        }
    }

    /** A guard process that only says whether it is alive. */
    private class FakeProcess : Process() {
        @Volatile var alive = true

        override fun getOutputStream(): OutputStream = OutputStream.nullOutputStream()
        override fun getInputStream(): InputStream = InputStream.nullInputStream()
        override fun getErrorStream(): InputStream = InputStream.nullInputStream()
        override fun waitFor(): Int = 0
        override fun exitValue(): Int = 0
        override fun destroy() {
            alive = false
        }
        override fun isAlive(): Boolean = alive
    }

    private companion object {
        const val APP = 4242L
    }
}
