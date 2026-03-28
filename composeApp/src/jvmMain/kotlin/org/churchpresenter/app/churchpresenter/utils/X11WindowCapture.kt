package org.churchpresenter.app.churchpresenter.utils

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.awt.image.BufferedImage

/**
 * X11 Composite extension window capture via JNA.
 * Captures window content even when occluded by other windows.
 *
 * Uses XCompositeRedirectWindow to get an off-screen pixmap,
 * then XGetImage to read the pixel data.
 */
object X11WindowCapture {

    private var available: Boolean? = null
    private var display: Pointer? = null

    // X11 constants
    private const val ZPixmap = 2
    private const val CompositeRedirectAutomatic = 1

    @Structure.FieldOrder("width", "height", "xoffset", "format", "data",
        "byte_order", "bitmap_unit", "bitmap_bit_order", "bitmap_pad",
        "depth", "bytes_per_line", "bits_per_pixel",
        "red_mask", "green_mask", "blue_mask",
        "obdata", "f")
    open class XImage : Structure() {
        @JvmField var width: Int = 0
        @JvmField var height: Int = 0
        @JvmField var xoffset: Int = 0
        @JvmField var format: Int = 0
        @JvmField var data: Pointer? = null
        @JvmField var byte_order: Int = 0
        @JvmField var bitmap_unit: Int = 0
        @JvmField var bitmap_bit_order: Int = 0
        @JvmField var bitmap_pad: Int = 0
        @JvmField var depth: Int = 0
        @JvmField var bytes_per_line: Int = 0
        @JvmField var bits_per_pixel: Int = 0
        @JvmField var red_mask: NativeLong = NativeLong(0)
        @JvmField var green_mask: NativeLong = NativeLong(0)
        @JvmField var blue_mask: NativeLong = NativeLong(0)
        @JvmField var obdata: Pointer? = null
        @JvmField var f: Pointer? = null
    }

    @Structure.FieldOrder("x", "y", "width", "height", "border_width", "depth", "visual",
        "root", "clazz", "bit_gravity", "win_gravity", "backing_store",
        "backing_planes", "backing_pixel", "save_under", "colormap",
        "map_installed", "map_state", "all_event_masks", "your_event_mask",
        "do_not_propagate_mask", "override_redirect", "screen")
    open class XWindowAttributes : Structure() {
        @JvmField var x: Int = 0
        @JvmField var y: Int = 0
        @JvmField var width: Int = 0
        @JvmField var height: Int = 0
        @JvmField var border_width: Int = 0
        @JvmField var depth: Int = 0
        @JvmField var visual: Pointer? = null
        @JvmField var root: NativeLong = NativeLong(0)
        @JvmField var clazz: Int = 0
        @JvmField var bit_gravity: Int = 0
        @JvmField var win_gravity: Int = 0
        @JvmField var backing_store: Int = 0
        @JvmField var backing_planes: NativeLong = NativeLong(0)
        @JvmField var backing_pixel: NativeLong = NativeLong(0)
        @JvmField var save_under: Int = 0
        @JvmField var colormap: NativeLong = NativeLong(0)
        @JvmField var map_installed: Int = 0
        @JvmField var map_state: Int = 0
        @JvmField var all_event_masks: NativeLong = NativeLong(0)
        @JvmField var your_event_masks: NativeLong = NativeLong(0)
        @JvmField var do_not_propagate_mask: NativeLong = NativeLong(0)
        @JvmField var override_redirect: Int = 0
        @JvmField var screen: Pointer? = null
    }

    private interface X11 : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XGetWindowAttributes(display: Pointer, window: NativeLong, attrs: XWindowAttributes): Int
        fun XGetImage(display: Pointer, drawable: NativeLong, x: Int, y: Int,
                      width: Int, height: Int, planeMask: NativeLong, format: Int): Pointer?
        fun XDestroyImage(image: Pointer): Int
        fun XGetPixel(image: Pointer, x: Int, y: Int): NativeLong
        fun XCompositeRedirectWindow(display: Pointer, window: NativeLong, update: Int)
        fun XCompositeUnredirectWindow(display: Pointer, window: NativeLong, update: Int)
        fun XCompositeNameWindowPixmap(display: Pointer, window: NativeLong): NativeLong
        fun XFreePixmap(display: Pointer, pixmap: NativeLong)
    }

    private var x11: X11? = null
    private val redirectedWindows = mutableSetOf<Long>()

    fun isAvailable(): Boolean {
        if (available == null) {
            available = try {
                val osName = System.getProperty("os.name", "").lowercase()
                if (!osName.contains("linux")) {
                    false
                } else {
                    x11 = Native.load("X11", X11::class.java)
                    // Test that Xcomposite is also loadable
                    Native.load("Xcomposite", X11::class.java)
                    display = x11?.XOpenDisplay(null)
                    display != null
                }
            } catch (_: Throwable) {
                false
            }
        }
        return available ?: false
    }

    fun captureWindow(windowId: Long): BufferedImage? {
        if (!isAvailable()) return null
        val dpy = display ?: return null
        val lib = x11 ?: return null
        val wid = NativeLong(windowId)

        return try {
            // Redirect window to off-screen pixmap if not already
            if (windowId !in redirectedWindows) {
                lib.XCompositeRedirectWindow(dpy, wid, CompositeRedirectAutomatic)
                redirectedWindows.add(windowId)
            }

            // Get window attributes for size
            val attrs = XWindowAttributes()
            lib.XGetWindowAttributes(dpy, wid, attrs)
            val w = attrs.width
            val h = attrs.height
            if (w <= 0 || h <= 0) return null

            // Get the composite pixmap
            val pixmap = lib.XCompositeNameWindowPixmap(dpy, wid)
            if (pixmap.toLong() == 0L) return null

            // Read pixels from the pixmap
            val allPlanes = NativeLong(-1L)
            val imagePtr = lib.XGetImage(dpy, pixmap, 0, 0, w, h, allPlanes, ZPixmap)
            lib.XFreePixmap(dpy, pixmap)

            if (imagePtr == null) return null

            // Convert to BufferedImage
            val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val pixel = lib.XGetPixel(imagePtr, x, y).toLong()
                    val r = ((pixel shr 16) and 0xFF).toInt()
                    val g = ((pixel shr 8) and 0xFF).toInt()
                    val b = (pixel and 0xFF).toInt()
                    img.setRGB(x, y, (255 shl 24) or (r shl 16) or (g shl 8) or b)
                }
            }

            lib.XDestroyImage(imagePtr)
            img
        } catch (_: Throwable) {
            null
        }
    }
}
