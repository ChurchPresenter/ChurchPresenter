package org.churchpresenter.app.churchpresenter.utils

import java.awt.AWTEvent
import java.awt.Image
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.AWTEventListener
import java.awt.event.WindowEvent
import java.io.IOException
import javax.imageio.ImageIO

/**
 * The app icon every one of the app's windows shows in its title bar and in Alt+Tab.
 *
 * Windows used to be given the vector `ic_app_icon`, which Java rasterises at 16 and 32 px on the
 * fly. That was a third drawing of the icon, different from both the taskbar's `icon.ico` and the
 * macOS icon, and soft at the small sizes Windows draws at 100% scaling. Each window now gets the
 * same pixel frames `icon.ico` is built from (`composeApp/tools/generate_windows_icon.py`), at
 * every size, and the system picks the one it needs rather than scaling one.
 *
 * Installed once, for every window as it opens -- including windows that never named an icon.
 */
internal object AppWindowIcons {

    /** The frames `composeApp/tools/generate_windows_icon.py` writes under `:icons`' `app-icon/`. */
    internal val SIZES = listOf(16, 20, 24, 32, 40, 48, 64, 128, 256)

    /** Every frame that could be read, smallest first; empty only if the resources are missing. */
    internal val frames: List<Image> by lazy {
        SIZES.mapNotNull { size ->
            try {
                AppWindowIcons::class.java.getResourceAsStream("/app-icon/icon-$size.png")?.use(ImageIO::read)
            } catch (_: IOException) {
                null
            }
        }
    }

    /** From now on, every window this app opens gets the app icon's frames. */
    fun install(): AWTEventListener? = install(Toolkit.getDefaultToolkit(), frames)

    internal fun install(toolkit: Toolkit, frames: List<Image>): AWTEventListener? {
        if (frames.isEmpty()) return null
        val listener = AWTEventListener { event -> openedWindow(event)?.iconImages = frames }
        toolkit.addAWTEventListener(listener, AWTEvent.WINDOW_EVENT_MASK)
        return listener
    }

    internal fun openedWindow(event: AWTEvent): Window? =
        if (event.id == WindowEvent.WINDOW_OPENED) event.source as? Window else null
}
