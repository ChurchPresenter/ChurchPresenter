package org.churchpresenter.canvas

import androidx.compose.runtime.staticCompositionLocalOf
import org.churchpresenter.ui.WindowInfo

/**
 * What the camera and screen-capture editors ask the machine about it.
 *
 * Both listings shell out — `xprop`, `osascript`, the Win32 window list, `ffmpeg -list_options` —
 * and both answer with whatever hardware and desktop the machine happens to have. That makes the
 * controls built on top of them untestable in place and, on macOS, capable of raising an
 * accessibility prompt that blocks a headless run outright.
 *
 * So the editors ask this instead, and the app hands them [Platform]. A test hands them a fixed
 * list and gets the parts that matter: the window a source is pointed at, the handle stored beside
 * its title, and what happens when the machine reports nothing at all.
 *
 * The same shape as [CanvasDeckLink] and [CanvasFilePicker], for the same reason — and public
 * for the same reason too: the test fixtures that render these editors are not a friend of this
 * module's `main`, so a stand-in has to be nameable from outside it.
 */
interface CanvasDeviceListing {

    /** Every window currently open on the desktop. */
    fun openWindows(): List<WindowInfo>

    /** The capture formats a camera offers, or empty when it offers none the app could read. */
    fun cameraFormats(devicePath: String, deviceName: String): List<CameraFormat>

    companion object {
        /** What the app uses: the real desktop, and the real device. */
        val Platform: CanvasDeviceListing = object : CanvasDeviceListing {
            override fun openWindows(): List<WindowInfo> = listOpenWindows()
            override fun cameraFormats(devicePath: String, deviceName: String): List<CameraFormat> =
                listCameraFormats(devicePath, deviceName)
        }

        /** A machine with no windows and no cameras — the default a test gets for free. */
        val None: CanvasDeviceListing = object : CanvasDeviceListing {
            override fun openWindows(): List<WindowInfo> = emptyList()
            override fun cameraFormats(devicePath: String, deviceName: String): List<CameraFormat> = emptyList()
        }
    }
}

/** The listing the editors below this point should use. The app provides [CanvasDeviceListing.Platform]. */
val LocalCanvasDeviceListing = staticCompositionLocalOf { CanvasDeviceListing.Platform }
