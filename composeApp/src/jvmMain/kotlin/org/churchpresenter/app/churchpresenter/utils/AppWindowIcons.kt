package org.churchpresenter.app.churchpresenter.utils

import java.awt.AWTEvent
import java.awt.Image
import java.awt.RenderingHints
import java.awt.Toolkit
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.image.BufferedImage
import java.io.IOException
import javax.imageio.ImageIO
import kotlin.math.abs

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

    /** The sizes Windows asks a window's icon for at 100% scaling: title bar and taskbar. */
    private val TITLE_BAR_SIZES = listOf(16, 32)

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

    /** Gives [window] the app icon's frames. */
    internal fun applyTo(window: Window) {
        if (frames.isNotEmpty()) window.iconImages = frames
    }

    /**
     * Scales each frame once the way AWT will for a window's title bar and taskbar, here rather than
     * in the first window's opening: AWT draws a frame into an image of the size the system asks
     * for, and the first such draw sets up Java2D's drawing loops -- a quarter of a second on the
     * event thread, as the window opened. Returns how many frames were drawn.
     */
    internal fun warmScaling(sizes: List<Int> = TITLE_BAR_SIZES): Int {
        var drawn = 0
        for (size in sizes) {
            val source = frames.minByOrNull { abs(it.getWidth(null) - size) } ?: return drawn
            val target = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
            val g = target.createGraphics()
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
                if (g.drawImage(source, 0, 0, size, size, null)) drawn++
            } finally {
                g.dispose()
            }
        }
        return drawn
    }

    /** From now on, every window this app opens gets the app icon's frames. */
    fun install() {
        if (frames.isEmpty()) return
        Thread({ warmScaling() }, "window-icon-warm-up").apply {
            isDaemon = true
            start()
        }
        Toolkit.getDefaultToolkit().addAWTEventListener(
            { event ->
                if (event.id == WindowEvent.WINDOW_OPENED) (event.source as? Window)?.let(::applyTo)
            },
            AWTEvent.WINDOW_EVENT_MASK,
        )
    }
}
