package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.data.settings.AtemSettings
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
 * Most of it is pure (no disk, no render), but the last two touch the cache directory and the
 * eviction one deletes from it, so `user.home` is redirected to a temp dir for every test. That
 * redirect only works because `LottieRenderCache.cacheDir` resolves per call: the object is
 * reachable from `CompanionServer`, `LowerThird` and `PresenterManager`, so in a full run something
 * has already touched it, and a latched `cacheDir` would hand these tests the developer's own
 * cache to delete.
 */
class LottieRenderCacheTest {

    private lateinit var originalUserHome: String
    private lateinit var tempHome: java.nio.file.Path

    @BeforeTest
    fun isolateUserHome() {
        originalUserHome = System.getProperty("user.home")
        tempHome = Files.createTempDirectory("lrc-home")
        System.setProperty("user.home", tempHome.toString())
    }

    @AfterTest
    fun restoreUserHome() {
        System.setProperty("user.home", originalUserHome)
        tempHome.toFile().deleteRecursively()
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
        assertNull(
            LottieRenderCache.lottieDurationMs("""{"fr":0,"ip":0,"op":90}"""),
            "a zero frame-rate has no timeline",
        )
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
        assertNull(
            LottieRenderCache.desktopVariant("""{"w":1920,"h":1080}""", atem1080),
            "no timeline, nothing to stream",
        )
    }

    // ── ARGB RLE codec ────────────────────────────────────────────────────────

    private fun roundTrip(pixels: IntArray): IntArray =
        LottieRenderCache.decodeArgbRle(LottieRenderCache.encodeArgbRle(pixels), pixels.size)

    @Test
    fun `a solid-color frame round-trips through a single run record`() {
        val pixels = IntArray(100) { 0xFF112233.toInt() }
        assertTrue(roundTrip(pixels).contentEquals(pixels))
    }

    @Test
    fun `an all-distinct frame round-trips through literal records`() {
        val pixels = IntArray(50) { it }
        assertTrue(roundTrip(pixels).contentEquals(pixels))
    }

    @Test
    fun `a frame mixing runs and literals round-trips intact`() {
        val pixels = intArrayOf(1, 1, 1, 1, 2, 3, 4, 5, 5, 5, 6)
        assertTrue(roundTrip(pixels).contentEquals(pixels))
    }

    @Test
    fun `decoding fewer pixels than the payload promises throws rather than returning a short frame`() {
        val payload = LottieRenderCache.encodeArgbRle(IntArray(10) { 7 })
        assertFailsWith<java.io.IOException> { LottieRenderCache.decodeArgbRle(payload, 20) }
    }

    // ── Pixel scaling ────────────────────────────────────────────────────────

    @Test
    fun `scaling a solid-color image preserves the color regardless of size change`() {
        val src = IntArray(4) { 0xFFFF0000.toInt() }

        val scaledUp = LottieRenderCache.scaleArgb(src, 2, 2, 8, 8)
        assertEquals(64, scaledUp.size)
        assertTrue(scaledUp.all { it == 0xFFFF0000.toInt() })

        val scaledDown = LottieRenderCache.scaleArgb(src, 2, 2, 1, 1)
        assertEquals(1, scaledDown.size)
        assertEquals(0xFFFF0000.toInt(), scaledDown[0])
    }

    // ── Cache file presence ──────────────────────────────────────────────────

    @Test
    fun `isReady is false until a file exists at the content-addressed cache path`() {
        val json = """{"w":10,"h":10,"fr":30,"ip":0,"op":30,"unique":"isready-test"}"""
        val variant = LottieRenderCache.Variant(clip = true, width = 10, height = 10, fps = 30.0, frameCount = 30)
        assertFalse(LottieRenderCache.isReady(json, variant))

        val file = LottieRenderCache.cacheFile(LottieRenderCache.keyFor(json, variant))
        file.parentFile.mkdirs()
        file.writeBytes(ByteArray(1))
        try {
            assertTrue(LottieRenderCache.isReady(json, variant))
        } finally {
            file.delete()
        }
    }

    // ── Eviction ─────────────────────────────────────────────────────────────

    @Test
    fun `eviction removes the oldest entries first once past the entry cap`() {
        val dir = LottieRenderCache.cacheDir
        // This test empties the directory it is given, and eviction deletes from it too. If the
        // user.home redirect above ever stops taking effect, that directory is the developer's own
        // render cache. Refuse to touch anything outside the temp home rather than find out later.
        check(dir.toPath().startsWith(tempHome)) { "refusing to evict outside the test home: $dir" }
        dir.mkdirs()
        dir.listFiles { f -> f.extension == "lrcc" }?.forEach { it.delete() }

        val total = LottieRenderCache.MAX_ENTRIES + 5
        val base = System.currentTimeMillis()
        val files = (0 until total).map { i ->
            File(dir, "evict-test-$i.lrcc").apply {
                writeBytes(ByteArray(1))
                setLastModified(base + i * 1000L)
            }
        }

        LottieRenderCache.evictOldEntries()

        assertEquals(LottieRenderCache.MAX_ENTRIES, dir.listFiles { f -> f.extension == "lrcc" }?.size)
        (0 until 5).forEach { assertFalse(files[it].exists(), "file $it should have been evicted") }
        (5 until total).forEach { assertTrue(files[it].exists(), "file $it should have survived") }
    }

