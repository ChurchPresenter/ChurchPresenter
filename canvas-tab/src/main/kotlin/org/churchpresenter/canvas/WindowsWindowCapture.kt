package org.churchpresenter.canvas

import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import java.awt.Rectangle
import java.awt.image.BufferedImage

private const val BITS_PER_PIXEL: Short = 32
private const val OPAQUE_ALPHA = 255
private const val ALPHA_SHIFT = 24
private const val RED_SHIFT = 16
private const val GREEN_SHIFT = 8

/**
 * Windows window capture using JNA (user32.dll + gdi32.dll).
 * Lists visible windows via EnumWindows and captures window content
 * via PrintWindow (works even when occluded).
 */
object WindowsWindowCapture {

    data class WindowInfo(val title: String, val hwnd: Long)

    private var available: Boolean? = null

    fun isAvailable(): Boolean {
        if (available == null) {
            available = try {
                val osName = System.getProperty("os.name", "").lowercase()
                if (!osName.contains("win")) {
                    false
                } else {
                    User32.INSTANCE != null
                }
            } catch (_: Throwable) {
                false
            }
        }
        return available ?: false
    }

    fun listWindows(): List<WindowInfo> {
        if (!isAvailable()) return emptyList()
        return listWindowsWith(User32.INSTANCE)
    }

    internal fun listWindowsWith(user32: User32): List<WindowInfo> {
        val windows = mutableListOf<WindowInfo>()
        try {
            user32.EnumWindows({ hwnd, _ ->
                if (user32.IsWindowVisible(hwnd)) {
                    val titleBuf = CharArray(512)
                    val len = user32.GetWindowText(hwnd, titleBuf, titleBuf.size)
                    titleOrNull(titleBuf, len)?.let {
                        windows.add(WindowInfo(it, Pointer.nativeValue(hwnd.pointer)))
                    }
                }
                true // continue enumeration
            }, null)
        } catch (_: Throwable) {}
        return windows
    }

    internal fun titleOrNull(titleBuf: CharArray, len: Int): String? {
        if (len <= 0) return null
        val title = String(titleBuf, 0, len)
        return if (title.isNotBlank()) title else null
    }

    fun getWindowBounds(hwnd: Long): Rectangle? {
        if (!isAvailable()) return null
        return getWindowBoundsWith(User32.INSTANCE, hwnd)
    }

    internal fun getWindowBoundsWith(user32: User32, hwnd: Long): Rectangle? {
        return runCatching {
            val hwndPtr = WinDef.HWND(Pointer(hwnd))
            val rect = WinDef.RECT()
            if (user32.GetWindowRect(hwndPtr, rect)) {
                rectToBounds(rect.left, rect.top, rect.right, rect.bottom)
            } else null
        }.getOrNull()
    }

    fun captureWindow(hwnd: Long): BufferedImage? {
        if (!isAvailable()) return null
        return captureWindowWith(User32.INSTANCE, GDI32.INSTANCE, hwnd)
    }

    internal fun captureWindowWith(user32: User32, gdi32: GDI32, hwnd: Long): BufferedImage? {
        return runCatching {
            val hwndPtr = WinDef.HWND(Pointer(hwnd))
            val rect = WinDef.RECT()
            if (!user32.GetWindowRect(hwndPtr, rect)) null
            else printWindowToImage(
                user32, gdi32, hwndPtr,
                width = rect.right - rect.left,
                height = rect.bottom - rect.top
            )
        }.getOrNull()
    }

    private fun printWindowToImage(
        user32: User32,
        gdi32: GDI32,
        hwndPtr: WinDef.HWND,
        width: Int,
        height: Int
    ): BufferedImage? {
        if (width <= 0 || height <= 0) return null
        val hdcWindow = user32.GetDC(hwndPtr) ?: return null

        val hdcMem = gdi32.CreateCompatibleDC(hdcWindow)
        val hBitmap = gdi32.CreateCompatibleBitmap(hdcWindow, width, height)
        val hOld = gdi32.SelectObject(hdcMem, hBitmap)

        // PrintWindow with PW_RENDERFULLCONTENT (0x2) for occluded capture
        user32.PrintWindow(hwndPtr, hdcMem, 2)

        // Read pixels from bitmap
        val bmi = WinGDI.BITMAPINFO()
        bmi.bmiHeader.biSize = bmi.bmiHeader.size()
        bmi.bmiHeader.biWidth = width
        bmi.bmiHeader.biHeight = -height // top-down
        bmi.bmiHeader.biPlanes = 1
        bmi.bmiHeader.biBitCount = BITS_PER_PIXEL
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB

        val bufferSize = width.toLong() * height * 4
        val buffer = Memory(bufferSize)
        gdi32.GetDIBits(hdcMem, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS)

        // Cleanup GDI objects
        gdi32.SelectObject(hdcMem, hOld)
        gdi32.DeleteObject(hBitmap)
        gdi32.DeleteDC(hdcMem)
        user32.ReleaseDC(hwndPtr, hdcWindow)

        return bgraBufferToImage(buffer, width, height)
    }

    internal fun rectToBounds(left: Int, top: Int, right: Int, bottom: Int): Rectangle =
        Rectangle(left, top, right - left, bottom - top)

    internal fun bgraBufferToImage(buffer: Pointer, width: Int, height: Int): BufferedImage {
        val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val bgra = buffer.getInt((i * 4).toLong())
            val b = (bgra shr 16) and 0xFF
            val g = (bgra shr 8) and 0xFF
            val r = bgra and 0xFF
            pixels[i] = (OPAQUE_ALPHA shl ALPHA_SHIFT) or (r shl RED_SHIFT) or (g shl GREEN_SHIFT) or b
        }
        img.setRGB(0, 0, width, height, pixels, 0, width)
        return img
    }
}
