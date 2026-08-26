package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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
private const val WAIT_MS = 4_000L

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

    private fun waitFor(what: String, condition: () -> Boolean) = runBlocking {
        val deadline = System.nanoTime() + WAIT_MS * 1_000_000
        while (!condition()) {
            if (System.nanoTime() > deadline) throw AssertionError("timed out waiting for $what")
            yield()
        }
    }

    private fun renderer(
        lib: FakeNdiLibrary,
        mode: NdiOutputMode = NdiOutputMode.ALPHA,
        enabled: Boolean = true,
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
        return NdiVideoRenderer(sender, context, assignment, width = W, height = H, fps = 60) to sender
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
}
