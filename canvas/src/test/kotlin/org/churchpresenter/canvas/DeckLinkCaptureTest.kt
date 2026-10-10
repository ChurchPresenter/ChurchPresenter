package org.churchpresenter.canvas

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.diagnostics.CrashReportSweep
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeckLinkCaptureTest {

    private val sweep = CrashReportSweep()

    @BeforeTest
    fun mark() = sweep.mark()

    @AfterTest
    fun clean() = sweep.sweep()

    private val source = SceneSource.CameraSource(
        id = "dl",
        name = "DeckLink",
        isDeckLink = true,
        deckLinkIndex = 0,
        videoFormat = "1080p30",
        videoConnection = 2,
    )

    private class StopPolling : CancellationException("enough frames")

    /** A card that answers from [frames] in order, and stops the poll once they run out. */
    private class FakeCard(
        val present: Boolean = true,
        val modes: List<DeckLinkManager.InputMode> = listOf(DeckLinkManager.InputMode("1080p30", "Hp30")),
        val opens: Boolean = true,
        val outputActive: Boolean = false,
        val frames: List<IntArray?> = emptyList(),
    ) : DeckLinkInputs {
        var opened: Triple<Int, String, Int>? = null
        private var polled = 0

        override fun findDevice(index: Int) =
            if (present) DeckLinkManager.DeckLinkDevice(index, "UltraStudio Recorder") else null

        override fun inputModes(index: Int) = modes

        override fun videoConnections(index: Int) = emptyList<DeckLinkManager.VideoConnection>()

        override fun openInput(index: Int, mode: String, connection: Int): Boolean {
            opened = Triple(index, mode, connection)
            return opens
        }

        override fun isOutputActive(index: Int) = outputActive

        override fun inputFrame(index: Int): IntArray? = frames.getOrNull(polled++)

        override suspend fun pause(millis: Long) {
            if (polled >= frames.size) throw StopPolling()
        }
    }

    private fun capture(card: FakeCard, entry: CacheEntry = CacheEntry()): CacheEntry = runBlocking {
        try {
            DeckLinkCapture(source, entry, DeckLinkOpenReports(), card).run()
        } catch (_: StopPolling) {
            // The fake ran out of frames.
        }
        entry
    }

    private fun frame(w: Int, h: Int) = IntArray(2 + w * h) { 0xFF00FF00.toInt() }.also { it[0] = w; it[1] = h }

    @Test
    fun `a card that is not there is never opened`() {
        val card = FakeCard(present = false)

        val entry = capture(card)

        assertEquals(CameraFailure.DECKLINK_NOT_FOUND, entry.error.value)
        assertNull(card.opened)
    }

    @Test
    fun `a card with no input modes is never opened`() {
        val card = FakeCard(modes = emptyList())

        val entry = capture(card)

        assertEquals(CameraFailure.DECKLINK_NO_INPUT, entry.error.value)
        assertNull(card.opened)
    }

    @Test
    fun `a card that will not open says so`() {
        val entry = capture(FakeCard(opens = false))

        assertEquals(CameraFailure.DECKLINK_OPEN_FAILED, entry.error.value)
    }

    @Test
    fun `a card already sending an output says its input is in use`() {
        val entry = capture(FakeCard(opens = false, outputActive = true))

        assertEquals(CameraFailure.DECKLINK_INPUT_IN_USE, entry.error.value)
    }

    @Test
    fun `the card is opened on the source's format and connection`() {
        val card = FakeCard(frames = listOf(frame(2, 2)))

        capture(card)

        assertEquals(Triple(0, "1080p30", 2), card.opened)
    }

    @Test
    fun `a polled frame goes on screen and clears any earlier error`() {
        val entry = CacheEntry().apply { error.value = CameraFailure.UNKNOWN }

        capture(FakeCard(frames = listOf(frame(4, 3))), entry)

        assertNull(entry.error.value)
        val shown = assertNotNull(entry.frame.value)
        assertEquals(4, shown.width)
        assertEquals(3, shown.height)
    }

    @Test
    fun `a lost signal clears the picture once enough polls come back empty`() {
        val entry = capture(FakeCard(frames = listOf(frame(2, 2)) + List(31) { null }))

        assertNull(entry.frame.value)
    }

    @Test
    fun `a brief dropout keeps the last picture up`() {
        val entry = capture(FakeCard(frames = listOf(frame(2, 2)) + List(5) { null }))

        assertNotNull(entry.frame.value)
    }

    @Test
    fun `a poll with no usable frame shows nothing`() = runBlocking {
        val entry = CacheEntry()

        assertFalse(showDeckLinkFrame(null, entry, first = true))
        assertFalse(showDeckLinkFrame(intArrayOf(1, 1), entry, first = true))
        assertFalse(showDeckLinkFrame(intArrayOf(0, 2, 0, 0), entry, first = true))
        assertFalse(showDeckLinkFrame(intArrayOf(2, -1, 0, 0), entry, first = true))
        assertNull(entry.frame.value)
        assertTrue(showDeckLinkFrame(frame(1, 1), entry, first = false))
    }

    @Test
    fun `a card that never sends a picture stays blank through a long run of empty polls`() {
        val entry = capture(FakeCard(frames = List(40) { null }))

        assertNull(entry.frame.value)
        assertNull(entry.error.value, "an open card with no signal is not an error")
    }

    @Test
    fun `a source on auto opens the card with no mode`() {
        val card = FakeCard(frames = listOf(frame(2, 2)))

        runBlocking {
            try {
                DeckLinkCapture(source.copy(videoFormat = ""), CacheEntry(), DeckLinkOpenReports(), card).run()
            } catch (_: StopPolling) {
                // The fake ran out of frames.
            }
        }

        assertEquals(Triple(0, "", 2), card.opened)
    }

    @Test
    fun `a card that fails to open again still says so after its one report`() {
        val reports = DeckLinkOpenReports()
        val entries = List(2) { CacheEntry() }

        runBlocking {
            entries.forEach { DeckLinkCapture(source, it, reports, FakeCard(opens = false)).run() }
        }

        assertEquals(listOf(CameraFailure.DECKLINK_OPEN_FAILED, CameraFailure.DECKLINK_OPEN_FAILED), entries.map { it.error.value })
        assertFalse(reports.claim(source.deckLinkIndex), "the card's one report was already spent")
    }
}
