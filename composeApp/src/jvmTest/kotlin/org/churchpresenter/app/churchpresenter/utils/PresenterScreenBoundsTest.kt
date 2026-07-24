package org.churchpresenter.app.churchpresenter.utils

import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.awt.GraphicsConfiguration
import java.awt.GraphicsDevice
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [presenterBoundsOf] picks which display the audience output lands on: the first non-primary
 * screen (the projector), falling back to the primary when that is all there is. The
 * GraphicsEnvironment lookup around it is headless-bound, but the choice itself is pure. Real
 * GraphicsDevices can't be constructed and the test JVM is headless, so the topology is faked with
 * MockK — the same approach as FindScreenIndexTest.
 */
class PresenterScreenBoundsTest {

    private fun screen(x: Int, y: Int, w: Int, h: Int): GraphicsDevice {
        val config = mockk<GraphicsConfiguration>()
        every { config.bounds } returns Rectangle(x, y, w, h)
        val device = mockk<GraphicsDevice>()
        every { device.defaultConfiguration } returns config
        return device
    }

    @Test
    fun `the first non-primary display is chosen for the audience output`() {
        val primary = screen(0, 0, 1920, 1080)
        val projector = screen(1920, 0, 1280, 720)
        assertEquals(
            Rectangle(1920, 0, 1280, 720),
            presenterBoundsOf(arrayOf(primary, projector), primary),
        )
    }

    @Test
    fun `a non-primary is preferred even when it is not first in the list`() {
        val primary = screen(0, 0, 1920, 1080)
        val projector = screen(-1920, 0, 1920, 1080)
        assertEquals(
            Rectangle(-1920, 0, 1920, 1080),
            presenterBoundsOf(arrayOf(primary, projector), primary),
        )
    }

    @Test
    fun `a single-display machine falls back to the primary`() {
        val primary = screen(0, 0, 2560, 1440)
        assertEquals(Rectangle(0, 0, 2560, 1440), presenterBoundsOf(arrayOf(primary), primary))
    }

    private fun withGraphicsEnvironment(screens: Array<GraphicsDevice>, primary: GraphicsDevice, block: () -> Unit) {
        mockkStatic(GraphicsEnvironment::class)
        try {
            val ge = mockk<GraphicsEnvironment>()
            every { ge.screenDevices } returns screens
            every { ge.defaultScreenDevice } returns primary
            every { GraphicsEnvironment.getLocalGraphicsEnvironment() } returns ge
            block()
        } finally {
            unmockkStatic(GraphicsEnvironment::class)
        }
    }

    @Test
    fun `presenterScreenBounds reads the environment and returns the projector bounds`() {
        val primary = screen(0, 0, 1920, 1080)
        val projector = screen(1920, 0, 1280, 720)
        withGraphicsEnvironment(arrayOf(primary, projector), primary) {
            assertEquals(Rectangle(1920, 0, 1280, 720), presenterScreenBounds())
        }
    }

    @Test
    fun `presenterAspectRatio divides the resolved presenter bounds`() {
        val primary = screen(0, 0, 1920, 1080)
        val projector = screen(1920, 0, 1280, 720)
        withGraphicsEnvironment(arrayOf(primary, projector), primary) {
            assertEquals(1280f / 720f, presenterAspectRatio(), 0.0001f)
        }
    }

    @Test
    fun `aspect ratio is width divided by height`() {
        assertEquals(16f / 9f, aspectRatioOf(Rectangle(0, 0, 1920, 1080)), 0.0001f)
        assertEquals(4f / 3f, aspectRatioOf(Rectangle(0, 0, 1024, 768)), 0.0001f)
        assertEquals(1f, aspectRatioOf(Rectangle(0, 0, 1000, 1000)), 0.0001f, "a square screen is 1.0")
        assertTrue(aspectRatioOf(Rectangle(0, 0, 1080, 1920)) < 1f, "portrait is narrower than tall")
    }

    @Test
    fun `the audio and video extension sets are populated and disjoint`() {
        assertTrue("mp3" in Constants.AUDIO_EXTENSIONS)
        assertTrue("opus" in Constants.AUDIO_EXTENSIONS)
        assertTrue("mp4" in Constants.VIDEO_EXTENSIONS)
        assertTrue("mkv" in Constants.VIDEO_EXTENSIONS)
        assertTrue(
            (Constants.AUDIO_EXTENSIONS intersect Constants.VIDEO_EXTENSIONS).isEmpty(),
            "an extension classified as both audio and video would route media to the wrong player",
        )
    }
}
