package org.churchpresenter.canvas

import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ScreenGrabTest {

    /** A screen that records what it was asked for and hands back a picture of the right size. */
    private class FakeScreen(
        override val available: Boolean = true,
        private val windowPicture: BufferedImage? = null,
        private val bounds: Rectangle? = Rectangle(10, 20, 30, 40),
    ) : ScreenGrabber {
        val windowIds = mutableListOf<Long>()
        val titles = mutableListOf<String>()
        val captured = mutableListOf<Rectangle>()

        override fun captureWindow(windowId: Long): BufferedImage? = windowPicture.also { windowIds += windowId }

        override fun windowBounds(title: String): Rectangle? = bounds.also { titles += title }

        override fun capture(rect: Rectangle): BufferedImage {
            captured += rect
            return BufferedImage(rect.width, rect.height, BufferedImage.TYPE_INT_ARGB)
        }
    }

    private fun spec(
        mode: String = "region",
        width: Int = 640,
        height: Int = 360,
        title: String = "",
        id: String = "",
    ) = ScreenCaptureSpec(
        mode, x = 5, y = 6, width = width, height = height, intervalMs = 100, windowTitle = title, windowId = id,
    )

    @Test
    fun `a machine that cannot grab the screen grabs nothing`() {
        val screen = FakeScreen(available = false)

        assertNull(grabScreen(spec(), screen))
        assertTrue(screen.captured.isEmpty())
    }

    @Test
    fun `a region is grabbed exactly as given`() {
        val screen = FakeScreen()

        val image = grabScreen(spec(), screen)

        assertEquals(listOf(Rectangle(5, 6, 640, 360)), screen.captured)
        assertEquals(640, image?.width)
    }

    @Test
    fun `an empty region is not grabbed`() {
        val screen = FakeScreen()

        assertNull(grabScreen(spec(width = 0), screen))
        assertNull(grabScreen(spec(height = 0), screen))
        assertTrue(screen.captured.isEmpty())
    }

    @Test
    fun `a window the platform can capture is taken from the platform, even when covered`() {
        val picture = BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB)
        val screen = FakeScreen(windowPicture = picture)

        val image = grabScreen(spec(mode = "window", title = "Lyrics", id = "0x1f"), screen)

        assertSame(picture, image)
        assertEquals(listOf(31L), screen.windowIds)
        assertTrue(screen.captured.isEmpty())
    }

    @Test
    fun `a window the platform cannot capture falls back to where it sits on screen`() {
        val screen = FakeScreen()

        grabScreen(spec(mode = "window", title = "Lyrics", id = "0x1f"), screen)

        assertEquals(listOf("Lyrics"), screen.titles)
        assertEquals(listOf(Rectangle(10, 20, 30, 40)), screen.captured)
    }

    @Test
    fun `a window id that is not hex is tried as window zero`() {
        val screen = FakeScreen()

        grabScreen(spec(mode = "window", title = "Lyrics", id = "zzz"), screen)

        assertEquals(listOf(0L), screen.windowIds)
    }

    @Test
    fun `a window known only by its title is grabbed where it sits`() {
        val screen = FakeScreen()

        grabScreen(spec(mode = "window", title = "Lyrics"), screen)

        assertTrue(screen.windowIds.isEmpty())
        assertEquals(listOf(Rectangle(10, 20, 30, 40)), screen.captured)
    }

    @Test
    fun `a window that is not on screen grabs nothing`() {
        val screen = FakeScreen(bounds = null)

        assertNull(grabScreen(spec(mode = "window", title = "Gone"), screen))
        assertTrue(screen.captured.isEmpty())
    }

    @Test
    fun `window mode with no title or id grabs the region`() {
        val screen = FakeScreen()

        grabScreen(spec(mode = "window"), screen)

        assertEquals(listOf(Rectangle(5, 6, 640, 360)), screen.captured)
    }

    @Test
    fun `the real screen is unavailable in a headless test run`() {
        assertNull(grabScreen(spec()))
    }
}
