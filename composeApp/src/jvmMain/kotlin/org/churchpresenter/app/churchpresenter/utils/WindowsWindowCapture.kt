package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.GDI32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinUser
import java.awt.Rectangle
import java.awt.image.BufferedImage

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
        val windows = mutableListOf<WindowInfo>()
        try {
            User32.INSTANCE.EnumWindows({ hwnd, _ ->
                if (User32.INSTANCE.IsWindowVisible(hwnd)) {
                    val titleBuf = CharArray(512)
                    val len = User32.INSTANCE.GetWindowText(hwnd, titleBuf, titleBuf.size)
                    if (len > 0) {
                        val title = String(titleBuf, 0, len)
                        if (title.isNotBlank()) {
                            windows.add(WindowInfo(title, Pointer.nativeValue(hwnd.pointer)))
                        }
                    }
                }
                true // continue enumeration
            }, null)
        } catch (_: Throwable) {}
        return windows
    }

    fun getWindowBounds(hwnd: Long): Rectangle? {
        if (!isAvailable()) return null
        return try {
            val hwndPtr = WinDef.HWND(Pointer(hwnd))
            val rect = WinDef.RECT()
            if (User32.INSTANCE.GetWindowRect(hwndPtr, rect)) {
                Rectangle(rect.left, rect.top, rect.right - rect.left, rect.bottom - rect.top)
            } else null
        } catch (_: Throwable) { null }
    }

    fun captureWindow(hwnd: Long): BufferedImage? {
        if (!isAvailable()) return null
        return try {
            val hwndPtr = WinDef.HWND(Pointer(hwnd))
            val rect = WinDef.RECT()
            if (!User32.INSTANCE.GetWindowRect(hwndPtr, rect)) return null

            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            if (width <= 0 || height <= 0) return null

            val hdcWindow = User32.INSTANCE.GetDC(hwndPtr)
            if (hdcWindow == null) return null

            val hdcMem = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow)
            val hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, width, height)
            val hOld = GDI32.INSTANCE.SelectObject(hdcMem, hBitmap)

            // PrintWindow with PW_RENDERFULLCONTENT (0x2) for occluded capture
            User32.INSTANCE.PrintWindow(hwndPtr, hdcMem, 2)

            // Read pixels from bitmap
            val bmi = WinGDI.BITMAPINFO()
            bmi.bmiHeader.biSize = bmi.bmiHeader.size()
            bmi.bmiHeader.biWidth = width
            bmi.bmiHeader.biHeight = -height // top-down
            bmi.bmiHeader.biPlanes = 1
            bmi.bmiHeader.biBitCount = 32
            bmi.bmiHeader.biCompression = WinGDI.BI_RGB

            val bufferSize = width.toLong() * height * 4
            val buffer = Memory(bufferSize)
            GDI32.INSTANCE.GetDIBits(hdcMem, hBitmap, 0, height, buffer, bmi, WinGDI.DIB_RGB_COLORS)

            // Cleanup GDI objects
            GDI32.INSTANCE.SelectObject(hdcMem, hOld)
            GDI32.INSTANCE.DeleteObject(hBitmap)
            GDI32.INSTANCE.DeleteDC(hdcMem)
            User32.INSTANCE.ReleaseDC(hwndPtr, hdcWindow)

            // Convert BGRA buffer to BufferedImage
            val img = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            val pixels = IntArray(width * height)
            for (i in pixels.indices) {
                val bgra = buffer.getInt((i * 4).toLong())
                val b = (bgra shr 16) and 0xFF
                val g = (bgra shr 8) and 0xFF
                val r = bgra and 0xFF
                pixels[i] = (255 shl 24) or (r shl 16) or (g shl 8) or b
            }
            img.setRGB(0, 0, width, height, pixels, 0, width)
            img
        } catch (_: Throwable) { null }
    }
}
