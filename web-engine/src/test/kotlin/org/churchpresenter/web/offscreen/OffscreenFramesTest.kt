package org.churchpresenter.web.offscreen

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.awt.Rectangle
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
 * The frame store: what a paint publishes (the view alone, or with its levels, together), the popup
 * over it, what retires and what closes, and that a closed store takes no more paints. The level
 * worker runs on its own thread, so each test waits for the frame it expects on the flow itself.
 */
class OffscreenFramesTest {
    private val stats = OffscreenStats()
    private val failures = mutableListOf<Throwable>()
    private var dipWidth = 960
    private val store = OffscreenFrames({ dipWidth }, rgba = false, stats = stats, onPaintFailed = { failures += it })

    @AfterTest
    fun closeStore() {
        store.close()()
    }

    private fun awaitFrame(test: (OffscreenFrame) -> Boolean): OffscreenFrame = runBlocking {
        withTimeout(WAIT_MS) { checkNotNull(store.frames.first { it != null && test(it) }) }
    }

    @Test
    fun `a narrow view is published from the paint with no levels`() {
        val buffer = paintBuffer(800, 450)
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), buffer, 800, 450)

        val frame = assertNotNull(store.frames.value)
        assertTrue(frame.levels.isEmpty())
        assertContentEquals(buffer.allBytes(), frame.view.bytes())
        assertEquals(1, stats.drain().paints)
    }

    @Test
    fun `a wide view is published only together with its levels`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 1920, 1080)), paintBuffer(1920, 1080), 1920, 1080)

        val frame = awaitFrame { it.levels.isNotEmpty() }
        assertEquals(listOf(960, 480), frame.levels.map { it.width })
        assertEquals(1, frame.levelSeq)
        assertEquals(1, frame.viewSeq)
    }

    @Test
    fun `an open popup is drawn over the view, in the view's pixels, until it closes`() {
        dipWidth = 400
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)
        val view = assertNotNull(store.frames.value).view

        store.onPopupSize(Rectangle(10, 20, 100, 50))
        store.onPopupShow(true)
        store.onPaint(true, arrayOf(Rectangle(0, 0, 200, 100)), paintBuffer(200, 100), 200, 100)

        val shown = assertNotNull(assertNotNull(store.frames.value).popup)
        assertSame(view, assertNotNull(store.frames.value).view, "a popup paint keeps the view")
        assertEquals(listOf(20f, 40f, 200f, 100f), shown.rect.let { listOf(it.left, it.top, it.width, it.height) })

        store.onPopupShow(false)
        assertNull(assertNotNull(store.frames.value).popup)
        assertFalse(shown.image.isClosed, "a frame may still be drawing the closed popup")
    }

    @Test
    fun `a replaced view closes only once it has retired`() {
        val views = (1..RETIRED_KEPT + 2).map { n ->
            store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450, seed = n), 800, 450)
            assertNotNull(store.frames.value).view
        }

        assertFalse(views.last().isClosed)
        assertFalse(views[views.size - 2].isClosed, "the frame before the current one is still kept")
        assertTrue(views.first().isClosed, "a view ${RETIRED_KEPT + 1} frames old is freed")
    }

    @Test
    fun `a closed store clears its frame and takes no more paints, and frees what it held`() {
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)
        val last = assertNotNull(store.frames.value).view

        val free = store.close()
        store.onPaint(false, arrayOf(Rectangle(0, 0, 800, 450)), paintBuffer(800, 450), 800, 450)

        assertNull(store.frames.value)
        assertFalse(last.isClosed, "nothing is freed until the caller says no window can draw it")
        free()
        assertTrue(last.isClosed)
    }

    @Test
    fun `the cursor and a failed paint are passed on`() {
        store.onCursorChange(3)
        val failure = IllegalStateException("paint")
        store.onPaintFailed(failure)

        assertEquals(3, store.cursor.value)
        assertEquals(listOf<Throwable>(failure), failures)
    }

    private companion object {
        const val WAIT_MS = 5_000L
    }
}
