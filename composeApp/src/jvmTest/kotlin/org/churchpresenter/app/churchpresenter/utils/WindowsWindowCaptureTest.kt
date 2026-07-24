package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.platform.win32.WinUser
import io.mockk.every
import io.mockk.mockk
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [WindowsWindowCapture] wraps user32/gdi32 through JNA, so its capture body only runs on Windows.
 * The reachable, cross-platform contract is the availability gate: on every non-Windows host the
 * three entry points must short-circuit to empty/null without touching JNA or throwing. These tests
 * assert that contract, keying off the actual host OS so they hold on all three target platforms
 * (the GDI capture path itself needs a real Windows window and is out of reach headless).
 */
class WindowsWindowCaptureTest {

    private val isWindows = System.getProperty("os.name", "").lowercase().contains("win")

    @Test
    fun `isAvailable is false off Windows and stable across calls`() {
        val first = WindowsWindowCapture.isAvailable()
        if (!isWindows) assertTrue(!first, "capture must report unavailable on a non-Windows host")
        // Memoised: a second call must agree with the first regardless of platform.
        assertEquals(first, WindowsWindowCapture.isAvailable())
    }

    @Test
    fun `listWindows returns an empty list when capture is unavailable`() {
        val windows = WindowsWindowCapture.listWindows()
        assertNotNull(windows, "listWindows must never return null")
        if (!isWindows) assertTrue(windows.isEmpty(), "no windows can be enumerated off Windows")
    }

    @Test
    fun `getWindowBounds returns null for an invalid handle`() {
        // Handle 0 is never a real window, so this is null on every platform: unavailable off
        // Windows, and a failed GetWindowRect on Windows.
        assertNull(WindowsWindowCapture.getWindowBounds(0L))
    }

    @Test
    fun `captureWindow returns null for an invalid handle`() {
        assertNull(WindowsWindowCapture.captureWindow(0L))
    }

    @Test
    fun `WindowInfo carries its title and handle`() {
        val info = WindowsWindowCapture.WindowInfo("Main Output", 0x1234L)
        assertEquals("Main Output", info.title)
        assertEquals(0x1234L, info.hwnd)
        // Value semantics — two infos with the same fields are equal.
        assertEquals(info, WindowsWindowCapture.WindowInfo("Main Output", 0x1234L))
    }

    @Test
    fun `WindowInfo destructures into title and handle`() {
        val (title, hwnd) = WindowsWindowCapture.WindowInfo("Confidence Monitor", 42L)
        assertEquals("Confidence Monitor", title)
        assertEquals(42L, hwnd)
    }

    @Test
    fun `WindowInfo copy changes only the named field`() {
        val info = WindowsWindowCapture.WindowInfo("Stage", 1L)
        assertEquals(WindowsWindowCapture.WindowInfo("Stage", 2L), info.copy(hwnd = 2L))
        assertEquals(WindowsWindowCapture.WindowInfo("Booth", 1L), info.copy(title = "Booth"))
    }

    @Test
    fun `WindowInfo distinguishes different titles and handles`() {
        val info = WindowsWindowCapture.WindowInfo("Output", 1L)
        assertNotEquals(info, WindowsWindowCapture.WindowInfo("Output", 2L))
        assertNotEquals(info, WindowsWindowCapture.WindowInfo("Preview", 1L))
    }

    @Test
    fun `equal WindowInfos share a hash code`() {
        assertEquals(
            WindowsWindowCapture.WindowInfo("Output", 7L).hashCode(),
            WindowsWindowCapture.WindowInfo("Output", 7L).hashCode(),
        )
    }

    @Test
    fun `no invalid handle yields bounds or a capture`() {
        for (bad in listOf(0L, -1L, Long.MAX_VALUE)) {
            assertNull(WindowsWindowCapture.getWindowBounds(bad), "bounds for handle $bad")
            assertNull(WindowsWindowCapture.captureWindow(bad), "capture for handle $bad")
        }
    }

    @Test
    fun `listWindows is repeatable and never throws`() {
        val first = WindowsWindowCapture.listWindows()
        val second = WindowsWindowCapture.listWindows()
        assertNotNull(first)
        assertNotNull(second)
        if (!isWindows) {
            assertTrue(first.isEmpty() && second.isEmpty())
        }
    }

    @Test
    fun `equal WindowInfos collapse to one set entry`() {
        val set = setOf(
            WindowsWindowCapture.WindowInfo("Output", 1L),
            WindowsWindowCapture.WindowInfo("Output", 1L),
            WindowsWindowCapture.WindowInfo("Output", 2L),
        )
        assertEquals(2, set.size)
        assertTrue(WindowsWindowCapture.WindowInfo("Output", 1L) in set)
    }

    @Test
    fun `WindowInfo toString shows its title and handle`() {
        val text = WindowsWindowCapture.WindowInfo("Lower Third", 99L).toString()
        assertTrue("Lower Third" in text, text)
        assertTrue("99" in text, text)
    }

    @Test
    fun `a full-width handle survives as a plain Long`() {
        val info = WindowsWindowCapture.WindowInfo("wide", Long.MAX_VALUE)
        assertEquals(Long.MAX_VALUE, info.hwnd)
    }

