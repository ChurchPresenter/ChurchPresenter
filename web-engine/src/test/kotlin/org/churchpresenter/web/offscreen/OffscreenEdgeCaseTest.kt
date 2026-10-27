package org.churchpresenter.web.offscreen

import androidx.compose.ui.unit.IntSize
import org.churchpresenter.web.presenter.JcefShutdownHook
import org.jetbrains.skia.ColorType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.Canvas
import java.awt.Rectangle
import java.awt.event.KeyEvent
import java.lang.ref.Reference
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * Edge cases of the pieces already covered: rects outside the frame and rects big enough to split,
 * a popup repainted at the same size or shown without a place, a closed page asked to resize, the
 * real JDK hook table, and the keys each platform leaves alone.
 */
class OffscreenEdgeCaseTest {
    private val page = testPage()
    private val store = OffscreenFrames({ 800 }, rgba = false, stats = OffscreenStats(), onPaintFailed = {})

    @AfterTest
    fun close() {
        page.close()
        store.close()()
    }

    @Test
    fun `a rect outside the frame copies nothing and a large one is split across the threads`() = withPool { pool ->
        val ring = FrameRing(2000, 1000, swizzle = false, colorType = ColorType.BGRA_8888)
        val source = paintBuffer(2000, 1000)
        repeat(RING_SIZE) { ring.write(arrayOf(Rectangle(0, 0, 2000, 1000)), source, pool) }

        // A round of small paints, so no buffer still has a full-frame paint to catch up on.
        repeat(RING_SIZE) { ring.write(arrayOf(Rectangle(5000, 5000, 10, 10)), source, pool) }
        val written = ring.write(arrayOf(Rectangle(0, 0, 1200, 900), Rectangle(-50, -50, 10, 10)), source, pool)

        assertFalse(written.full)
        assertContentEquals(source.allBytes(), written.image.bytes())
        Reference.reachabilityFence(ring)
    }

    @Test
    fun `a popup repainted at the same size reuses its copy, and one with no place is not drawn`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)
        store.onPopupShow(true)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20), 50, 20)
        assertNull(assertNotNull(store.frames.value).popup, "Chromium has not said where it goes yet")

        store.onPopupSize(Rectangle(0, 0, 50, 20))
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20, seed = 2), 50, 20)
        assertNotNull(assertNotNull(store.frames.value).popup)

        store.onPopupShow(false)
        store.onPopupShow(false)
        assertNull(assertNotNull(store.frames.value).popup)
    }

    @Test
    fun `a paint of several rects marks all of them for the levels`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 1920, 1080)), paintBuffer(1920, 1080), 1920, 1080)
        val rects = arrayOf(Rectangle(0, 0, 10, 10), Rectangle(100, 100, 10, 10))
        store.onPaint(false, rects, paintBuffer(1920, 1080), 1920, 1080)
        val frame = runBlocking {
            withTimeout(5_000) { checkNotNull(store.frames.first { it?.levels?.isNotEmpty() == true }) }
        }
        assertEquals(1920, frame.view.width)
    }

    @Test
    fun `a closed page takes no size and the same size twice changes nothing`() {
        val surface = Any()
        page.requestSize(surface, IntSize(1920, 1080))
        page.applySize()
        page.applySize()
        assertEquals(1920, page.browser.viewWidth)

        page.close()
        page.requestSize(surface, IntSize(640, 360))
        page.releaseSize(surface)
        page.applySize()
        assertEquals(1920, page.browser.viewWidth)
    }

    @Test
    fun `the JDK's hook table is read, and an ordinary hook is not jcefmaven's`() {
        val hook = Thread {}
        Runtime.getRuntime().addShutdownHook(hook)
        try {
            assertTrue(hook in assertNotNull(JcefShutdownHook.registeredHooks()))
            assertFalse(JcefShutdownHook.isJcefmavenHook(hook))
            assertFalse(JcefShutdownHook.isJcefmavenHook(Thread()), "a thread with no task")
        } finally {
            Runtime.getRuntime().removeShutdownHook(hook)
        }
        assertEquals("kept", JcefShutdownHook.withoutJcefmavenHook { "kept" })
    }

    @Test
    fun `keys the platform fixes leave alone pass through untouched`() {
        val source = Canvas()
        val pressedEnter = KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ENTER, '\n')
        assertEquals('\n', windowsKeyEvent(pressedEnter).keyChar)
        val typedA = KeyEvent(source, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a')
        assertSame(typedA, windowsKeyEvent(typedA))
        assertEquals('a', typedA.keyChar)

        val latin1 = KeyEvent(source, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_E, 'é')
        assertSame(latin1, linuxKeyEvent(latin1), "é is its own keysym")
        assertEquals(0xffcb, keysymFor(KeyEvent.VK_F14, KeyEvent.KEY_LOCATION_STANDARD))
        assertEquals(0xffb0, keysymFor(KeyEvent.VK_NUMPAD0, KeyEvent.KEY_LOCATION_NUMPAD))
    }

    @Test
    fun `a script for the page's focused frame is taken without the native library`() {
        BrowserKeys(page.browser).run("void 0")
        val typed = KeyEvent(page.browser.eventSource, KeyEvent.KEY_TYPED, 0, 0, KeyEvent.VK_UNDEFINED, 'a')
        BrowserKeys(page.browser).key(typed)
    }

    @Test
    fun `two shown pictures with different popups, or one without, are not the same`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)
        store.onPopupSize(Rectangle(0, 0, 50, 20))
        store.onPopupShow(true)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20), 50, 20)
        val first = Shown.of(assertNotNull(store.frames.value), small = false)
        store.onPopupSize(Rectangle(5, 5, 50, 20))
        store.onPaint(true, arrayOf(Rectangle(0, 0, 50, 20)), paintBuffer(50, 20), 50, 20)
        val moved = Shown.of(assertNotNull(store.frames.value), small = false)
        store.onPopupShow(false)
        val none = Shown.of(assertNotNull(store.frames.value), small = false)

        assertFalse(Shown.drawsTheSame(first, moved))
        assertFalse(Shown.drawsTheSame(first, none))
        assertFalse(Shown.drawsTheSame(none, first))
    }

    @Test
    fun `with no picture wide enough the largest is drawn`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)
        val view = assertNotNull(store.frames.value).view
        assertSame(view, pick(listOf(view), 4000f))
    }
}
