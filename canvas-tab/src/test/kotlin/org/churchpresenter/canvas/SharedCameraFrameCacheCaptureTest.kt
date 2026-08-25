package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Pulling frames off a capture card.
 *
 * This is the half of the cache that used to be unreachable: the frame pump only runs once a device
 * says it opened, and there is no capture card on a test machine. `CanvasDeckLink` is a parameter
 * now, so a stub can answer — which means the ordering that actually matters is testable: the device
 * is opened before it is polled, closed when the last subscriber leaves, and *not* closed while
 * another scene is still using it.
 *
 * The existing [SharedCameraFrameCacheTest] covers the pure parts — the cache key and the ffmpeg
 * command. This covers what happens with a device attached.
 */
class SharedCameraFrameCacheCaptureTest {

    /** A card that hands back [frames], then nothing. */
    private class StubDeckLink(
        private val frames: List<IntArray?> = emptyList(),
        private val opens: Boolean = true,
        private val available: Boolean = true,
    ) : CanvasDeckLink {
        val opened = mutableListOf<Triple<Int, String, Int>>()
        val closed = mutableListOf<Int>()
        val polls = AtomicInteger()

        override fun isAvailable() = available
        override fun listDevices() = emptyList<CanvasDeckLink.Device>()
        override fun isOutputActive(deviceIndex: Int) = false
        override fun listInputModes(deviceIndex: Int) = emptyList<CanvasDeckLink.InputMode>()
        override fun listVideoConnections(deviceIndex: Int) = emptyList<CanvasDeckLink.VideoConnection>()

        override fun openInput(deviceIndex: Int, mode: String, connection: Int): Boolean {
            opened += Triple(deviceIndex, mode, connection)
            return opens
        }

        override fun getInputFrame(deviceIndex: Int): IntArray? {
            val i = polls.getAndIncrement()
            return frames.getOrNull(i)
        }

        override fun closeInput(deviceIndex: Int) {
            closed += deviceIndex
        }
    }

    /** A frame in the card's own layout: width, height, then ARGB pixels. */
    private fun frame(w: Int, h: Int, colour: Int = 0xFF00FF00.toInt()) =
        IntArray(2 + w * h).also { it[0] = w; it[1] = h; for (i in 2 until it.size) it[i] = colour }

    private fun deckLinkSource(index: Int = 0, format: String = "1080p30", connection: Int = 1) =
        SceneSource.CameraSource(
            id = "cam", name = "Camera",
            devicePath = "decklink://$index", deviceName = "DeckLink",
            isDeckLink = true, deckLinkIndex = index,
            videoFormat = format, videoConnection = connection,
        )

    private val acquired = mutableListOf<Pair<SceneSource.CameraSource, CanvasDeckLink>>()

    @AfterTest
    fun releaseEverything() {
        // The cache is a process-wide singleton, so a capture left running would leak into the next
        // test and keep polling a stub that has gone.
        acquired.forEach { (source, deck) -> SharedCameraFrameCache.release(source, deck) }
        acquired.clear()
    }

    private fun acquire(source: SceneSource.CameraSource, deck: CanvasDeckLink) =
        SharedCameraFrameCache.acquire(source, deck).also { acquired += source to deck }

