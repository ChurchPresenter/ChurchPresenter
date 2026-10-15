package org.churchpresenter.outputguard

import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GuardWindowsTest {

    private val dir: File = Files.createTempDirectory("guard-windows").toFile()
    private val files = GuardFiles(dir)
    private val made = mutableListOf<FakeBackstop>()
    private val loads = mutableListOf<Int>()
    private val still = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
    private val windows = GuardWindows(
        files,
        newBackstop = { FakeBackstop().also { made += it } },
        loadStill = { id, then ->
            loads += id
            then(still)
        },
    )

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun output(id: Int, x: Int = 0, seq: Long = 0) = GuardedOutput(id, Rectangle(x, 0, 1920, 1080), seq)

    @Test
    fun `a window goes under each output, moves with it, and goes when it does`() {
        windows.follow(listOf(output(1), output(2, x = 1920)))
        assertEquals(2, windows.count)
        assertEquals(listOf(Rectangle(0, 0, 1920, 1080)), made[0].placed)

        windows.follow(listOf(output(1, x = 100), output(2, x = 1920)))
        assertEquals(listOf(Rectangle(0, 0, 1920, 1080), Rectangle(100, 0, 1920, 1080)), made[0].placed)
        assertEquals(1, made[1].placed.size, "an output that did not move is not placed again")

        windows.follow(listOf(output(1, x = 100)))
        assertEquals(1, windows.count)
        assertTrue(made[1].disposed)
    }

    @Test
    fun `a still is loaded once per new one`() {
        windows.follow(listOf(output(1, seq = 0)))
        windows.follow(listOf(output(1, seq = 0)))
        windows.follow(listOf(output(1, seq = 1)))

        assertEquals(listOf(1, 1), loads, "once when the window came, once for the newer still")
        assertEquals(listOf(still, still), made.single().shown)
    }

    @Test
    fun `covering raises every window once, and lowering puts them back once`() {
        windows.follow(listOf(output(1), output(2)))

        windows.cover()
        assertTrue(windows.isCovering)
        assertTrue(made.all { it.raised })

        made.forEach { it.raised = false }
        windows.cover()
        assertFalse(made.any { it.raised }, "already covering")

        made.forEach { it.raised = true }
        windows.lower()
        assertFalse(windows.isCovering)
        assertFalse(made.any { it.raised })

        made.forEach { it.raised = true }
        windows.lower()
        assertTrue(made.all { it.raised }, "already lowered")
    }

    @Test
    fun `closing disposes every window`() {
        windows.follow(listOf(output(1), output(2)))

        windows.close()

        assertEquals(0, windows.count)
        assertTrue(made.all { it.disposed })
    }

    @Test
    fun `by default a still is read off the event thread and shown on it`() {
        files.writeSnapshot(id = 3, width = 1, height = 1, bgra = byteArrayOf(0, 0, -1, -1))
        val real = GuardWindows(files, newBackstop = { FakeBackstop().also { made += it } })

        javax.swing.SwingUtilities.invokeAndWait { real.follow(listOf(output(3))) }

        val deadline = System.nanoTime() + 5_000_000_000L
        while (made.single().shown.isEmpty() && System.nanoTime() < deadline) Thread.yield()
        assertEquals(0xFFFF0000.toInt(), made.single().shown.single().getRGB(0, 0))
    }

    @Test
    fun `by default an output with no still shows nothing`() {
        files.writeSnapshot(id = 3, width = 1, height = 1, bgra = byteArrayOf(0, 0, -1, -1))
        val real = GuardWindows(files, newBackstop = { FakeBackstop().also { made += it } })

        // One loader thread, in order: once output 3's still is up, output 4's empty read is done.
        javax.swing.SwingUtilities.invokeAndWait { real.follow(listOf(output(4), output(3, x = 1920))) }

        val deadline = System.nanoTime() + 5_000_000_000L
        while (made[1].shown.isEmpty() && System.nanoTime() < deadline) Thread.yield()
        javax.swing.SwingUtilities.invokeAndWait { }
        assertEquals(1, made[1].shown.size)
        assertTrue(made[0].shown.isEmpty())
    }
}
