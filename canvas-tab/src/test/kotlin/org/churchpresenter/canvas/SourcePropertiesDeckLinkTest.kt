@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The capture-card half of the camera editor.
 *
 * A DeckLink source is configured differently from a webcam: it has a physical input to pick (SDI,
 * HDMI) and a video mode, both read off the card. None of that could be reached before — the editor
 * asked a hardware singleton directly, and a test machine has no card. `CanvasDeckLink` is a
 * composition local now, so the whole branch is drivable.
 *
 * The warning is the part that matters most: a card already sending a program feed usually cannot
 * also capture, and an operator who picks it anyway gets a black source with no explanation.
 */
class SourcePropertiesDeckLinkTest {

    private class Card(
        private val connections: List<CanvasDeckLink.VideoConnection> = emptyList(),
        private val modes: List<CanvasDeckLink.InputMode> = emptyList(),
        private val outputActive: Boolean = false,
        private val devices: List<CanvasDeckLink.Device> = emptyList(),
    ) : CanvasDeckLink {
        override fun isAvailable() = true
        override fun listDevices() = devices
        override fun isOutputActive(deviceIndex: Int) = outputActive
        override fun listInputModes(deviceIndex: Int) = modes
        override fun listVideoConnections(deviceIndex: Int) = connections
        override fun openInput(deviceIndex: Int, mode: String, connection: Int) = true
        override fun getInputFrame(deviceIndex: Int): IntArray? = null
        override fun closeInput(deviceIndex: Int) = Unit
    }

    private fun deckLinkSource(connection: Int = 0, format: String = "") =
        SceneSource.CameraSource(
            id = "cam", name = "Camera",
            devicePath = "decklink://0", deviceName = "DeckLink Mini",
            isDeckLink = true, deckLinkIndex = 0,
            videoConnection = connection, videoFormat = format,
        )

    private val sdi = CanvasDeckLink.VideoConnection("SDI", 1)
    private val hdmi = CanvasDeckLink.VideoConnection("HDMI", 2)
    private val hd = CanvasDeckLink.InputMode("1080p30", "Hp30")
    private val uhd = CanvasDeckLink.InputMode("2160p30", "4Kp30")

    // ── The output-in-use warning ───────────────────────────────────────────────

    @Test
    fun `a card already sending output warns that capture may not work`() {
        sourcePanel(deckLinkSource(), deckLink = Card(outputActive = true)) { _ ->
            assertTrue(
                countOf("This device is currently used for output.") > 0 ||
                    onAllNodesWithTextContaining("currently used for output").isNotEmpty(),
                "the operator gets no explanation for a black source otherwise",
            )
        }
    }

    @Test
    fun `a card that is not sending output shows no warning`() {
        sourcePanel(deckLinkSource(), deckLink = Card(outputActive = false)) { _ ->
            assertTrue(onAllNodesWithTextContaining("currently used for output").isEmpty())
        }
    }

    // ── Video connection ────────────────────────────────────────────────────────

    @Test
    fun `the card's physical inputs are offered`() {
        sourcePanel(deckLinkSource(), deckLink = Card(connections = listOf(sdi, hdmi))) { _ ->
            openDropdown("SDI")

            assertTrue(onAllNodesWithTextContaining("SDI").isNotEmpty())
            assertTrue(onAllNodesWithTextContaining("HDMI").isNotEmpty())
        }
    }

    @Test
    fun `picking an input stores the driver's own value, not its name`() {
        sourcePanel(deckLinkSource(), deckLink = Card(connections = listOf(sdi, hdmi))) { get ->
            openDropdown("SDI")
            onAllNodesWithText("HDMI").onLast().performClick()
            waitForIdle()

            // The name is for the operator; the number is what the driver is handed back.
            assertEquals(2, (get() as SceneSource.CameraSource).videoConnection)
        }
    }

    @Test
    fun `a card offering no inputs draws no connection selector`() {
        sourcePanel(deckLinkSource(), deckLink = Card(connections = emptyList())) { _ ->
            // Nothing to choose between, so the row is omitted rather than shown empty.
            assertEquals(0, countOf("Video Connection"))
        }
    }

    // ── Video mode ──────────────────────────────────────────────────────────────

    @Test
    fun `the card's capture modes are offered alongside Auto`() {
        sourcePanel(deckLinkSource(), deckLink = Card(modes = listOf(hd, uhd))) { _ ->
            openDropdown("Auto")

            assertTrue(onAllNodesWithTextContaining("Auto").isNotEmpty(), "detect-the-signal is the default")
            assertTrue(onAllNodesWithTextContaining("1080p30").isNotEmpty())
        }
    }

    @Test
    fun `picking a mode stores its encoded value`() {
        sourcePanel(deckLinkSource(), deckLink = Card(modes = listOf(hd, uhd))) { get ->
            openDropdown("Auto")
            onAllNodesWithText("2160p30").onLast().performClick()
            waitForIdle()

            assertEquals("4Kp30", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    @Test
    fun `choosing Auto clears the stored mode so the card detects the signal`() {
        sourcePanel(deckLinkSource(format = "Hp30"), deckLink = Card(modes = listOf(hd))) { get ->
            openDropdown("1080p30")
            onAllNodesWithText("Auto").onLast().performClick()
            waitForIdle()

            assertEquals("", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    // ── The device list ─────────────────────────────────────────────────────────

    @Test
    fun `an attached card appears in the camera list under its own name`() {
        val card = Card(devices = listOf(CanvasDeckLink.Device(0, "Mini Recorder")))
        sourcePanel(deckLinkSource(), deckLink = card) { _ ->
            button("Refresh Cameras").performClick()
            waitForIdle()

            // The enumeration prefixes it, so the operator can tell a card from a webcam.
            assertTrue(onAllNodesWithTextContaining("Mini Recorder").isNotEmpty())
        }
    }
}
