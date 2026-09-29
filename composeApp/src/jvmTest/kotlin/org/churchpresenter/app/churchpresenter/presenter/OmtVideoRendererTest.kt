package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.omt.FakeOmtLibrary
import org.churchpresenter.omt.OmtOutputMode
import org.churchpresenter.omt.OmtQuality
import org.churchpresenter.omt.OmtSender
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

/** Idle ticks to watch go by before believing a parked renderer is really sending nothing. */
private const val PARKED_POLLS = 2

/**
 * The OMT output's app-side renderer: what reaches the wire, and when it does not.
 *
 * Drives a real [OmtSender] over `:omt`'s [FakeOmtLibrary], so what is asserted is the frames an OMT
 * receiver would have got. No library is loaded.
 */
class OmtVideoRendererTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun tearDown() {
        scope.cancel()
    }

    /** Polls a short [delay] apart and ends on the condition; the deadline only fails the test. */
    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            delay(POLL_MS)
        }
    }

    private fun renderer(
        lib: FakeOmtLibrary,
        mode: OmtOutputMode = OmtOutputMode.ALPHA,
        enabled: Boolean = true,
        receivers: Int = 1,
        onReceiverSeen: () -> Unit = {},
        /** Opens the sender up front, for tests that ask about receivers without starting a pump. */
        openSender: Boolean = false,
    ): OmtVideoRenderer {
        lib.connections = receivers
        val sender = OmtSender(lib, OUTPUT_NAME, mode, fps = 60).apply { if (openSender) open() }
        val assignment = mutableStateOf(ScreenAssignment(omtEnabled = enabled))
        val context = OffscreenOutputContext(
            presenterManager = PresenterManager(),
            appSettingsState = mutableStateOf(AppSettings()),
            screenAssignmentState = assignment,
            effectiveModeState = mutableStateOf(Presenting.NONE),
            outputIndex = 0,
            kind = OffscreenOutputKind.OMT,
        )
        return OmtVideoRenderer(
            sender, context, assignment, width = W, height = H, fps = 60, onReceiverSeen = onReceiverSeen,
        )
    }

    @Test
    fun `starting an output opens the sender and sends frames of its size`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib)
        r.start(scope)
        waitFor("a frame on the wire") { lib.sent.isNotEmpty() }
        r.stop()

        assertEquals(listOf(OUTPUT_NAME), lib.created)
        assertEquals(W, lib.sent.first().width)
        assertEquals(H, lib.sent.first().height)
    }

    @Test
    fun `alpha mode flags its frames as carrying alpha, and fill mode does not`() {
        val alpha = FakeOmtLibrary()
        renderer(alpha, OmtOutputMode.ALPHA).run {
            start(scope)
            waitFor("an alpha frame") { alpha.sent.isNotEmpty() }
            stop()
        }
        assertTrue(alpha.sent.first().alpha)

        val fill = FakeOmtLibrary()
        renderer(fill, OmtOutputMode.FILL).run {
            start(scope)
            waitFor("a fill frame") { fill.sent.isNotEmpty() }
            stop()
        }
        assertFalse(fill.sent.first().alpha)
    }

    @Test
    fun `a sender the library refused never renders`() {
        val lib = FakeOmtLibrary(refuseNames = setOf(OUTPUT_NAME))
        val r = renderer(lib)
        r.start(scope)
        r.stop()
        assertTrue(lib.created.isEmpty())
        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `an output switched off sends nothing and never asks who is watching`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib, enabled = false)
        r.start(scope)
        // The pump's idle ticks keep deciding; the sender is open, so the answer is the switch.
        waitFor("the sender to open") { lib.created.isNotEmpty() }
        // Read before stop(), which asks once on purpose to decide whether to clear the receivers.
        val sentWhileOff = lib.sent.size
        val askedWhileOff = lib.connectionQueries
        r.stop()
        assertEquals(0, sentWhileOff)
        assertEquals(0, askedWhileOff)
    }

    @Test
    fun `an output nobody is watching puts nothing on the wire`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib, receivers = 0)
        r.start(scope)
        waitFor("the pump to ask who is watching, twice") { lib.connectionQueries >= PARKED_POLLS }
        r.stop()
        assertTrue(lib.sent.isEmpty())
    }

    @Test
    fun `stopping takes the source off the network`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib)
        r.start(scope)
        waitFor("a frame") { lib.sent.isNotEmpty() }
        r.stop()
        assertEquals(1, lib.destroyed.size)
    }

    @Test
    fun `stopping a watched output leaves its receivers on a blank frame, then closes`() {
        // OBS keeps drawing the last frame it received after a source goes away, so the last one
        // sent has to be empty — otherwise quitting leaves a verse frozen on the stream.
        val lib = FakeOmtLibrary()
        val r = renderer(lib, OmtOutputMode.ALPHA, receivers = 1)
        r.start(scope)
        waitFor("a frame") { lib.sent.isNotEmpty() }
        r.stop()

        val last = lib.sent.last()
        assertEquals(W, last.width)
        assertEquals(H, last.height)
        assertTrue(last.bytes.all { it == 0.toByte() }, "the last frame sent is fully transparent")
        assertEquals(1, lib.destroyed.size)
    }

    @Test
    fun `stopping an output nobody is watching sends nothing more`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib, receivers = 0, openSender = true)
        r.stop()
        assertTrue(lib.sent.isEmpty())
        assertEquals(1, lib.destroyed.size)
    }

    @Test
    fun `the receiver count and the network name come from the sender`() {
        val lib = FakeOmtLibrary()
        val r = renderer(lib, receivers = 3)
        r.start(scope)
        waitFor("the sender to open") { lib.created.isNotEmpty() }
        assertEquals(3, r.receiverCount())
        assertEquals("FAKEHOST ($OUTPUT_NAME)", r.address())
        r.stop()
    }

    @Test
    fun `the library is asked at most once per poll interval, and usage is reported once`() {
        var seen = 0
        val lib = FakeOmtLibrary().apply { connections = 1 }
        val r = renderer(lib, onReceiverSeen = { seen++ }, openSender = true)
        assertEquals(1, r.refreshReceivers(nowMs = 1_000))
        assertEquals(1, r.refreshReceivers(nowMs = 1_000 + OmtVideoRenderer.RECEIVER_POLL_MS - 1))
        assertEquals(1, lib.connectionQueries, "the count between polls is the one already known")
        lib.connections = 2
        assertEquals(2, r.refreshReceivers(nowMs = 1_000 + OmtVideoRenderer.RECEIVER_POLL_MS))
        assertEquals(1, seen, "a receiver tuning in is reported once, not once per poll")
    }

    @Test
    fun `a source nobody is watching reports no usage`() {
        var seen = 0
        val r = renderer(FakeOmtLibrary(), receivers = 0, onReceiverSeen = { seen++ }, openSender = true)
        r.refreshReceivers(nowMs = 0)
        r.refreshReceivers(nowMs = OmtVideoRenderer.RECEIVER_POLL_MS)
        assertEquals(0, seen)
    }

    @Test
    fun `sending needs the switch, the sender and a receiver`() {
        assertTrue(OmtVideoRenderer.shouldSend(enabled = true, senderOpen = true, receivers = 1))
        assertFalse(OmtVideoRenderer.shouldSend(enabled = false, senderOpen = true, receivers = 1))
        assertFalse(OmtVideoRenderer.shouldSend(enabled = true, senderOpen = false, receivers = 1))
        assertFalse(OmtVideoRenderer.shouldSend(enabled = true, senderOpen = true, receivers = 0))
    }

    @Test
    fun `every mode and quality round-trips through its stored name`() {
        for (mode in OmtOutputMode.entries) {
            val stored = OmtVideoRenderer.storedModeOf(mode)
            assertEquals(mode, OmtVideoRenderer.modeOf(ScreenAssignment(omtMode = stored)))
        }
        for (quality in OmtQuality.entries) {
            val stored = OmtVideoRenderer.storedQualityOf(quality)
            assertEquals(quality, OmtVideoRenderer.qualityOf(ScreenAssignment(omtQuality = stored)))
        }
    }

    @Test
    fun `a new output is alpha at automatic quality, and unknown stored values fall back to those`() {
        assertEquals(OmtOutputMode.ALPHA, OmtVideoRenderer.modeOf(ScreenAssignment()))
        assertEquals(OmtQuality.DEFAULT, OmtVideoRenderer.qualityOf(ScreenAssignment()))
        assertEquals(OmtOutputMode.ALPHA, OmtVideoRenderer.modeOf(ScreenAssignment(omtMode = "fill_key")))
        assertEquals(OmtQuality.DEFAULT, OmtVideoRenderer.qualityOf(ScreenAssignment(omtQuality = "ultra")))
        assertEquals(Constants.OMT_MODE_ALPHA, ScreenAssignment().omtMode)
    }
}
