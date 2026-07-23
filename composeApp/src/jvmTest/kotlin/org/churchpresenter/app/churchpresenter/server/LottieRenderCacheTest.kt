package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The size and variant policy that decides which cached render every consumer shares.
 *
 * A lottie can be streamed to desktop playback and uploaded to an ATEM, and the whole point of the
 * cache is that those are ONE render when they can be — same content, same size, one entry. Getting
 * this arithmetic wrong is expensive in two directions: too-large a canvas wastes disk and decode
 * time for no visual gain, and an ATEM upload at anything but the switcher raster produces a
 * stride/chroma-shifted (purplish, half) image. So this covers the pure decisions — parsing the
 * lottie's own w/h/timing, clamping to 1920, and the aspect-match branch that chooses between
 * "share one upsized entry" and "letterbox into the raster".
 *
 * All functions here are pure (no disk, no render). The object's one-time init deletes an old cache
 * dir under user.home, so user.home is redirected to a temp dir before the class is first touched.
 */
class LottieRenderCacheTest {

    private lateinit var originalUserHome: String

    @BeforeTest
    fun isolateUserHome() {
        originalUserHome = System.getProperty("user.home")
        System.setProperty("user.home", Files.createTempDirectory("lrc-home").toString())
    }

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
    }

    /** A minimal lottie: 1920×1080 canvas, 30fps, frames 0..90 → a 3-second clip. */
    private val clipJson = """{"w":1920,"h":1080,"fr":30,"ip":0,"op":90}"""

    // ── Reading the lottie JSON ─────────────────────────────────────────────────

    @Test
    fun `canvas size comes straight from the lottie`() {
        assertEquals(1920 to 1080, LottieRenderCache.lottieCanvasSize(clipJson))
    }

    @Test
    fun `a canvas with a non-positive dimension is rejected`() {
        assertNull(LottieRenderCache.lottieCanvasSize("""{"w":0,"h":1080}"""), "a zero-width canvas can't be rendered")
        assertNull(LottieRenderCache.lottieCanvasSize("not json"), "garbage must not throw, just yield null")
    }

    @Test
    fun `duration is derived from the frame range and rate`() {
        // (op - ip) / fr seconds = (90 - 0) / 30 = 3s
        assertEquals(3000L, LottieRenderCache.lottieDurationMs(clipJson))
    }

    @Test
    fun `a lottie whose end is not after its start has no duration`() {
        assertNull(LottieRenderCache.lottieDurationMs("""{"fr":30,"ip":40,"op":40}"""), "op<=ip is not a playable clip")
        assertNull(LottieRenderCache.lottieDurationMs("""{"fr":0,"ip":0,"op":90}"""), "a zero frame-rate has no timeline")
    }

    @Test
    fun `frame count scales the duration by the requested fps`() {
        // 3s at the switcher's 25fps and at desktop's 30fps produce different counts.
        assertEquals(75, LottieRenderCache.clipFrameCount(clipJson, 25.0))
        assertEquals(90, LottieRenderCache.clipFrameCount(clipJson, 30.0))
        assertNull(LottieRenderCache.clipFrameCount("""{"w":1,"h":1}""", 30.0), "no timing means no clip")
    }

    // ── Clamping ────────────────────────────────────────────────────────────────

    @Test
    fun `a canvas within the limit is left alone`() {
        assertEquals(1280 to 720, LottieRenderCache.clampCanvasSize(1280, 720))
    }

    @Test
    fun `an oversized canvas scales down proportionally so neither side exceeds 1920`() {
        // 4K letterbox → half size, aspect preserved (no visual loss under ContentScale.Fit).
        assertEquals(1920 to 1080, LottieRenderCache.clampCanvasSize(3840, 2160))
    }

    // ── ATEM / desktop variant policy ───────────────────────────────────────────

    private val atem1080 = AtemSettings(host = "10.0.0.1", renderWidth = 1920, renderHeight = 1080)

    @Test
    fun `matching aspect shares one entry sized to the larger of canvas and raster`() {
        // A 1080p (16:9) lottie against a 720p raster keeps the larger per-axis size, so the one
        // cached render serves both the ATEM upload and desktop playback. (The canvas is clamped to
        // 1920 first, so the max only exceeds the raster when the canvas legitimately does.)
        val atem720 = AtemSettings(host = "10.0.0.1", renderWidth = 1280, renderHeight = 720)
        assertEquals(1920 to 1080, LottieRenderCache.atemRenderSize(clipJson, atem720))
    }

    @Test
    fun `a differing aspect renders exactly at the raster to avoid distortion`() {
        // A square lottie can't be non-uniformly stretched to 16:9, so it letterboxes into the raster.
        val square = """{"w":1000,"h":1000,"fr":30,"ip":0,"op":90}"""
        assertEquals(1920 to 1080, LottieRenderCache.atemRenderSize(square, atem1080))
    }

    @Test
    fun `desktop size is the plain canvas when no ATEM is configured`() {
        assertEquals(1920 to 1080, LottieRenderCache.desktopRenderSize(clipJson, null))
        assertEquals(1920 to 1080, LottieRenderCache.desktopRenderSize(clipJson, AtemSettings(host = "")))
    }

    @Test
    fun `desktop size upgrades to the shared ATEM size on a matching aspect`() {
        val small = """{"w":1280,"h":720,"fr":30,"ip":0,"op":90}"""
        // 720p canvas + 1080p same-aspect raster → the shared 1080p entry.
        assertEquals(1920 to 1080, LottieRenderCache.desktopRenderSize(small, atem1080))
    }

    @Test
    fun `a still variant ignores clip timing`() {
        val still = LottieRenderCache.atemVariant(clipJson, atem1080, clip = false)
        assertEquals(false, still.clip)
        assertEquals(1, still.frameCount, "a still is a single frame regardless of the lottie's timeline")
    }

    @Test
    fun `the desktop variant is null when the lottie carries no timing`() {
        assertNull(LottieRenderCache.desktopVariant("""{"w":1920,"h":1080}""", atem1080), "no timeline, nothing to stream")
    }
}
