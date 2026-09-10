package org.churchpresenter.app.churchpresenter.composables

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How an override learned from a device's own complaint is merged into the input flags a chosen
 * format asked for.
 *
 * The rule that matters is the last one: `-framerate` is replaced rather than appended. ffmpeg
 * takes the last occurrence of an input option either way, so the picture would be right — but two
 * of them in one argv is a command nobody can read in a bug report.
 */
class CaptureOverrideArgsTest {

    private val requested = listOf("-video_size", "1920x1080", "-framerate", "30")

    @Test
    fun `nothing overridden yet leaves the requested flags exactly as they were`() {
        assertEquals(requested, applyCaptureOverride(requested, CaptureOverride.NONE))
    }

    @Test
    fun `asking the device for nothing discards even what the source requested`() {
        assertEquals(emptyList(), applyCaptureOverride(requested, CaptureOverride.DEVICE_DEFAULTS))
    }

    @Test
    fun `device defaults win over a pixel format named alongside them`() {
        val override = CaptureOverride(pixelFormat = "uyvy422", framerate = "25", useDeviceDefaults = true)
        assertEquals(emptyList(), applyCaptureOverride(requested, override))
    }

    @Test
    fun `a pixel format is added, having no counterpart to replace`() {
        assertEquals(
            requested + listOf("-pixel_format", "uyvy422"),
            applyCaptureOverride(requested, CaptureOverride(pixelFormat = "uyvy422")),
        )
    }

    @Test
    fun `an overridden frame rate replaces the requested one rather than joining it`() {
        val merged = applyCaptureOverride(requested, CaptureOverride(framerate = "29.97"))
        assertEquals(listOf("-video_size", "1920x1080", "-framerate", "29.97"), merged)
        assertEquals(1, merged.count { it == "-framerate" })
    }

    @Test
    fun `the size the source asked for survives a frame rate override`() {
        val merged = applyCaptureOverride(requested, CaptureOverride(framerate = "60"))
        assertTrue(merged.containsAll(listOf("-video_size", "1920x1080")))
    }

    @Test
    fun `both overrides at once produce one of each flag`() {
        val merged = applyCaptureOverride(requested, CaptureOverride(pixelFormat = "nv12", framerate = "24"))
        assertEquals(
            listOf("-video_size", "1920x1080", "-framerate", "24", "-pixel_format", "nv12"),
            merged,
        )
    }

    @Test
    fun `a frame rate override with nothing requested is simply the override`() {
        assertEquals(listOf("-framerate", "15"), applyCaptureOverride(emptyList(), CaptureOverride(framerate = "15")))
    }

    @Test
    fun `a request carrying no frame rate keeps every flag it had`() {
        val sizeOnly = listOf("-video_size", "1280x720")
        assertEquals(
            sizeOnly + listOf("-framerate", "30"),
            applyCaptureOverride(sizeOnly, CaptureOverride(framerate = "30")),
        )
    }

    @Test
    fun `a request with no flags at all and no override stays empty`() {
        assertEquals(emptyList(), applyCaptureOverride(emptyList(), CaptureOverride.NONE))
    }

    @Test
    fun `the order of the requested flags is preserved`() {
        val ordered = listOf("-video_size", "1280x720", "-pix_fmt", "yuv420p", "-framerate", "30")
        val merged = applyCaptureOverride(ordered, CaptureOverride(framerate = "50"))
        assertEquals(listOf("-video_size", "1280x720", "-pix_fmt", "yuv420p", "-framerate", "50"), merged)
    }

    @Test
    fun `a repeated frame rate in the request is collapsed to the override's single one`() {
        val messy = listOf("-framerate", "30", "-video_size", "640x480", "-framerate", "15")
        val merged = applyCaptureOverride(messy, CaptureOverride(framerate = "25"))
        assertEquals(listOf("-video_size", "640x480", "-framerate", "25"), merged)
    }
}
