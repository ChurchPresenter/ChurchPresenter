package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import io.mockk.every
import io.mockk.mockk
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [X11WindowCapture] wraps libX11/libXcomposite through JNA, so the real capture only runs on a
 * Linux box with an X server. The availability gate and every branch of the capture body are still
 * exercisable by injecting a fake [X11WindowCapture.X11] into the extracted captureWith/ximageToImage
 * — only the actual native calls stay out of reach headless.
 */
class X11WindowCaptureTest {

    private val isLinux = System.getProperty("os.name", "").lowercase().contains("linux")
    private val fakePtr = Pointer(1L)

    @Test
    fun `isAvailable is false off Linux and stable across calls`() {
        val first = X11WindowCapture.isAvailable()
        if (!isLinux) assertTrue(!first, "capture must report unavailable when not on Linux")
        assertEquals(first, X11WindowCapture.isAvailable())
    }

    private fun stubbedX11(width: Int, height: Int, pixel: Long = 0x00112233): X11WindowCapture.X11 {
        val lib = mockk<X11WindowCapture.X11>(relaxed = true)
        every { lib.XGetWindowAttributes(any(), any(), any()) } answers {
            thirdArg<X11WindowCapture.XWindowAttributes>().apply { this.width = width; this.height = height }
            1
        }
        every { lib.XCompositeNameWindowPixmap(any(), any()) } returns NativeLong(0x9999)
        every { lib.XGetImage(any(), any(), any(), any(), any(), any(), any(), any()) } returns fakePtr
        every { lib.XGetPixel(any(), any(), any()) } returns NativeLong(pixel)
        return lib
    }

    @Test
    fun `captureWith produces an opaque image of the window size`() {
        val lib = stubbedX11(width = 2, height = 2, pixel = 0x00112233)

        val img = assertNotNull(X11WindowCapture.captureWith(lib, fakePtr, windowId = 7L, redirected = mutableSetOf()))

        assertEquals(2, img.width)
        assertEquals(2, img.height)
        assertEquals(BufferedImage.TYPE_INT_ARGB, img.type)
        for (y in 0 until 2) for (x in 0 until 2) {
            assertEquals(0xFF112233.toInt(), img.getRGB(x, y), "BGR channels map and alpha is forced opaque")
        }
    }

    @Test
    fun `captureWith redirects a window once and reuses it thereafter`() {
        val lib = stubbedX11(2, 2)
        val redirected = mutableSetOf<Long>()

        X11WindowCapture.captureWith(lib, fakePtr, windowId = 42L, redirected = redirected)
        assertTrue(42L in redirected, "a fresh window is registered as redirected")

        X11WindowCapture.captureWith(lib, fakePtr, windowId = 42L, redirected = redirected)
        assertEquals(1, redirected.size, "an already-redirected window is not registered again")
    }

    @Test
    fun `captureWith returns null for a zero-area window`() {
        val lib = stubbedX11(width = 0, height = 0)
        assertNull(X11WindowCapture.captureWith(lib, fakePtr, 1L, mutableSetOf()))
    }

    @Test
    fun `captureWith returns null when the composite pixmap is unavailable`() {
        val lib = stubbedX11(2, 2)
        every { lib.XCompositeNameWindowPixmap(any(), any()) } returns NativeLong(0)
        assertNull(X11WindowCapture.captureWith(lib, fakePtr, 1L, mutableSetOf()))
    }

    @Test
    fun `captureWith returns null when the image cannot be read`() {
        val lib = stubbedX11(2, 2)
        every { lib.XGetImage(any(), any(), any(), any(), any(), any(), any(), any()) } returns null
        assertNull(X11WindowCapture.captureWith(lib, fakePtr, 1L, mutableSetOf()))
    }

    @Test
    fun `captureWith swallows a native failure into null`() {
        val lib = stubbedX11(2, 2)
        every { lib.XGetWindowAttributes(any(), any(), any()) } throws RuntimeException("x error")
        assertNull(X11WindowCapture.captureWith(lib, fakePtr, 1L, mutableSetOf()))
    }

    @Test
    fun `captureWindow returns null when capture is unavailable`() {
        if (!isLinux) assertNull(X11WindowCapture.captureWindow(123L))
    }

    private fun resetAvailableCache() {
        X11WindowCapture::class.java.getDeclaredField("available")
            .apply { isAccessible = true }.set(X11WindowCapture, null)
    }

    @Test
    fun `isAvailable probes the native libraries under linux and memoises the result`() {
        val realOs = System.getProperty("os.name")
        try {
            System.setProperty("os.name", "Linux")
            resetAvailableCache()
            val result = X11WindowCapture.isAvailable()
            assertEquals(result, X11WindowCapture.isAvailable(), "the probe runs once and is cached")
        } finally {
            System.setProperty("os.name", realOs)
            resetAvailableCache()
        }
    }

    @Test
    fun `ximageToImage reads row by row across the full width and height`() {
        val lib = mockk<X11WindowCapture.X11>()
        every { lib.XGetPixel(any(), any(), any()) } answers {
            val x = secondArg<Int>()
            val y = thirdArg<Int>()
            NativeLong(((x + 1) * 0x10 + (y + 1)).toLong())
        }

        val img = X11WindowCapture.ximageToImage(lib, fakePtr, w = 3, h = 2)

        assertEquals(3, img.width)
        assertEquals(2, img.height)
        assertEquals(0xFF000000.toInt() or (2 * 0x10 + 2), img.getRGB(1, 1))
        for (y in 0 until 2) for (x in 0 until 3) {
            assertEquals(0xFF, img.getRGB(x, y) ushr 24, "every pixel opaque at ($x,$y)")
        }
    }

    @Test
    fun `ximageToImage maps each pixel and forces opaque alpha`() {
        val lib = mockk<X11WindowCapture.X11>()
        every { lib.XGetPixel(any(), any(), any()) } returns NativeLong(0x00AABBCC)

        val img = X11WindowCapture.ximageToImage(lib, fakePtr, w = 1, h = 1)

        assertEquals(0xFFAABBCC.toInt(), img.getRGB(0, 0))
    }
}
