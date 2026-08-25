@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.ui.WindowInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Picking a camera, and picking the format it captures in.
 *
 * Choosing a different device has to clear the format and connection stored with the old one — a
 * `1920x1080@60` left over from a card is a mode a webcam does not have, and ffmpeg refuses to open
 * with it. The source then shows nothing for a reason nothing on screen explains.
 *
 * The device list comes from a `CanvasDeckLink` and the format list from `ffmpeg -list_options`,
 * so neither existed on a test machine. Both are behind composition locals now.
 */
class SourcePropertiesCameraPickerTest {

    private class Card(private val devices: List<CanvasDeckLink.Device>) : CanvasDeckLink {
        override fun isAvailable() = devices.isNotEmpty()
        override fun listDevices() = devices
        override fun isOutputActive(deviceIndex: Int) = false
        override fun listInputModes(deviceIndex: Int) = emptyList<CanvasDeckLink.InputMode>()
        override fun listVideoConnections(deviceIndex: Int) = emptyList<CanvasDeckLink.VideoConnection>()
        override fun openInput(deviceIndex: Int, mode: String, connection: Int) = true
        override fun getInputFrame(deviceIndex: Int): IntArray? = null
        override fun closeInput(deviceIndex: Int) = Unit
    }

    private class Formats(private val formats: List<CameraFormat>) : CanvasDeviceListing {
        val askedAbout = mutableListOf<String>()
        override fun openWindows() = emptyList<WindowInfo>()
        override fun cameraFormats(devicePath: String, deviceName: String): List<CameraFormat> {
            askedAbout += devicePath
            return formats
        }
    }

    private val hd = CameraFormat(1280, 720, 30)
    private val fullHd = CameraFormat(1920, 1080, 60)

    private fun webcam(path: String = "v4l2:///dev/video0", format: String = "") =
        SceneSource.CameraSource(
            id = "cam", name = "Camera", devicePath = path, deviceName = "USB Cam", videoFormat = format,
        )

    private fun twoCards() = Card(listOf(
        CanvasDeckLink.Device(index = 0, name = "Mini Recorder"),
        CanvasDeckLink.Device(index = 1, name = "Duo 2"),
    ))

    // ── Choosing a device ──────────────────────────────────────────────────────

    @Test
    fun `every attached device is offered`() {
        val source = SceneSource.CameraSource(
            id = "cam", name = "Camera", isDeckLink = true, deckLinkIndex = 0,
            devicePath = "decklink://0", deviceName = "Mini Recorder",
        )
        sourcePanel(source, deckLink = twoCards()) { _ ->
            openDropdown("DeckLink: Mini Recorder")

            onNodeWithText("DeckLink: Duo 2", substring = true).assertExists()
        }
    }

    @Test
    fun `choosing a different device stores its path and clears the old format`() {
        val onCardZero = SceneSource.CameraSource(
            id = "cam", name = "Camera", isDeckLink = true, deckLinkIndex = 0,
            devicePath = "decklink://0", deviceName = "Mini Recorder",
            videoFormat = "Hp30", videoConnection = 2,
        )

        sourcePanel(onCardZero, deckLink = twoCards()) { get ->
            chooseFromDropdown("DeckLink: Mini Recorder", "DeckLink: Duo 2")

            val stored = get() as SceneSource.CameraSource
            assertEquals("decklink://1", stored.devicePath)
            assertEquals("Duo 2", stored.deviceName)
            assertEquals(1, stored.deckLinkIndex)
            assertEquals("", stored.videoFormat, "the old card's mode is not a mode this one has")
            assertEquals(0, stored.videoConnection, "nor is its input")
        }
    }

    // ── Choosing a format ──────────────────────────────────────────────────────

    @Test
    fun `a webcam's formats are offered alongside Auto`() {
        sourcePanel(webcam(), deckLink = twoCards(), listing = Formats(listOf(hd, fullHd))) { _ ->
            openDropdown("Auto (default)")

            onNodeWithText("1280x720 @ 30fps", substring = true).assertExists()
        }
    }

    @Test
    fun `the formats are asked for by the device path, not the device name`() {
        val formats = Formats(listOf(hd))

        sourcePanel(webcam(path = "avfoundation://0"), deckLink = twoCards(), listing = formats) { _ ->
            assertTrue(formats.askedAbout.contains("avfoundation://0"))
        }
    }

    @Test
    fun `picking a format stores its encoded value, not the words shown`() {
        sourcePanel(webcam(), deckLink = twoCards(), listing = Formats(listOf(hd, fullHd))) { get ->
            chooseFromDropdown("Auto (default)", "1920x1080 @ 60fps")

            assertEquals("1920x1080@60", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    @Test
    fun `choosing Auto clears the format so the device picks for itself`() {
        sourcePanel(webcam(format = "1280x720@30"), deckLink = twoCards(), listing = Formats(listOf(hd, fullHd))) { get ->
            chooseFromDropdown("1280x720 @ 30fps", "Auto (default)")

            assertEquals("", (get() as SceneSource.CameraSource).videoFormat)
        }
    }

    @Test
    fun `a stored format the device no longer offers shows as Auto`() {
        // A camera swapped for a different model keeps the old source's format string. Showing it
        // would offer the operator a mode that is not in the list and cannot be selected again.
        sourcePanel(webcam(format = "3840x2160@60"), deckLink = twoCards(), listing = Formats(listOf(hd))) { _ ->
            assertTrue(countOf("Auto (default)") > 0)
        }
    }

    @Test
    fun `a camera offering no formats still offers Auto`() {
        sourcePanel(webcam(), deckLink = twoCards(), listing = Formats(emptyList())) { _ ->
            assertTrue(countOf("Auto (default)") > 0, "there must always be something to select")
        }
    }
}
