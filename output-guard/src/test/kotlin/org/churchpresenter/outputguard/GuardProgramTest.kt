package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardProgramTest {

    private val dir: File = Files.createTempDirectory("guard-program").toFile()
    private val files = GuardFiles(dir)
    private val state = GuardState.inMemory().apply { reset(pid = APP, now = 0) }
    private val windows = GuardWindows(files, newBackstop = ::FakeBackstop, loadStill = { _, _ -> })
    private var alive = true
    private var now = 0L
    private val relaunched = mutableListOf<List<String>>()
    private val exits = AtomicInteger()
    private val program = GuardProgram(
        files, state, APP,
        appAlive = { alive },
        relaunch = { relaunched += it },
        clock = { now },
        exit = { exits.incrementAndGet() },
        windows = windows,
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun appHasOneOutput() {
        state.outputs = listOf(GuardedOutput(1, Rectangle(0, 0, 1920, 1080), 0))
    }

    @Test
    fun `while the app runs, a window sits under each of its outputs`() {
        appHasOneOutput()

        program.tick()

        assertEquals(1, windows.count)
        assertFalse(windows.isCovering)
    }

    @Test
    fun `when the app dies its outputs are covered and it is started again, once`() {
        files.writeRelaunchCommand(listOf("app", "-Xmx2g"))
        appHasOneOutput()
        program.tick()

        alive = false
        program.tick()
        program.tick()

        assertTrue(windows.isCovering)
        assertEquals(listOf(listOf("app", "-Xmx2g", RELAUNCHED_ARGUMENT)), relaunched)
        assertEquals(0, exits.get())
    }

    @Test
    fun `the guard leaves once the app it started has its outputs up`() {
        files.writeRelaunchCommand(listOf("app"))
        appHasOneOutput()
        program.tick()
        alive = false
        program.tick()

        state.reset(pid = APP + 1, now = now)
        program.tick()
        assertEquals(0, exits.get(), "the new app is up, its outputs are not")

        state.outputsUp = true
        program.tick()
        assertEquals(1, exits.get())
        assertEquals(0, windows.count)
    }

    @Test
    fun `with no command to start the app again, the stills stay up`() {
        appHasOneOutput()
        program.tick()
        alive = false

        program.tick()

        assertTrue(relaunched.isEmpty())
        assertTrue(windows.isCovering)
        assertEquals(0, exits.get())
    }

    @Test
    fun `a quit on purpose leaves without covering anything`() {
        appHasOneOutput()
        program.tick()
        state.closing = true
        alive = false

        program.tick()

        assertEquals(1, exits.get())
        assertFalse(windows.isCovering)
        assertTrue(relaunched.isEmpty())
    }

    @Test
    fun `a hang is covered while it lasts, when asked for`() {
        appHasOneOutput()
        state.takeOverOnHang = true
        state.live = true
        now = 10_000
        state.heartbeatAt = now
        state.uiTickAt = now - HANG_MS - 1

        program.tick()
        assertTrue(windows.isCovering)

        state.uiTickAt = now
        program.tick()
        assertFalse(windows.isCovering)
    }

    @Test
    fun `started, it looks at the app on its own until told to leave`() {
        appHasOneOutput()
        val running = GuardProgram(
            files, state, APP,
            appAlive = { true },
            relaunch = { },
            exit = { exits.incrementAndGet() },
            windows = windows,
        )

        running.start()
        awaitTrue("the first look") { windows.count == 1 }
        state.closing = true
        awaitTrue("it leaves") { exits.get() == 1 }
    }

    private fun awaitTrue(what: String, condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition()) {
            check(System.nanoTime() < deadline) { "timed out waiting for $what" }
            Thread.yield()
        }
    }

    private companion object {
        const val APP = 4242L
    }
}
