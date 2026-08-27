package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.ndi.FakeNdiLibrary
import org.churchpresenter.ndi.NdiOutputMode
import org.churchpresenter.ndi.NdiPixelFormat
import org.churchpresenter.ndi.NdiSender
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private const val OUTPUT_NAME = "Lower Third"
private const val W = 8
private const val H = 4
private const val POLL_MS = 2L
private const val WAIT_MS = 4_000L

/** Enough frames at the 60fps the tests build to cross several one-second poll ticks. */
private const val FRAMES_OVER_SEVERAL_SECONDS = 200

/**
 * The NDI output's app-side renderer: what reaches the wire, and when it does not.
 *
 * Drives a real [NdiSender] over `:ndi`'s [FakeNdiLibrary], so what is asserted is the frames an NDI
 * receiver would have got — not that a stub was called. No NDI Runtime is involved.
 */
class NdiVideoRendererTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /**
     * Polls with a short [delay] rather than [kotlinx.coroutines.yield]: yielding in a
     * `runBlocking` loop is a busy-wait that pins a core, and this suite runs on four parallel
     * forks. Still ends on the positive signal — the deadline only fails the test.
     */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    private fun renderer(
        lib: FakeNdiLibrary,
        mode: NdiOutputMode = NdiOutputMode.ALPHA,
        enabled: Boolean = true,
        onReceiverSeen: () -> Unit = {},
    ): Pair<NdiVideoRenderer, NdiSender> {
        val sender = NdiSender(lib, OUTPUT_NAME, mode, fps = 60)
        val assignment = mutableStateOf(ScreenAssignment(ndiEnabled = enabled))
        val context = OffscreenOutputContext(
            presenterManager = PresenterManager(),
            appSettingsState = mutableStateOf(AppSettings()),
            screenAssignmentState = assignment,
            effectiveModeState = mutableStateOf(Presenting.NONE),
            outputIndex = 0,
            kind = OffscreenOutputKind.NDI,
        )
        return NdiVideoRenderer(
            sender, context, assignment, width = W, height = H, fps = 60, onReceiverSeen = onReceiverSeen,
        ) to sender
    }

    // ── What is put on the network ──────────────────────────────────────────────

    @Test
    fun `starting an output opens the sender and sends frames`() {
        val lib = FakeNdiLibrary()
        val (r, _) = renderer(lib)
        r.start(scope)
        waitFor("a frame on the wire") { lib.sent.isNotEmpty() }
        r.stop()

        assertEquals(listOf(OUTPUT_NAME), lib.created)
        assertEquals(W, lib.sent.first().width)
        assertEquals(H, lib.sent.first().height)
    }

    @Test
    fun `alpha mode puts a BGRA frame on the network`() {
        val lib = FakeNdiLibrary()
        val (r, _) = renderer(lib, NdiOutputMode.ALPHA)
        r.start(scope)
        waitFor("a frame") { lib.sent.isNotEmpty() }
        r.stop()

        // The whole point of the feature: a receiver gets a keyed layer, not a flattened picture.
        assertEquals(NdiPixelFormat.BGRA, lib.sent.first().format)
    }

    @Test
    fun `fill mode puts an opaque BGRX frame on the network`() {
        val lib = FakeNdiLibrary()
        val (r, _) = renderer(lib, NdiOutputMode.FILL)
        r.start(scope)
        waitFor("a frame") { lib.sent.isNotEmpty() }
        r.stop()

        assertEquals(NdiPixelFormat.BGRX, lib.sent.first().format)
    }

    @Test
    fun `fill and key mode puts two sources on the network`() {
        val lib = FakeNdiLibrary()
        val (r, _) = renderer(lib, NdiOutputMode.FILL_AND_KEY)
        r.start(scope)
        waitFor("a frame on each source") {
            lib.framesFor(OUTPUT_NAME).isNotEmpty() && lib.framesFor("$OUTPUT_NAME Key").isNotEmpty()
        }
        r.stop()

        assertEquals(listOf(OUTPUT_NAME, "$OUTPUT_NAME Key"), lib.created)
    }

    @Test
    fun `it keeps sending rather than only sending what changed`() {
        // The opposite trade to a Browser Source, and deliberate: a receiver expects a continuous
        // stream, and an NDI source that stops sending reads as a dead one.
        val lib = FakeNdiLibrary()
        val (r, _) = renderer(lib)
        r.start(scope)
        waitFor("several frames of unchanging content") { lib.sent.size >= 3 }
        r.stop()
    }

    // ── When nothing is put on the network ──────────────────────────────────────

    @Test
    fun `a sender the runtime refused stops the renderer before it renders`() {
        val lib = FakeNdiLibrary(refuseNames = setOf(OUTPUT_NAME))
        val (r, sender) = renderer(lib)
        r.start(scope)

        assertFalse(sender.isOpen)
        assertTrue(lib.sent.isEmpty(), "there is no point rendering frames with nowhere to put them")
    }

    @Test
    fun `an output switched off sends nothing`() {
        val lib = FakeNdiLibrary()
        val (r, sender) = renderer(lib, enabled = false)
        r.start(scope)
        // The sender still opens — the source stays on the network — but no frame is rendered.
        waitFor("the sender to be open") { sender.isOpen }
        r.stop()

        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `stopping takes the source off the network`() {
        val lib = FakeNdiLibrary()
        val (r, sender) = renderer(lib)
        r.start(scope)
        waitFor("a frame") { lib.sent.isNotEmpty() }
        r.stop()

        // Left open, every receiver would hold the last frame and show a frozen lower third.
        assertFalse(sender.isOpen)
        assertEquals(1, lib.destroyed.size)
    }

    // ── The gate, and the stored mode ───────────────────────────────────────────

    @Test
    fun `nothing is rendered unless the output is on and the sender is open`() {
        assertTrue(NdiVideoRenderer.shouldSend(enabled = true, senderOpen = true))
        assertFalse(NdiVideoRenderer.shouldSend(enabled = false, senderOpen = true))
        assertFalse(NdiVideoRenderer.shouldSend(enabled = true, senderOpen = false))
        assertFalse(NdiVideoRenderer.shouldSend(enabled = false, senderOpen = false))
    }

    @Test
    fun `the receiver count comes from the sender`() {
        val lib = FakeNdiLibrary()
        lib.connections = 2
        val (r, _) = renderer(lib)
        assertEquals(0, r.connectionCount(), "nothing is watching a source that is not on the network")
        r.start(scope)
        assertEquals(2, r.connectionCount())
        r.stop()
    }

    @Test
    fun `each stored mode string maps to its behaviour`() {
        assertEquals(NdiOutputMode.ALPHA, NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = Constants.NDI_MODE_ALPHA)))
        assertEquals(NdiOutputMode.FILL, NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = Constants.NDI_MODE_FILL)))
        assertEquals(
            NdiOutputMode.FILL_AND_KEY,
            NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = Constants.NDI_MODE_FILL_AND_KEY)),
        )
    }

    @Test
    fun `an unrecognised stored mode falls back to alpha rather than failing`() {
        // A settings file from a future build, or one edited by hand. Alpha is the default anyway.
        assertEquals(NdiOutputMode.ALPHA, NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = "hologram")))
        assertEquals(NdiOutputMode.ALPHA, NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = "")))
    }

    @Test
    fun `every mode round-trips through its stored name`() {
        for (mode in NdiOutputMode.entries) {
            val stored = NdiVideoRenderer.storedModeOf(mode)
            assertEquals(mode, NdiVideoRenderer.modeOf(ScreenAssignment(ndiMode = stored)), "$mode did not survive")
        }
    }

    @Test
    fun `a new output defaults to alpha`() {
        assertEquals(NdiOutputMode.ALPHA, NdiVideoRenderer.modeOf(ScreenAssignment()))
    }

    // ── Noticing that someone is actually watching ──────────────────────────────

    @Test
    fun `isReceiverPollTick asks once per second and then stops`() {
        // The first frame asks, the rest of that second does not: an unwatched source would
        // otherwise make a native call on every one of its 60 frames, for ever.
        assertTrue(NdiVideoRenderer.isReceiverPollTick(frame = 0, fps = 60, alreadySeen = false))
        assertFalse(NdiVideoRenderer.isReceiverPollTick(frame = 1, fps = 60, alreadySeen = false))
        assertFalse(NdiVideoRenderer.isReceiverPollTick(frame = 59, fps = 60, alreadySeen = false))
        assertTrue(NdiVideoRenderer.isReceiverPollTick(frame = 60, fps = 60, alreadySeen = false))

        // Once the answer is known there is nothing left to ask.
        assertFalse(NdiVideoRenderer.isReceiverPollTick(frame = 0, fps = 60, alreadySeen = true))
        assertFalse(NdiVideoRenderer.isReceiverPollTick(frame = 120, fps = 60, alreadySeen = true))

        // A renderer built with no cadence polls every frame rather than dividing by zero.
        assertTrue(NdiVideoRenderer.isReceiverPollTick(frame = 7, fps = 0, alreadySeen = false))
    }

    @Test
    fun `a source nobody is watching reports no usage`() {
        val lib = FakeNdiLibrary().apply { connections = 0 }
        var seen = 0
        val (r, sender) = renderer(lib, onReceiverSeen = { seen++ })
        sender.open()

        repeat(FRAMES_OVER_SEVERAL_SECONDS) { r.observeReceivers() }

        // NDI keeps announcing an unwatched source, so "the output is on" must not count as usage.
        assertEquals(0, seen)
    }

    @Test
    fun `a receiver tuning in is reported once, not once per frame`() {
        val lib = FakeNdiLibrary().apply { connections = 2 }
        var seen = 0
        val (r, sender) = renderer(lib, onReceiverSeen = { seen++ })
        sender.open()

        repeat(FRAMES_OVER_SEVERAL_SECONDS) { r.observeReceivers() }

        // Two receivers over several seconds of frames is still one service with NDI in use.
        assertEquals(1, seen)
    }

    @Test
    fun `a receiver that arrives mid-service is still noticed`() {
        val lib = FakeNdiLibrary().apply { connections = 0 }
        var seen = 0
        val (r, sender) = renderer(lib, onReceiverSeen = { seen++ })
        sender.open()
        repeat(FRAMES_OVER_SEVERAL_SECONDS) { r.observeReceivers() }
        assertEquals(0, seen)

        // The OBS operator opens the source ten minutes in, which is the normal case.
        lib.connections = 1
        repeat(FRAMES_OVER_SEVERAL_SECONDS) { r.observeReceivers() }

        assertEquals(1, seen)
    }
}
