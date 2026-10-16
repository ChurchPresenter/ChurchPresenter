@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.canvas.FakeDeckLinkCard
import org.churchpresenter.canvas.bridgeOver
import org.churchpresenter.canvas.outputsOver
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.media.viewmodel.MediaViewModel
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckLinkComposeOutputTest {

    @Test
    fun `convertToKeySignal keys white where the fill is fully white`() {
        val pixels = intArrayOf(0xFFFFFFFF.toInt())
        convertToKeySignal(pixels)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }

    @Test
    fun `convertToKeySignal keys black where the fill is fully black`() {
        val pixels = intArrayOf(0xFF000000.toInt())
        convertToKeySignal(pixels)
        assertEquals(0xFF000000.toInt(), pixels[0])
    }

    @Test
    fun `convertToKeySignal takes the max channel as luminance for a saturated color`() {
        // Pure red fill: max(255, 0, 0) = 255 -> full white key, content should show through.
        val pixels = intArrayOf(0xFFFF0000.toInt())
        convertToKeySignal(pixels)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
    }

    @Test
    fun `convertToKeySignal keys a mid-gray to the same gray level`() {
        val gray = 0x80
        val pixels = intArrayOf((0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray)
        convertToKeySignal(pixels)
        val expected = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        assertEquals(expected, pixels[0])
    }

    @Test
    fun `convertToKeySignal always forces the alpha channel to opaque`() {
        val pixels = intArrayOf((0x00 shl 24) or (0x10 shl 16) or (0x20 shl 8) or 0x30)
        convertToKeySignal(pixels)
        assertEquals(0xFF, (pixels[0] shr 24) and 0xFF)
    }

    @Test
    fun `convertToKeySignal processes every pixel in the array`() {
        val pixels = intArrayOf(0xFFFF0000.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        convertToKeySignal(pixels)
        assertEquals(0xFFFFFFFF.toInt(), pixels[0])
        assertEquals(0xFF000000.toInt(), pixels[1])
        assertEquals(0xFFFFFFFF.toInt(), pixels[2])
    }

    @Test
    fun `DeckLinkComposeOutput with no DeckLink hardware mounts and disposes without crashing`() = runComposeUiTest {
        // DeckLinkManager.isAvailable() is false on this machine (no native library present), so
        // this always takes the early-return path in the DisposableEffect — proving that path,
        // and the composition-time icon rendering above it, are safe on their own.
        setContent {
            DeckLinkComposeOutput(
                deviceIndex = 0,
                outputRole = Constants.OUTPUT_ROLE_NORMAL,
                appSettings = AppSettings(),
                mediaViewModel = MediaViewModel(),
            ) {}
        }
        waitForIdle()
    }

    @Test
    fun `DeckLinkComposeOutput in key mode with no DeckLink hardware does not crash`() = runComposeUiTest {
        setContent {
            DeckLinkComposeOutput(
                deviceIndex = 0,
                outputRole = Constants.OUTPUT_ROLE_KEY,
                appSettings = AppSettings(),
                mediaViewModel = MediaViewModel(),
                isLowerThird = true,
            ) {}
        }
        waitForIdle()
    }

    @Test
    fun `disposing the output returns while the card is still opening, and the close follows it`() = runComposeUiTest {
        val card = FakeDeckLinkCard()
        val reached = CountDownLatch(1)
        val release = CountDownLatch(1)
        card.onOpen = {
            reached.countDown()
            release.await(WAIT_S, TimeUnit.SECONDS)
        }
        val worker = Executors.newSingleThreadExecutor()
        try {
            var mounted by mutableStateOf(true)
            setContent {
                if (mounted) {
                    DeckLinkComposeOutput(
                        deviceIndex = 0,
                        outputRole = Constants.OUTPUT_ROLE_NORMAL,
                        appSettings = AppSettings(),
                        mediaViewModel = MediaViewModel(),
                        outputs = outputsOver(bridgeOver(card), worker),
                    ) {}
                }
            }
            assertTrue(reached.await(WAIT_S, TimeUnit.SECONDS))

            mounted = false
            waitForIdle()

            // The dispose is done, and the card is still held inside its open.
            assertEquals(emptyList(), card.eventsNow())
            release.countDown()
            val drained = CountDownLatch(1)
            worker.execute { drained.countDown() }
            assertTrue(drained.await(WAIT_S, TimeUnit.SECONDS))
            assertEquals(listOf("open 0", "black 0", "black 0", "black 0", "close 0"), card.eventsNow())
        } finally {
            release.countDown()
            worker.shutdownNow()
        }
    }

    // ── skiaBgraToArgbPixels ──────────────────────────────────────────────────────────────────────

    @Test
    fun `skiaBgraToArgbPixels converts a single BGRA pixel to ARGB`() {
        // B=0x11 G=0x22 R=0x33 A=0xFF → ARGB packed as 0xFF332211
        val bytes = byteArrayOf(0x11, 0x22, 0x33, 0xFF.toByte())
        val pixels = IntArray(1)
        skiaBgraToArgbPixels(bytes, pixels, 1)
        assertEquals(0xFF332211.toInt(), pixels[0])
    }

    @Test
    fun `skiaBgraToArgbPixels converts multiple pixels in order`() {
        val bytes = byteArrayOf(
            0x00, 0x00, 0x00, 0xFF.toByte(),           // opaque black
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()  // opaque white
        )
        val pixels = IntArray(2)
        skiaBgraToArgbPixels(bytes, pixels, 2)
        assertEquals(0xFF000000.toInt(), pixels[0])
        assertEquals(0xFFFFFFFF.toInt(), pixels[1])
    }

    @Test
    fun `skiaBgraToArgbPixels preserves alpha channel`() {
        // Half-transparent red: B=0 G=0 R=FF A=80
        val bytes = byteArrayOf(0x00, 0x00, 0xFF.toByte(), 0x80.toByte())
        val pixels = IntArray(1)
        skiaBgraToArgbPixels(bytes, pixels, 1)
        assertEquals(0x80, (pixels[0] shr 24) and 0xFF)
        assertEquals(0xFF, (pixels[0] shr 16) and 0xFF)
        assertEquals(0x00, (pixels[0] shr 8) and 0xFF)
        assertEquals(0x00, pixels[0] and 0xFF)
    }

    @Test
    fun `skiaBgraToArgbPixels handles zero pixelCount without error`() {
        val bytes = ByteArray(0)
        val pixels = IntArray(0)
        skiaBgraToArgbPixels(bytes, pixels, 0)
    }

    @Test
    fun `skiaBgraToArgbPixels converts only pixelCount pixels even if arrays are larger`() {
        val bytes = byteArrayOf(
            0x11, 0x22, 0x33, 0xFF.toByte(),
            0x44, 0x55, 0x66, 0xFF.toByte()
        )
        val pixels = IntArray(2)
        skiaBgraToArgbPixels(bytes, pixels, 1)
        assertEquals(0xFF332211.toInt(), pixels[0])
        assertEquals(0, pixels[1])
    }

    private companion object {
        const val WAIT_S = 5L
    }
}
