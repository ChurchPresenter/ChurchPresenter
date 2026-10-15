package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardStateTest {

    private val dir: File = Files.createTempDirectory("guard-state").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test
    fun `what the app writes the guard reads, through the file`() {
        val file = File(dir, "state")
        val app = GuardState.open(file)
        app.reset(pid = 4242, now = 1_000)
        app.heartbeatAt = 2_000
        app.uiTickAt = 1_500
        app.live = true
        app.takeOverOnHang = true
        val outputs = listOf(
            GuardedOutput(1, Rectangle(0, 0, 1920, 1080), snapshotSeq = 3),
            GuardedOutput(2, Rectangle(1920, 0, 1280, 720), snapshotSeq = 0),
        )
        app.outputs = outputs

        val guard = GuardState.open(file)

        assertTrue(guard.isCurrent)
        assertEquals(4242, guard.appPid)
        assertEquals(2_000, guard.heartbeatAt)
        assertEquals(1_500, guard.uiTickAt)
        assertTrue(guard.live)
        assertTrue(guard.takeOverOnHang)
        assertFalse(guard.closing)
        assertFalse(guard.outputsUp)
        assertEquals(outputs, guard.outputs)
    }

    @Test
    fun `each flag is set and cleared on its own`() {
        val state = GuardState.inMemory()
        state.reset(pid = 1, now = 0)

        state.closing = true
        state.outputsUp = true
        state.live = true
        state.outputsUp = false

        assertTrue(state.closing)
        assertTrue(state.live)
        assertFalse(state.outputsUp)
        assertFalse(state.takeOverOnHang)
    }

    @Test
    fun `a reset starts over, and a file no app has written is not current`() {
        val state = GuardState.inMemory()
        assertFalse(state.isCurrent)

        state.reset(pid = 7, now = 50)
        state.closing = true
        state.outputs = listOf(GuardedOutput(1, Rectangle(0, 0, 10, 10), 1))
        state.reset(pid = 8, now = 90)

        assertTrue(state.isCurrent)
        assertEquals(8, state.appPid)
        assertEquals(90, state.heartbeatAt)
        assertEquals(90, state.uiTickAt)
        assertFalse(state.closing)
        assertTrue(state.outputs.isEmpty())
    }

    @Test
    fun `a file another version of the app wrote is not current`() {
        val file = File(dir, "state")
        GuardState.open(file).reset(pid = 5, now = 0)
        RandomAccessFile(file, "rw").use { raf ->
            raf.seek(4)
            raf.write(byteArrayOf(99, 0, 0, 0))
        }

        assertFalse(GuardState.open(file).isCurrent)
    }

    @Test
    fun `the app's pid can be written on its own`() {
        val state = GuardState.inMemory()
        state.reset(pid = 5, now = 0)

        state.appPid = 6

        assertEquals(6, state.appPid)
    }

    @Test
    fun `no more than sixteen outputs are kept`() {
        val state = GuardState.inMemory()
        state.reset(pid = 1, now = 0)

        state.outputs = (1..20).map { GuardedOutput(it, Rectangle(it, 0, 100, 100), 0) }

        assertEquals((1..GuardState.MAX_OUTPUTS).toList(), state.outputs.map { it.id })
    }
}
