package org.churchpresenter.canvas

import io.mockk.every
import io.mockk.mockk
import org.churchpresenter.settings.ScreenAssignment
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which display an output assignment resolves to, and what happens when there is no display at all.
 *
 * The precedence matters in a real building: an operator sets the projector up once, and the stored
 * bounds have to keep winning over the stored index, because plugging in or unplugging any other
 * screen renumbers the indices while the projector stays where it is on the desktop. Getting that
 * backwards would silently move the audience output to the wrong screen after a cable change.
 *
 * The headless case is not hypothetical either — it is what lets `CanvasTab` be composed in a test
 * at all. The tab compares its scene's aspect ratio against the assigned display's, and used to ask
 * `GraphicsEnvironment` for that directly, which throws on a machine with no screen.
 *
 * Real `GraphicsDevice`s cannot be constructed and the test JVM is headless, so the topology is
 * faked with MockK, as in [PresenterScreenBoundsTest] and `FindScreenIndexTest`.
 */
class AssignedDisplayBoundsTest {

    private fun screen(x: Int, y: Int, w: Int, h: Int): GraphicsDevice {
        val config = mockk<GraphicsConfiguration>()
        every { config.bounds } returns Rectangle(x, y, w, h)
        val device = mockk<GraphicsDevice>()
        every { device.defaultConfiguration } returns config
        return device
    }

    private val primary = screen(0, 0, 1920, 1080)
    private val projector = screen(1920, 0, 1280, 720)
    private val stage = screen(-1080, 0, 1080, 1920)
    private val all = arrayOf(primary, projector, stage)

    @Test
    fun `stored bounds pick the screen sitting at that position`() {
        val assignment = ScreenAssignment(targetDisplay = 0, targetBoundsX = 1920, targetBoundsY = 0)
        assertEquals(Rectangle(1920, 0, 1280, 720), assignedBoundsOf(all, primary, assignment))
    }

    @Test
    fun `stored bounds win over a stale index`() {
        // The operator chose the projector when it was index 1; another screen has since renumbered
        // it. The position is what still identifies it.
        val assignment = ScreenAssignment(targetDisplay = 0, targetBoundsX = -1080, targetBoundsY = 0)
        assertEquals(
            Rectangle(-1080, 0, 1080, 1920),
            assignedBoundsOf(all, primary, assignment),
            "the screen at the stored position must win over the one at the stored index",
        )
    }

    @Test
    fun `the index is used when no bounds were stored`() {
        val assignment = ScreenAssignment(targetDisplay = 2)
        assertEquals(Rectangle(-1080, 0, 1080, 1920), assignedBoundsOf(all, primary, assignment))
    }

    @Test
    fun `bounds that match nothing fall through to the index`() {
        val assignment = ScreenAssignment(targetDisplay = 1, targetBoundsX = 9999, targetBoundsY = 9999)
        assertEquals(
            Rectangle(1920, 0, 1280, 720),
            assignedBoundsOf(all, primary, assignment),
            "an unplugged screen's stored position must not strand the assignment",
        )
    }

    @Test
    fun `an out-of-range index falls back to the first non-primary screen`() {
        val assignment = ScreenAssignment(targetDisplay = 7)
        assertEquals(Rectangle(1920, 0, 1280, 720), assignedBoundsOf(all, primary, assignment))
    }

    @Test
    fun `the auto assignment takes the first non-primary screen`() {
        // targetDisplay defaults to -1 (auto), which is not a valid index.
        assertEquals(Rectangle(1920, 0, 1280, 720), assignedBoundsOf(all, primary, ScreenAssignment()))
    }

    @Test
    fun `a single-display machine falls back to the primary`() {
        assertEquals(
            Rectangle(0, 0, 1920, 1080),
            assignedBoundsOf(arrayOf(primary), primary, ScreenAssignment()),
        )
    }

    @Test
    fun `with no displays at all the bounds are 1080p rather than an exception`() {
        // No mocking: the test JVM is genuinely headless, so this exercises the real fallback the
        // CanvasTab aspect-ratio check depends on.
        assertEquals(Rectangle(0, 0, 1920, 1080), assignedDisplayBounds(ScreenAssignment()))
    }
}