    @Test
    fun `an empty title is preserved`() {
        val info = WindowsWindowCapture.WindowInfo("", 0L)
        assertEquals("", info.title)
        assertEquals(WindowsWindowCapture.WindowInfo("", 0L), info)
    }

    private fun bufferOf(vararg bytes: Int): Memory =
        Memory(bytes.size.toLong()).apply {
            bytes.forEachIndexed { i, b -> setByte(i.toLong(), b.toByte()) }
        }

    @Test
    fun `the pixel conversion maps each channel to its ARGB slot`() {
        val buffer = bufferOf(0x11, 0x22, 0x33, 0xFF, 0xAA, 0xBB, 0xCC, 0x00)

        val img = WindowsWindowCapture.bgraBufferToImage(buffer, width = 2, height = 1)

        assertEquals(2, img.width)
        assertEquals(1, img.height)
        assertEquals(0xFF112233.toInt(), img.getRGB(0, 0))
        assertEquals(0xFFAABBCC.toInt(), img.getRGB(1, 0))
    }

    @Test
    fun `every produced pixel is fully opaque regardless of the source alpha byte`() {
        val buffer = bufferOf(0x01, 0x02, 0x03, 0x00)

        val img = WindowsWindowCapture.bgraBufferToImage(buffer, width = 1, height = 1)

        assertEquals(0xFF, img.getRGB(0, 0) ushr 24, "alpha must be forced opaque")
    }

    @Test
    fun `rows are read at the right stride`() {
        val buffer = bufferOf(0x01, 0x02, 0x03, 0x00, 0x04, 0x05, 0x06, 0x00)

        val img = WindowsWindowCapture.bgraBufferToImage(buffer, width = 1, height = 2)

        assertEquals(0xFF010203.toInt(), img.getRGB(0, 0))
        assertEquals(0xFF040506.toInt(), img.getRGB(0, 1))
    }

    @Test
    fun `the produced image is ARGB`() {
        val img = WindowsWindowCapture.bgraBufferToImage(bufferOf(0, 0, 0, 0), width = 1, height = 1)
        assertEquals(BufferedImage.TYPE_INT_ARGB, img.type)
    }

    @Test
    fun `bounds are the rect origin with width and height derived from the edges`() {
        val bounds = WindowsWindowCapture.rectToBounds(left = 10, top = 20, right = 110, bottom = 220)
        assertEquals(10, bounds.x)
        assertEquals(20, bounds.y)
        assertEquals(100, bounds.width, "width is right - left, not right")
        assertEquals(200, bounds.height, "height is bottom - top, not bottom")
    }

    @Test
    fun `bounds handle a negative origin from an off-screen window`() {
        val bounds = WindowsWindowCapture.rectToBounds(left = -50, top = -30, right = 150, bottom = 90)
        assertEquals(-50, bounds.x)
        assertEquals(-30, bounds.y)
        assertEquals(200, bounds.width)
        assertEquals(120, bounds.height)
    }

    @Test
    fun `a titled window keeps its title`() {
        val buf = CharArray(512).also { "Main Output".toCharArray().copyInto(it) }
        assertEquals("Main Output", WindowsWindowCapture.titleOrNull(buf, "Main Output".length))
    }

    @Test
    fun `a zero-length title is dropped`() {
        assertNull(WindowsWindowCapture.titleOrNull(CharArray(512), 0))
    }

    @Test
    fun `a blank title is dropped`() {
        val buf = CharArray(512).also { "   ".toCharArray().copyInto(it) }
        assertNull(WindowsWindowCapture.titleOrNull(buf, 3))
    }

    @Test
    fun `only the reported length of the buffer is read`() {
        val buf = CharArray(512).also { "Preview leftover".toCharArray().copyInto(it) }
        assertEquals("Preview", WindowsWindowCapture.titleOrNull(buf, 7))
    }

    @Test
    fun `listWindowsWith collects a visible titled window`() {
        val user32 = mockk<User32>()
        val handle = WinDef.HWND(Pointer(42L))
        every { user32.EnumWindows(any(), any()) } answers {
            firstArg<WinUser.WNDENUMPROC>().callback(handle, null)
            true
        }
        every { user32.IsWindowVisible(handle) } returns true
        every { user32.GetWindowText(handle, any<CharArray>(), any()) } answers {
            "Zoom".toCharArray().copyInto(secondArg<CharArray>())
            4
        }

        val windows = WindowsWindowCapture.listWindowsWith(user32)

        assertEquals(listOf(WindowsWindowCapture.WindowInfo("Zoom", 42L)), windows)
    }

    @Test
    fun `listWindowsWith skips an invisible window`() {
        val user32 = mockk<User32>()
        val handle = WinDef.HWND(Pointer(1L))
        every { user32.EnumWindows(any(), any()) } answers {
            firstArg<WinUser.WNDENUMPROC>().callback(handle, null)
            true
        }
        every { user32.IsWindowVisible(handle) } returns false

        assertTrue(WindowsWindowCapture.listWindowsWith(user32).isEmpty())
    }

