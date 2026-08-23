package org.churchpresenter.companionserver

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import org.churchpresenter.settings.ScreenAssignment

import java.nio.ByteBuffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class BrowserSourceHubTest {

    private fun hub() = BrowserSourceHub(
        CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
        MutableStateFlow("")
    )

    private fun frame(
        x: Int = 0, y: Int = 0, w: Int = 0, h: Int = 0,
        fullW: Int = 0, fullH: Int = 0, png: ByteArray = ByteArray(0),
    ) = BrowserSourceFrame(x, y, w, h, fullW, fullH, png)

    @Test
    fun `outputs are addressable by index once published`() {
        val h = hub()
        val a = ScreenAssignment(browserSourceEnabled = true)
        val b = ScreenAssignment(browserSourceEnabled = false)
        h.updateBrowserSourceOutputs(listOf(a, b))

        assertEquals(a, h.browserSourceOutput(0))
        assertEquals(b, h.browserSourceOutput(1))
    }

    @Test
    fun `an index outside the configured outputs yields nothing`() {
        val h = hub()
        assertNull(h.browserSourceOutput(0))
        h.updateBrowserSourceOutputs(listOf(ScreenAssignment()))
        assertNull(h.browserSourceOutput(1))
        assertNull(h.browserSourceOutput(-1))
    }

    @Test
    fun `republishing replaces the previous outputs`() {
        val h = hub()
        h.updateBrowserSourceOutputs(listOf(ScreenAssignment(), ScreenAssignment()))
        h.updateBrowserSourceOutputs(listOf(ScreenAssignment()))
        assertNull(h.browserSourceOutput(1))
    }

    @Test
    fun `a registered frame flow is held for its output`() {
        val h = hub()
        val flow = MutableSharedFlow<BrowserSourceFrame>()
        h.registerBrowserSourceFrames(2, flow)
        assertSame(flow, h._browserSourceFrameFlows[2])
    }

    @Test
    fun `re-registering the same flow leaves it in place`() {
        val h = hub()
        val flow = MutableSharedFlow<BrowserSourceFrame>()
        h.registerBrowserSourceFrames(0, flow)
        h.registerBrowserSourceFrames(0, flow)
        assertSame(flow, h._browserSourceFrameFlows[0])
    }

    @Test
    fun `a replacement flow takes over and strands no sessions behind it`() {
        // A renderer restart (resolution/fps change) publishes a new flow; the old one never emits
        // again, so its sessions must be dropped rather than left waiting on a dead stream.
        val h = hub()
        h.registerBrowserSourceFrames(0, MutableSharedFlow())
        h._browserSourceSessions[0] = mutableSetOf()
        val replacement = MutableSharedFlow<BrowserSourceFrame>()
        h.registerBrowserSourceFrames(0, replacement)

        assertSame(replacement, h._browserSourceFrameFlows[0])
        assertNull(h._browserSourceSessions[0])
    }

    @Test
    fun `a frame is encoded as six ints of geometry followed by its png`() {
        val png = byteArrayOf(9, 8, 7)
        val bytes = hub().encodeBrowserSourceFrameMessage(
            frame(x = 1, y = 2, w = 3, h = 4, fullW = 5, fullH = 6, png = png)
        )
        assertEquals(24 + png.size, bytes.size)
        val buf = ByteBuffer.wrap(bytes)
        assertEquals(1, buf.int)
        assertEquals(2, buf.int)
        assertEquals(3, buf.int)
        assertEquals(4, buf.int)
        assertEquals(5, buf.int)
        assertEquals(6, buf.int)
        val rest = ByteArray(png.size).also { buf.get(it) }
        assertTrue(png.contentEquals(rest))
    }

    @Test
    fun `an empty frame still carries its geometry header`() {
        val bytes = hub().encodeBrowserSourceFrameMessage(frame(fullW = 1920, fullH = 1080))
        assertEquals(24, bytes.size)
        val buf = ByteBuffer.wrap(bytes)
        repeat(4) { buf.int }
        assertEquals(1920, buf.int)
        assertEquals(1080, buf.int)
    }

    @Test
    fun `a full-frame update encodes the whole surface as its rectangle`() {
        val bytes = hub().encodeBrowserSourceFrameMessage(
            frame(w = 1920, h = 1080, fullW = 1920, fullH = 1080, png = ByteArray(10))
        )
        val buf = ByteBuffer.wrap(bytes)
        assertEquals(0, buf.int)
        assertEquals(0, buf.int)
        assertEquals(1920, buf.int)
        assertEquals(1080, buf.int)
    }
}