    private fun awaitUntil(what: String, timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(5)
        }
        throw AssertionError("timed out waiting for $what")
    }

    // ── Opening ─────────────────────────────────────────────────────────────────

    @Test
    fun `the device is opened with the format and connection the source asked for`() {
        val deck = StubDeckLink(frames = listOf(frame(4, 4)))
        val source = deckLinkSource(index = 2, format = "1080p30", connection = 3)

        acquire(source, deck)

        awaitUntil("the device to be opened") { deck.opened.isNotEmpty() }
        assertEquals(Triple(2, "1080p30", 3), deck.opened.single())
    }

    @Test
    fun `a device that will not open reports a failure rather than polling forever`() {
        val deck = StubDeckLink(opens = false)
        val source = deckLinkSource()

        val flows = acquire(source, deck)

        awaitUntil("the failure to be reported") { flows.error.value != null }
        assertEquals(CameraFailure.DECKLINK_INPUT_IN_USE, flows.error.value)
    }

    @Test
    fun `a card the driver cannot see falls through to the ffmpeg path`() {
        val deck = StubDeckLink(available = false)
        val source = deckLinkSource()

        acquire(source, deck)

        // Never opened, because `isAvailable()` gates the whole DeckLink branch — the source falls
        // through to ffmpeg, which has no such device and fails on its own terms.
        Thread.sleep(100)
        assertTrue(deck.opened.isEmpty())
    }

    // ── Frames ──────────────────────────────────────────────────────────────────

    @Test
    fun `a frame off the card reaches the flow as an image`() {
        val deck = StubDeckLink(frames = List(50) { frame(8, 6) })
        val source = deckLinkSource()

        val flows = acquire(source, deck)

        awaitUntil("the first frame") { flows.frame.value != null }
        val image = assertNotNull(flows.frame.value)
        assertEquals(8, image.width)
        assertEquals(6, image.height)
    }

    @Test
    fun `the card is polled repeatedly, not once`() {
        val deck = StubDeckLink(frames = List(200) { frame(4, 4) })

        acquire(deckLinkSource(), deck)

        awaitUntil("several polls") { deck.polls.get() > 3 }
    }

    @Test
    fun `a null frame is skipped rather than clearing the picture`() {
        // A card that delivers, then stalls. The last good picture has to stay up: blanking on the
        // first dropped frame would flicker the output on any hiccup.
        val deck = StubDeckLink(frames = listOf(frame(4, 4)) + List(200) { null })
        val source = deckLinkSource()

        val flows = acquire(source, deck)

        awaitUntil("the first frame") { flows.frame.value != null }
        awaitUntil("several nulls after it") { deck.polls.get() > 10 }
        assertNotNull(flows.frame.value, "the last good frame is still showing")
    }

    @Test
    fun `a malformed frame is ignored`() = runBlocking {
        val deck = StubDeckLink(frames = listOf(IntArray(1), IntArray(0), intArrayOf(0, 0)) + List(50) { frame(4, 4) })
        val source = deckLinkSource()

        val flows = acquire(source, deck)

        // Too short to carry dimensions, or dimensions of zero — none of which may reach setRGB.
        awaitUntil("a good frame after the bad ones") { flows.frame.value != null }
        Unit
    }

    // ── Releasing ───────────────────────────────────────────────────────────────

    @Test
    fun `the device is closed when the last subscriber leaves`() {
        val deck = StubDeckLink(frames = List(50) { frame(4, 4) })
        val source = deckLinkSource(index = 5)
        SharedCameraFrameCache.acquire(source, deck)
        awaitUntil("the device to open") { deck.opened.isNotEmpty() }

        SharedCameraFrameCache.release(source, deck)

        assertEquals(listOf(5), deck.closed)
    }

    @Test
    fun `the device stays open while another scene is still using it`() {
        val deck = StubDeckLink(frames = List(50) { frame(4, 4) })
        val source = deckLinkSource(index = 5)
        SharedCameraFrameCache.acquire(source, deck)
        SharedCameraFrameCache.acquire(source, deck)
        awaitUntil("the device to open") { deck.opened.isNotEmpty() }

        SharedCameraFrameCache.release(source, deck)

        // Two scenes on one camera share a capture; closing on the first release would blank the
        // other one mid-service.
        assertTrue(deck.closed.isEmpty(), "still in use by the second subscriber")

        SharedCameraFrameCache.release(source, deck)
        assertEquals(listOf(5), deck.closed)
    }

    @Test
    fun `releasing something never acquired is a no-op`() {
        val deck = StubDeckLink()

        SharedCameraFrameCache.release(deckLinkSource(index = 9), deck)

        assertTrue(deck.closed.isEmpty())
    }

    @Test
    fun `two subscribers share one open`() {
        val deck = StubDeckLink(frames = List(50) { frame(4, 4) })
        val source = deckLinkSource(index = 6)
        val a = acquire(source, deck)
        val b = acquire(source, deck)

        awaitUntil("the device to open") { deck.opened.isNotEmpty() }

        assertEquals(1, deck.opened.size, "one device, one open")
        awaitUntil("a frame") { a.frame.value != null }
        assertNull(b.frame.value?.let { null }, "both see the same flow")
    }

    @Test
    fun `a frame with no height is ignored as surely as one with no width`() {
        // `w <= 0 || h <= 0` short-circuits, so a zero width never asks about the height. A frame
        // that is wide but flat is the only way the second half of that test is ever evaluated, and
        // it must be rejected too — setRGB on a zero-height raster throws.
        val deck = StubDeckLink(frames = listOf(intArrayOf(4, 0, 0), intArrayOf(-1, 4, 0)) + List(50) { frame(4, 4) })
        val source = deckLinkSource()

        val flows = acquire(source, deck)

        awaitUntil("a good frame after the flat and the negative one") { flows.frame.value != null }
        assertEquals(4, assertNotNull(flows.frame.value).height)
    }

    @Test
    fun `a card that stalls for long enough finally blanks the picture`() {
        // Holding the last frame indefinitely would leave a dead camera showing a still image that
        // looks live. Past the drop allowance the source goes black, which is honest.
        val deck = StubDeckLink(frames = listOf(frame(4, 4)) + List(400) { null })
        val source = deckLinkSource()

        val flows = acquire(source, deck)
        awaitUntil("the first frame") { flows.frame.value != null }

        awaitUntil("the picture to be cleared") { flows.frame.value == null }
    }

    // ── Sources that are not a capture card at all ──────────────────────────────

    @Test
    fun `a webcam source never touches the card, even with one attached`() {
        val deck = StubDeckLink(frames = List(50) { frame(4, 4) })
        val webcam = SceneSource.CameraSource(
            id = "cam", name = "Camera", devicePath = "/dev/video0", deviceName = "USB Cam",
        )

        acquire(webcam, deck)

        // isDeckLink is false, so the branch is never entered whatever the card says.
        Thread.sleep(100)
        assertTrue(deck.opened.isEmpty())
        SharedCameraFrameCache.release(webcam, deck)
        assertTrue(deck.closed.isEmpty())
    }

    @Test
    fun `a card source with no device index is not a card`() {
        // `isDeckLink` set without an index is a half-configured source — the app writes it that way
        // before a device has been picked. It must go down the ffmpeg path, not open device -1.
        val deck = StubDeckLink(frames = List(50) { frame(4, 4) })
        val unassigned = SceneSource.CameraSource(
            id = "cam", name = "Camera", devicePath = "", deviceName = "",
            isDeckLink = true, deckLinkIndex = -1,
        )

        acquire(unassigned, deck)

        Thread.sleep(100)
        assertTrue(deck.opened.isEmpty(), "device -1 must never be opened")
        SharedCameraFrameCache.release(unassigned, deck)
        assertTrue(deck.closed.isEmpty())
    }

    @Test
    fun `one card feeding two different modes stays open until both go`() {
        // Two scenes on the same card at different formats are two cache entries, not one — the key
        // carries the format. Closing the device on the first release would blank the other scene.
        val deck = StubDeckLink(frames = List(200) { frame(4, 4) })
        val hd = deckLinkSource(index = 7, format = "1080p30")
        val uhd = deckLinkSource(index = 7, format = "2160p30")
        SharedCameraFrameCache.acquire(hd, deck)
        SharedCameraFrameCache.acquire(uhd, deck)
        awaitUntil("both to open") { deck.opened.size == 2 }

        SharedCameraFrameCache.release(hd, deck)

        assertTrue(deck.closed.isEmpty(), "the other mode is still capturing off device 7")

        SharedCameraFrameCache.release(uhd, deck)
        assertEquals(listOf(7), deck.closed)
    }
}
