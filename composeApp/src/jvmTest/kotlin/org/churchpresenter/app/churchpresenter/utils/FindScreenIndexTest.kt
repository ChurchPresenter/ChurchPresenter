package org.churchpresenter.app.churchpresenter.utils

import io.mockk.every
import io.mockk.mockk
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [findScreenIndexByBounds] re-locates a saved projector output after a restart or a monitor
 * re-plug. Getting it wrong sends the audience output to the wrong display — or to the operator's
 * own screen mid-service.
 *
 * Real `GraphicsDevice`s can't be constructed, and the test JVM is headless, so the display
 * topology is faked with MockK. Both `getDefaultConfiguration()` and `getBounds()` are abstract,
 * which is what makes this mockable at all.
 */
class FindScreenIndexTest {

    private fun screen(x: Int, y: Int, w: Int, h: Int): GraphicsDevice {
        val config = mockk<GraphicsConfiguration>()
        every { config.bounds } returns Rectangle(x, y, w, h)
        val device = mockk<GraphicsDevice>()
        every { device.defaultConfiguration } returns config
        return device
    }

    /** Laptop screen, projector to its right, a third display above — a realistic booth layout. */
    private fun typicalLayout(): Array<GraphicsDevice> = arrayOf(
        screen(0, 0, 1920, 1080),
        screen(1920, 0, 1280, 720),
        screen(0, -1080, 1920, 1080),
    )

    @Test
    fun `finds the screen whose bounds match exactly`() {
        val screens = typicalLayout()
        assertEquals(0, findScreenIndexByBounds(screens, 0, 0, 1920, 1080))
        assertEquals(1, findScreenIndexByBounds(screens, 1920, 0, 1280, 720))
        assertEquals(2, findScreenIndexByBounds(screens, 0, -1080, 1920, 1080), "negative origins are valid")
    }

    @Test
    fun `the MIN_VALUE sentinel means no bounds were ever saved`() {
        // ScreenAssignment defaults targetBoundsX to Int.MIN_VALUE for "not set".
        assertNull(findScreenIndexByBounds(typicalLayout(), Int.MIN_VALUE, 0, 1920, 1080))
    }

    @Test
    fun `returns null when the saved display is gone`() {
        // The projector was unplugged, or its resolution changed since the setting was saved.
        assertNull(findScreenIndexByBounds(typicalLayout(), 3840, 0, 1920, 1080), "unplugged display")
        assertNull(findScreenIndexByBounds(typicalLayout(), 1920, 0, 1920, 1080), "same origin, new resolution")
    }

    @Test
    fun `every one of the four components must match`() {
        val screens = arrayOf(screen(100, 200, 1920, 1080))
        assertEquals(0, findScreenIndexByBounds(screens, 100, 200, 1920, 1080))
        assertNull(findScreenIndexByBounds(screens, 101, 200, 1920, 1080), "x differs")
        assertNull(findScreenIndexByBounds(screens, 100, 201, 1920, 1080), "y differs")
        assertNull(findScreenIndexByBounds(screens, 100, 200, 1921, 1080), "width differs")
        assertNull(findScreenIndexByBounds(screens, 100, 200, 1920, 1081), "height differs")
    }

    @Test
    fun `identical clones resolve to the first match`() {
        // Two projectors mirrored at the same coordinates — the result must be stable, not random.
        val screens = arrayOf(
            screen(0, 0, 1920, 1080),
            screen(0, 0, 1920, 1080),
        )
        assertEquals(0, findScreenIndexByBounds(screens, 0, 0, 1920, 1080))
    }

    @Test
    fun `an empty display list yields null rather than throwing`() {
        assertNull(findScreenIndexByBounds(emptyArray(), 0, 0, 1920, 1080))
    }
}
