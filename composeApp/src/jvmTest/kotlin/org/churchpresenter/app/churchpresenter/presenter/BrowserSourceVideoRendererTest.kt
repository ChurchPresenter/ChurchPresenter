package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BrowserSourceVideoRendererTest {

    private fun renderer(fps: Int = 30) = BrowserSourceVideoRenderer(
        presenterManager = PresenterManager(),
        appSettingsState = mutableStateOf(AppSettings()),
        screenAssignmentState = mutableStateOf(ScreenAssignment()),
        effectiveModeState = mutableStateOf(Presenting.NONE),
        fps = fps,
    )

    private fun solid(width: Int, height: Int, argb: Int) = IntArray(width * height) { argb }

    @Test
    fun `shouldRenderTick is false when nothing is connected`() {
        // The case that cost 57.5% of a core: outputs configured, app launched, no OBS client.
        assertFalse(BrowserSourceVideoRenderer.shouldRenderTick(enabled = true, subscriberCount = 0))
    }

    @Test
    fun `shouldRenderTick is false when the output is switched off`() {
        // browserSourceEnabled gated serving but never production, so a disabled output used to
        // render at full rate for a client that would have been refused anyway.
        assertFalse(BrowserSourceVideoRenderer.shouldRenderTick(enabled = false, subscriberCount = 3))
    }

    @Test
    fun `shouldRenderTick is false when disabled even with no subscribers`() {
        assertFalse(BrowserSourceVideoRenderer.shouldRenderTick(enabled = false, subscriberCount = 0))
    }

    @Test
    fun `shouldRenderTick is true only for an enabled output with a subscriber`() {
        assertTrue(BrowserSourceVideoRenderer.shouldRenderTick(enabled = true, subscriberCount = 1))
        assertTrue(BrowserSourceVideoRenderer.shouldRenderTick(enabled = true, subscriberCount = 12))
    }

    @Test
    fun `the idle poll is slower than the fastest tick rate`() {
        // Parking has to be cheaper than ticking or it defeats the point; 60fps is the ceiling
        // tickDelayMs can reach (16ms), so the poll must be well above it.
        assertTrue(BrowserSourceVideoRenderer.IDLE_POLL_MS > renderer(fps = 60).tickDelayMs)
    }

    @Test
    fun `a client connecting after an idle park is reseeded with a full frame`() {
        // The loop drops its baseline while parked, so the first tick after waking takes
        // decideTick's previous == null path — a full frame, not a delta against pixels the
        // new client never received.
        val frame = solid(4, 4, 0xFF00FF00.toInt())
        val decision = BrowserSourceVideoRenderer.decideTick(
            intBuf = frame,
            previous = null,
            width = 4,
            height = 4,
            newSubscriberJoined = true,
            elapsedMs = 10_000L,
            lastFullFrameAtMs = 10_000L,
        )
        assertNotNull(decision)
        assertTrue(decision.forceFullFrame)
        assertEquals(0, decision.rect.x)
        assertEquals(0, decision.rect.y)
        assertEquals(4, decision.rect.w)
        assertEquals(4, decision.rect.h)
    }

    @Test
    fun `BrowserSourceFrame equality is reference-based on the png bytes`() {
        val a = BrowserSourceFrame(0, 0, 4, 4, 4, 4, byteArrayOf(1, 2, 3))
        val b = BrowserSourceFrame(0, 0, 4, 4, 4, 4, byteArrayOf(1, 2, 3))
        assertNotEquals(a, b)
        assertEquals(a, a)
    }

    @Test
    fun `computeDirtyRect finds a single changed pixel`() {
        val width = 5
        val height = 5
        val previous = solid(width, height, 0)
        val current = previous.copyOf()
        current[2 * width + 3] = 1

        val rect = BrowserSourceVideoRenderer.computeDirtyRect(current, previous, width, height)

        assertEquals(BrowserSourceVideoRenderer.DirtyRect(3, 2, 1, 1), rect)
    }

    @Test
    fun `computeDirtyRect spans the bounding box of every changed pixel`() {
        val width = 6
        val height = 6
        val previous = solid(width, height, 0)
        val current = previous.copyOf()
        current[1 * width + 4] = 1
        current[3 * width + 1] = 1

        val rect = BrowserSourceVideoRenderer.computeDirtyRect(current, previous, width, height)

        assertEquals(BrowserSourceVideoRenderer.DirtyRect(1, 1, 4, 3), rect)
    }

    @Test
    fun `computeDirtyRect covers the full frame when every pixel changed`() {
        val width = 3
        val height = 3
        val previous = solid(width, height, 0)
        val current = solid(width, height, 1)

        val rect = BrowserSourceVideoRenderer.computeDirtyRect(current, previous, width, height)

        assertEquals(BrowserSourceVideoRenderer.DirtyRect(0, 0, width, height), rect)
    }

    @Test
    fun `cropPixels extracts the requested sub-rectangle`() {
        val srcWidth = 4
        val src = IntArray(srcWidth * 4) { it }

        val cropped = BrowserSourceVideoRenderer.cropPixels(src, srcWidth, x = 1, y = 1, w = 2, h = 2)

        assertEquals(listOf(5, 6, 9, 10), cropped.toList())
    }

    @Test
    fun `encodeFrame writes JPEG for a fully opaque frame`() {
        val argb = solid(2, 2, 0xFFFF0000.toInt())

        val bytes = BrowserSourceVideoRenderer.encodeFrame(argb, 2, 2)

        assertEquals(0xFF, bytes[0].toInt() and 0xFF)
        assertEquals(0xD8, bytes[1].toInt() and 0xFF)
    }

    @Test
    fun `encodeFrame writes PNG when any pixel has transparency`() {
        val argb = solid(2, 2, 0xFFFF0000.toInt())
        argb[0] = 0x00000000

        val bytes = BrowserSourceVideoRenderer.encodeFrame(argb, 2, 2)

        assertEquals(0x89, bytes[0].toInt() and 0xFF)
    }

    @Test
    fun `decideTick forces a full frame on the very first tick`() {
        val intBuf = solid(2, 2, 1)

        val decision = BrowserSourceVideoRenderer.decideTick(
            intBuf, previous = null, width = 2, height = 2,
            newSubscriberJoined = false, elapsedMs = 0, lastFullFrameAtMs = 0
        )

        assertNotNull(decision)
        assertTrue(decision.forceFullFrame)
        assertTrue(decision.contentChanged)
        assertEquals(BrowserSourceVideoRenderer.DirtyRect(0, 0, 2, 2), decision.rect)
    }

    @Test
    fun `decideTick returns null when nothing changed and no reseed is due`() {
        val buf = solid(2, 2, 7)

        val decision = BrowserSourceVideoRenderer.decideTick(
            buf, previous = buf.copyOf(), width = 2, height = 2,
            newSubscriberJoined = false, elapsedMs = 1000, lastFullFrameAtMs = 0
        )

        assertNull(decision)
    }

    @Test
    fun `decideTick emits a dirty rect delta when only content changed`() {
        val previous = solid(3, 3, 0)
        val current = previous.copyOf()
        current[4] = 1

        val decision = BrowserSourceVideoRenderer.decideTick(
            current, previous, width = 3, height = 3,
            newSubscriberJoined = false, elapsedMs = 1000, lastFullFrameAtMs = 0
        )

        assertNotNull(decision)
        assertFalse(decision.forceFullFrame)
        assertTrue(decision.contentChanged)
        assertEquals(BrowserSourceVideoRenderer.DirtyRect(1, 1, 1, 1), decision.rect)
    }

    @Test
    fun `decideTick forces a full frame for a new subscriber even without content change`() {
        val buf = solid(2, 2, 7)

        val decision = BrowserSourceVideoRenderer.decideTick(
            buf, previous = buf.copyOf(), width = 2, height = 2,
            newSubscriberJoined = true, elapsedMs = 1000, lastFullFrameAtMs = 0
        )

        assertNotNull(decision)
        assertTrue(decision.forceFullFrame)
        assertFalse(decision.contentChanged)
        assertEquals(BrowserSourceVideoRenderer.DirtyRect(0, 0, 2, 2), decision.rect)
    }

    @Test
    fun `decideTick forces a periodic full-frame reseed on its own schedule`() {
        val buf = solid(2, 2, 7)

        val notYetDue = BrowserSourceVideoRenderer.decideTick(
            buf, previous = buf.copyOf(), width = 2, height = 2,
            newSubscriberJoined = false, elapsedMs = 4999, lastFullFrameAtMs = 0
        )
        assertNull(notYetDue)

        val due = BrowserSourceVideoRenderer.decideTick(
            buf, previous = buf.copyOf(), width = 2, height = 2,
            newSubscriberJoined = false, elapsedMs = 5000, lastFullFrameAtMs = 0
        )

        assertNotNull(due)
        assertTrue(due.forceFullFrame)
        assertFalse(due.contentChanged)
        assertEquals(BrowserSourceVideoRenderer.DirtyRect(0, 0, 2, 2), due.rect)
    }

    @Test
    fun `decideTick prefers a full frame when content changes and a subscriber joins together`() {
        val previous = solid(2, 2, 0)
        val current = previous.copyOf()
        current[0] = 1

        val decision = BrowserSourceVideoRenderer.decideTick(
            current, previous, width = 2, height = 2,
            newSubscriberJoined = true, elapsedMs = 1000, lastFullFrameAtMs = 0
        )

        assertNotNull(decision)
        assertTrue(decision.forceFullFrame)
        assertTrue(decision.contentChanged)
        assertEquals(BrowserSourceVideoRenderer.DirtyRect(0, 0, 2, 2), decision.rect)
    }

    @Test
    fun `crossfadeDurationMs is zero-floored to 100ms when neither crossfade is enabled`() {
        assertEquals(100, BrowserSourceVideoRenderer.crossfadeDurationMs(false, 0, false, 0))
        assertEquals(100, BrowserSourceVideoRenderer.crossfadeDurationMs(false, 5000, false, 5000))
    }

    @Test
    fun `crossfadeDurationMs uses the longer of the two enabled durations`() {
        assertEquals(
            2000,
            BrowserSourceVideoRenderer.crossfadeDurationMs(
                bibleCrossfadeEnabled = true, bibleTransitionDurationMs = 2000,
                songCrossfadeEnabled = true, songTransitionDurationMs = 500,
            )
        )
        assertEquals(
            300,
            BrowserSourceVideoRenderer.crossfadeDurationMs(
                bibleCrossfadeEnabled = false, bibleTransitionDurationMs = 9000,
                songCrossfadeEnabled = true, songTransitionDurationMs = 300,
            )
        )
    }

    @Test
    fun `isScreenCrossfadeActive requires at least one crossfade enabled`() {
        assertFalse(
            BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                bibleCrossfadeEnabled = false, songCrossfadeEnabled = false,
                currentMode = Presenting.BIBLE, previousMode = Presenting.LYRICS,
            )
        )
        assertTrue(
            BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                bibleCrossfadeEnabled = true, songCrossfadeEnabled = false,
                currentMode = Presenting.BIBLE, previousMode = Presenting.LYRICS,
            )
        )
    }

    @Test
    fun `isScreenCrossfadeActive requires neither mode to be NONE`() {
        assertFalse(
            BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                bibleCrossfadeEnabled = true, songCrossfadeEnabled = true,
                currentMode = Presenting.NONE, previousMode = Presenting.LYRICS,
            )
        )
        assertFalse(
            BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                bibleCrossfadeEnabled = true, songCrossfadeEnabled = true,
                currentMode = Presenting.BIBLE, previousMode = Presenting.NONE,
            )
        )
        assertTrue(
            BrowserSourceVideoRenderer.isScreenCrossfadeActive(
                bibleCrossfadeEnabled = true, songCrossfadeEnabled = true,
                currentMode = Presenting.BIBLE, previousMode = Presenting.LYRICS,
            )
        )
    }

    @Test
    fun `showsContentFor maps every content type to its own screen assignment flag`() {
        val allOn = ScreenAssignment()
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.BIBLE, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.LYRICS, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.PICTURES, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.PRESENTATION, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.ANNOUNCEMENTS, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.LOWER_THIRD, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.MEDIA, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.WEBSITE, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.CANVAS, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.QA, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.STT, allOn))
        assertTrue(BrowserSourceVideoRenderer.showsContentFor(Presenting.DICTIONARY, allOn))
    }

    @Test
    fun `showsContentFor is false when the matching flag is off`() {
        val allOff = ScreenAssignment(
            bibleMode = Constants.SONG_LANG_OFF,
            songMode = Constants.SONG_LANG_OFF,
            showPictures = false,
            showAnnouncements = false,
            showStreaming = false,
            showMedia = false,
            showWebsite = false,
            showCanvas = false,
            showQA = false,
            showSTT = false,
            showDictionary = false,
        )
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.BIBLE, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.LYRICS, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.PICTURES, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.PRESENTATION, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.ANNOUNCEMENTS, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.LOWER_THIRD, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.MEDIA, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.WEBSITE, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.CANVAS, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.QA, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.STT, allOff))
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.DICTIONARY, allOff))
    }

    @Test
    fun `showsContentFor is false for NONE regardless of screen assignment`() {
        assertFalse(BrowserSourceVideoRenderer.showsContentFor(Presenting.NONE, ScreenAssignment()))
    }

    @Test
    fun `the tick delay is derived from the requested fps`() {
        assertEquals(1000L / 30, renderer(fps = 30).tickDelayMs)
        assertEquals(1000L / 60, renderer(fps = 60).tickDelayMs)
    }

    @Test
    fun `an out-of-range fps is clamped to 1 to 60`() {
        assertEquals(1000L / 1, renderer(fps = 0).tickDelayMs)
        assertEquals(1000L / 1, renderer(fps = -5).tickDelayMs)
        assertEquals(1000L / 60, renderer(fps = 200).tickDelayMs)
    }

    @Test
    fun `stop before start is a safe no-op`() {
        renderer().stop()
    }

    @Test
    fun `frames has no replay value before start is ever called`() {
        assertTrue(renderer().frames.replayCache.isEmpty())
    }
}