    @Test
    fun `listWindowsWith skips a window with no title`() {
        val user32 = mockk<User32>()
        val handle = WinDef.HWND(Pointer(1L))
        every { user32.EnumWindows(any(), any()) } answers {
            firstArg<WinUser.WNDENUMPROC>().callback(handle, null)
            true
        }
        every { user32.IsWindowVisible(handle) } returns true
        every { user32.GetWindowText(handle, any<CharArray>(), any()) } returns 0

        assertTrue(WindowsWindowCapture.listWindowsWith(user32).isEmpty())
    }

    @Test
    fun `listWindowsWith swallows a native failure`() {
        val user32 = mockk<User32>()
        every { user32.EnumWindows(any(), any()) } throws RuntimeException("boom")
        assertTrue(WindowsWindowCapture.listWindowsWith(user32).isEmpty())
    }

    private fun stubGetWindowRect(user32: User32, left: Int, top: Int, right: Int, bottom: Int) {
        every { user32.GetWindowRect(any(), any()) } answers {
            secondArg<WinDef.RECT>().apply {
                this.left = left; this.top = top; this.right = right; this.bottom = bottom
            }
            true
        }
    }

    @Test
    fun `getWindowBoundsWith turns the native rect into bounds`() {
        val user32 = mockk<User32>()
        stubGetWindowRect(user32, left = 10, top = 20, right = 110, bottom = 220)
        assertEquals(Rectangle(10, 20, 100, 200), WindowsWindowCapture.getWindowBoundsWith(user32, 5L))
    }

    @Test
    fun `getWindowBoundsWith returns null when GetWindowRect fails`() {
        val user32 = mockk<User32>()
        every { user32.GetWindowRect(any(), any()) } returns false
        assertNull(WindowsWindowCapture.getWindowBoundsWith(user32, 5L))
    }

    @Test
    fun `getWindowBoundsWith returns null when the native call throws`() {
        val user32 = mockk<User32>()
        every { user32.GetWindowRect(any(), any()) } throws RuntimeException()
        assertNull(WindowsWindowCapture.getWindowBoundsWith(user32, 5L))
    }

    private fun stubbedCapture(width: Int, height: Int): Pair<User32, GDI32> {
        val user32 = mockk<User32>()
        val gdi32 = mockk<GDI32>()
        stubGetWindowRect(user32, 0, 0, width, height)
        every { user32.GetDC(any()) } returns WinDef.HDC()
        every { user32.PrintWindow(any(), any(), any()) } returns true
        every { user32.ReleaseDC(any(), any()) } returns 1
        every { gdi32.CreateCompatibleDC(any()) } returns WinDef.HDC()
        every { gdi32.CreateCompatibleBitmap(any(), any(), any()) } returns WinDef.HBITMAP()
        every { gdi32.SelectObject(any(), any()) } returns WinNT.HANDLE()
        every { gdi32.GetDIBits(any(), any(), any(), any(), any(), any(), any()) } returns height
        every { gdi32.DeleteObject(any()) } returns true
        every { gdi32.DeleteDC(any()) } returns true
        return user32 to gdi32
    }

    @Test
    fun `captureWindowWith produces an opaque image of the window size`() {
        val (user32, gdi32) = stubbedCapture(width = 4, height = 3)

        val img = assertNotNull(WindowsWindowCapture.captureWindowWith(user32, gdi32, 7L))

        assertEquals(4, img.width)
        assertEquals(3, img.height)
        assertEquals(BufferedImage.TYPE_INT_ARGB, img.type)
        for (y in 0 until 3) for (x in 0 until 4) {
            assertEquals(0xFF, img.getRGB(x, y) ushr 24, "pixel ($x,$y) must be opaque")
        }
    }

    @Test
    fun `captureWindowWith returns null when the window rect is unavailable`() {
        val user32 = mockk<User32>()
        every { user32.GetWindowRect(any(), any()) } returns false
        assertNull(WindowsWindowCapture.captureWindowWith(user32, mockk(), 1L))
    }

    @Test
    fun `captureWindowWith returns null for a zero-area window`() {
        val user32 = mockk<User32>()
        stubGetWindowRect(user32, left = 5, top = 5, right = 5, bottom = 5)
        assertNull(WindowsWindowCapture.captureWindowWith(user32, mockk(), 1L))
    }

    @Test
    fun `captureWindowWith returns null when no device context is available`() {
        val user32 = mockk<User32>()
        stubGetWindowRect(user32, 0, 0, 4, 3)
        every { user32.GetDC(any()) } returns null
        assertNull(WindowsWindowCapture.captureWindowWith(user32, mockk(), 1L))
    }

    @Test
    fun `captureWindowWith returns null when a GDI call throws mid-capture`() {
        val user32 = mockk<User32>()
        val gdi32 = mockk<GDI32>()
        stubGetWindowRect(user32, 0, 0, 4, 3)
        every { user32.GetDC(any()) } returns WinDef.HDC()
        every { gdi32.CreateCompatibleDC(any()) } throws RuntimeException("gdi boom")

        assertNull(WindowsWindowCapture.captureWindowWith(user32, gdi32, 1L))
    }
}
