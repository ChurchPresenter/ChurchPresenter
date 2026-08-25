package org.churchpresenter.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The X11 structs the screen-capture code maps onto native memory.
 *
 * These are JNA `Structure` subclasses, and their `@FieldOrder` has to list every field in the exact
 * order the C header declares them. Get one wrong and JNA reads the right bytes into the wrong
 * fields: a capture comes back with its width where its depth should be, and the failure looks like
 * a corrupt image rather than a layout bug. JNA validates the order against the declared fields when
 * a struct is first constructed, so simply building one is the check.
 *
 * Nothing else constructs them off Linux, which is why they had no coverage at all.
 */
class X11StructLayoutTest {

    @Test
    fun `an XImage declares every field its layout names`() {
        // Throws if @FieldOrder and the declared fields disagree.
        val image = X11WindowCapture.XImage()

        assertEquals(0, image.width)
        assertEquals(0, image.height)
        assertEquals(0, image.depth)
    }

    @Test
    fun `an XImage's dimensions and pixel layout can be set`() {
        val image = X11WindowCapture.XImage().apply {
            width = 1920
            height = 1080
            depth = 24
            bits_per_pixel = 32
            bytes_per_line = 1920 * 4
        }

        // The four fields the capture path actually reads back off a real grab.
        assertEquals(1920, image.width)
        assertEquals(1080, image.height)
        assertEquals(32, image.bits_per_pixel)
        assertEquals(1920 * 4, image.bytes_per_line)
    }

    @Test
    fun `an XImage reports a non-zero native size`() {
        // If the field order were wrong JNA would not get this far.
        assertTrue(X11WindowCapture.XImage().size() > 0)
    }
}
