package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * The screen-capture backends are per-platform and talk to native APIs (Win32 GDI via JNA, X11 via
 * libX11). Only one can ever be functional on a given machine, so what is testable everywhere is
 * their **availability gate**: each must report unavailable on a foreign platform and refuse to do
 * anything, rather than throwing an UnsatisfiedLinkError up into the Canvas source picker.
 *
 * The capture paths themselves aren't exercised — they need a real window handle and a real display
 * server, which a headless CI runner doesn't have.
 */
class PlatformCaptureTest {

    private val osName = System.getProperty("os.name", "").lowercase()
    private val isWindows = osName.contains("win")
    private val isLinux = !isWindows && !osName.contains("mac")

    @Test
    fun `windows capture reports unavailable when not on windows`() {
        if (isWindows) return // on Windows availability depends on the real OS; nothing to assert
        assertFalse(WindowsWindowCapture.isAvailable(), "must not claim availability off-platform")
    }

    @Test
    fun `windows capture degrades instead of throwing when unavailable`() {
        if (isWindows) return
        // These are reached from the Canvas source picker; an exception there kills the dialog.
        assertFalse(WindowsWindowCapture.listWindows().isNotEmpty())
        assertNull(WindowsWindowCapture.getWindowBounds(0L))
        assertNull(WindowsWindowCapture.captureWindow(0L))
    }

    @Test
    fun `x11 capture reports unavailable when not on a linux display server`() {
        if (isLinux) return
        assertFalse(X11WindowCapture.isAvailable(), "must not claim availability off-platform")
    }

    @Test
    fun `x11 capture degrades instead of throwing when unavailable`() {
        if (isLinux) return
        assertNull(X11WindowCapture.captureWindow(0L))
    }

    @Test
    fun `at most one capture backend claims availability`() {
        // Both reporting available would mean the platform detection is broken; the Canvas picker
        // would then offer two different window lists for the same machine.
        val available = listOfNotNull(
            runCatching { WindowsWindowCapture.isAvailable() }.getOrDefault(false).takeIf { it },
            runCatching { X11WindowCapture.isAvailable() }.getOrDefault(false).takeIf { it },
        )
        kotlin.test.assertTrue(available.size <= 1, "more than one capture backend claims availability")
    }
}
