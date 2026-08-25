package org.churchpresenter.canvas

import org.churchpresenter.core.models.scene.SceneSource
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * What a screen-capture source grabs on each tick.
 *
 * The capture loop itself cannot run in a test — `Robot` throws in a headless JVM — but nothing it
 * decides needs a screen: whether to follow a window or a fixed area, where that window is now, and
 * whether the result has any pixels. Those are what go wrong. A window that has been closed since
 * the operator picked it, an area dragged to zero width, a source half-configured before a window
 * was chosen: each has to end as "nothing this tick" rather than as an exception inside the loop,
 * because the loop's only handler is a bare `catch` that stops the capture for good.
 */
class ScreenCaptureAreaTest {

    private fun capture(
        mode: String = "area",
        x: Int = 0, y: Int = 0, w: Int = 1920, h: Int = 1080,
        title: String = "", id: String = "",
    ) = SceneSource.ScreenCaptureSource(
        id = "cap", name = "Stage",
        captureMode = mode, captureX = x, captureY = y, captureWidth = w, captureHeight = h,
        windowTitle = title, windowId = id,
    )

    private val noWindows: (String) -> Rectangle? = { null }

    // ── A fixed area ────────────────────────────────────────────────────────────

    @Test
    fun `an area source grabs exactly the rectangle it was given`() {
        val area = captureAreaFor(capture(x = 100, y = 200, w = 640, h = 480), noWindows)

        assertEquals(Rectangle(100, 200, 640, 480), area)
    }

    @Test
    fun `an area dragged to no width grabs nothing`() {
        assertNull(captureAreaFor(capture(w = 0), noWindows))
    }

    @Test
    fun `an area dragged to no height grabs nothing`() {
        assertNull(captureAreaFor(capture(h = 0), noWindows))
    }

    @Test
    fun `a negative size grabs nothing rather than a mirrored rectangle`() {
        assertNull(captureAreaFor(capture(w = -100), noWindows))
        assertNull(captureAreaFor(capture(h = -100), noWindows))
    }

    @Test
    fun `an area source never asks where any window is`() {
        var asked = 0

        captureAreaFor(capture()) { asked++; null }

        assertEquals(0, asked, "looking a window up shells out to the platform on every tick")
    }

    // ── Following a window ──────────────────────────────────────────────────────

    @Test
    fun `a window source grabs where that window currently is`() {
        val where = Rectangle(30, 40, 800, 600)

        val area = captureAreaFor(capture(mode = "window", title = "Slides"), { if (it == "Slides") where else null })

        assertEquals(where, area, "the window is followed as it moves, not captured where it started")
    }

    @Test
    fun `a window that has since been closed grabs nothing`() {
        assertNull(captureAreaFor(capture(mode = "window", title = "Gone"), noWindows))
    }

    @Test
    fun `a window reported with no size grabs nothing`() {
        // A minimised window on some platforms; capturing it would throw inside the loop.
        assertNull(captureAreaFor(capture(mode = "window", title = "Min"), { Rectangle(0, 0, 0, 0) }))
        assertNull(captureAreaFor(capture(mode = "window", title = "Min"), { Rectangle(0, 0, 800, 0) }))
    }

    @Test
    fun `a window picked by handle still falls back to its title's bounds`() {
        // The platform grabbers get first refusal; when neither can, this is what Robot is given.
        val where = Rectangle(5, 6, 320, 240)

        val area = captureAreaFor(capture(mode = "window", title = "Notes", id = "0x1f4"), { where })

        assertEquals(where, area)
    }

    @Test
    fun `a source set to follow a window but naming none falls back to the fixed area`() {
        // How the app writes the source before a window has been picked.
        val area = captureAreaFor(capture(mode = "window", w = 1280, h = 720), noWindows)

        assertEquals(Rectangle(0, 0, 1280, 720), area)
    }

    // ── The window handle ───────────────────────────────────────────────────────

    @Test
    fun `a hex window handle is read as a number`() {
        assertEquals(0x1f4L, windowIdOf(capture(id = "0x1f4")))
    }

    @Test
    fun `a handle written without its prefix still reads`() {
        assertEquals(0x1f4L, windowIdOf(capture(id = "1f4")))
    }

    @Test
    fun `a handle that is not a number at all reads as no window`() {
        assertEquals(0L, windowIdOf(capture(id = "the second one")))
        assertEquals(0L, windowIdOf(capture(id = "")))
    }
}
