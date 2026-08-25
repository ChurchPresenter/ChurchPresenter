package org.churchpresenter.canvas

import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The do-nothing implementations the ports default to.
 *
 * Every port here defaults to "nothing attached", which is what a preview, a screenshot harness and
 * a test machine all are. They are three lines each and it would be easy to assume they are right —
 * but a default that threw, or that claimed a device was present, would take the whole tab down in
 * exactly the situations that are hardest to notice.
 */
class CanvasPortDefaultsTest {

    // ── CanvasFilePicker.None ───────────────────────────────────────────────────

    @Test
    fun `the default picker picks nothing`() = runBlocking {
        // Same answer as a cancelled dialog, so the caller needs no special case.
        assertNull(CanvasFilePicker.None.chooseSingle(null, emptyList(), "Choose"))
    }

    @Test
    fun `the default picker ignores whatever it is asked for`() = runBlocking {
        val filter = javax.swing.filechooser.FileNameExtensionFilter("Images", "png")
        assertNull(CanvasFilePicker.None.chooseSingle(java.nio.file.Path.of("/tmp"), listOf(filter), "Pick"))
    }

    // ── CanvasDeckLink.None ─────────────────────────────────────────────────────

    @Test
    fun `the default card reports itself absent`() {
        assertFalse(CanvasDeckLink.None.isAvailable())
    }

    @Test
    fun `the default card offers nothing to choose from`() {
        assertTrue(CanvasDeckLink.None.listDevices().isEmpty())
        assertTrue(CanvasDeckLink.None.listInputModes(0).isEmpty())
        assertTrue(CanvasDeckLink.None.listVideoConnections(0).isEmpty())
    }

    @Test
    fun `the default card never claims an output is busy`() {
        assertFalse(CanvasDeckLink.None.isOutputActive(0))
        assertFalse(CanvasDeckLink.None.isOutputActive(99))
    }

    @Test
    fun `the default card refuses to open and yields no frames`() {
        assertFalse(CanvasDeckLink.None.openInput(0, "1080p30", 1))
        assertNull(CanvasDeckLink.None.getInputFrame(0))
    }

    @Test
    fun `closing the default card is harmless`() {
        // Called on teardown whether or not anything was ever opened.
        CanvasDeckLink.None.closeInput(0)
    }

    // ── CanvasVideoSupport.Unavailable ──────────────────────────────────────────

    @Test
    fun `video defaults to unavailable and not failed`() {
        // Not the same thing: "no VLC installed" draws a different message from "VLC would not load".
        assertFalse(CanvasVideoSupport.Unavailable.available)
        assertFalse(CanvasVideoSupport.Unavailable.loadFailed)
    }

    @Test
    fun `video support carries the two states independently`() {
        assertTrue(CanvasVideoSupport(available = false, loadFailed = true).loadFailed)
        assertTrue(CanvasVideoSupport(available = true, loadFailed = false).available)
    }

    // ── CanvasDeckLink's value types ────────────────────────────────────────────

    @Test
    fun `an input mode carries the name shown and the value sent back`() {
        val mode = CanvasDeckLink.InputMode("1080p30", "Hp30")
        assertEquals("1080p30", mode.name)
        assertEquals("Hp30", mode.encodedValue)
    }

    @Test
    fun `a video connection carries the name shown and the driver's constant`() {
        val sdi = CanvasDeckLink.VideoConnection("SDI", 1)
        assertEquals("SDI", sdi.name)
        assertEquals(1, sdi.value)
    }

    @Test
    fun `a device carries its index and name`() {
        val device = CanvasDeckLink.Device(2, "Mini Recorder")
        assertEquals(2, device.index)
        assertEquals("Mini Recorder", device.name)
    }
}