    // ── Malformed lottie JSON ────────────────────────────────────────────────

    @Test
    fun `a canvas missing a dimension has no size`() {
        // A lottie exported without one of these is not renderable at any size, and the caller has
        // to be told so rather than handed a half-read pair.
        assertNull(LottieRenderCache.lottieCanvasSize("""{"w":1920}"""), "no height")
        assertNull(LottieRenderCache.lottieCanvasSize("""{"h":1080}"""), "no width")
    }

    @Test
    fun `a canvas with a negative dimension is rejected like a zero one`() {
        assertNull(LottieRenderCache.lottieCanvasSize("""{"w":1920,"h":-1080}"""))
    }

    @Test
    fun `a lottie with no out-point has no duration`() {
        // `op` is the only field with no sensible default: without it there is no end to the clip.
        assertNull(LottieRenderCache.lottieDurationMs("""{"w":10,"h":10,"fr":30,"ip":0}"""))
        assertNull(LottieRenderCache.clipFrameCount("""{"w":10,"h":10,"fr":30,"ip":0}""", fps = 25.0))
    }

    @Test
    fun `a missing in-point is read as frame zero`() {
        // Unlike `op`, a missing `ip` has an obvious meaning — the clip starts at the beginning —
        // and most exporters omit it. Defaulting keeps those files playable.
        assertEquals(3_000L, LottieRenderCache.lottieDurationMs("""{"w":10,"h":10,"fr":30,"op":90}"""))
    }

    // ── Reading a damaged cache file ─────────────────────────────────────────

    @Test
    fun `a file that is not a render cache is refused by its magic`() {
        // The cache directory is under the user's home and named by content hash. Anything else
        // that lands there — a truncated download, an unrelated file renamed — must fail on open
        // rather than be read as frame offsets and seek wildly through it.
        val file = File(
            tempHome.toFile(),
            "not-a-cache.lrcc",
        ).apply { writeBytes("XXXX".toByteArray() + ByteArray(32)) }

        assertFailsWith<IOException> { LottieRenderCache.Reader(file) }
    }

    @Test
    fun `a cache file from another version is refused`() {
        // The format carries a version byte precisely so an old file is rejected instead of being
        // decoded under the current layout, which would produce garbage frames on screen.
        val file = File(tempHome.toFile(), "old-version.lrcc").apply {
            writeBytes("LRCC".toByteArray() + byteArrayOf(99) + ByteArray(32))
        }

        assertFailsWith<IOException> { LottieRenderCache.Reader(file) }
    }

    // ── Eviction edge cases ──────────────────────────────────────────────────

    @Test
    fun `eviction on a cache directory that was never created does nothing`() {
        // Eviction runs after every render, including the very first one on a fresh install where
        // the directory may not exist yet. Listing it returns null there, not an empty array.
        val dir = LottieRenderCache.cacheDir
        check(dir.toPath().startsWith(tempHome)) { "refusing to touch a cache outside the test home: $dir" }
        dir.deleteRecursively()

        LottieRenderCache.evictOldEntries()

        assertFalse(dir.exists(), "eviction must not create the directory it was asked to tidy")
    }

    // ── Decoding a frame that promises more than it holds ────────────────────

    @Test
    fun `a literal run longer than the frame stops at the last pixel`() {
        // The pixel count comes from the file header and the run lengths from the payload. A run
        // that overshoots must fill to the end and stop, not write past the frame.
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES * 5).apply {
            putInt(-4)                       // four literal pixels...
            putInt(0xFF0000FF.toInt())
            putInt(0xFF00FF00.toInt())
            putInt(0xFFFF0000.toInt())
            putInt(0xFFFFFFFF.toInt())
        }.array()

        val decoded = LottieRenderCache.decodeArgbRle(payload, pixelCount = 2)  // ...into a two-pixel frame

        assertEquals(2, decoded.size)
        assertEquals(0xFF0000FF.toInt(), decoded[0])
        assertEquals(0xFF00FF00.toInt(), decoded[1])
    }

    @Test
    fun `a solid run longer than the frame fills only the frame`() {
        val payload = ByteBuffer.allocate(Int.SIZE_BYTES * 2).apply {
            putInt(10)                       // a ten-pixel run...
            putInt(0xFF123456.toInt())
        }.array()

        val decoded = LottieRenderCache.decodeArgbRle(payload, pixelCount = 3)  // ...into a three-pixel frame

        assertEquals(3, decoded.size)
        decoded.forEach { assertEquals(0xFF123456.toInt(), it) }
    }
}
