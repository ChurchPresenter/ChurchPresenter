package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The decisions the camera panel makes once the machine *does* report hardware.
 *
 * `SourcePropertiesCameraTest` covers the panel on a machine with nothing attached, which is the only
 * state a rendered test can arrange — what a camera dropdown contains is the operator's hardware, and
 * no fixture can plug a capture card in. These are the same panel's decisions, taken as functions and
 * driven directly with device lists that cannot otherwise exist here.
 *
 * They are worth pinning individually because each is about a source that was configured *somewhere
 * else*: a scene file saved on the machine in the booth and opened on a laptop, or a card moved
 * between slots. What the panel shows then — the stored device, a real one, or "Auto" — is the
 * difference between an operator seeing that their camera is missing and being told everything is
 * fine while the wrong input goes live.
 */
class SourcePropertiesCameraSelectionTest {

    private fun camera(
        devicePath: String = "",
        deviceName: String = "",
        isDeckLink: Boolean = false,
        deckLinkIndex: Int = -1,
        videoFormat: String = "",
        videoConnection: Int = 0,
    ) = SceneSource.CameraSource(
        id = "cam", name = "Cam", devicePath = devicePath, deviceName = deviceName,
        videoFormat = videoFormat, videoConnection = videoConnection,
        isDeckLink = isDeckLink, deckLinkIndex = deckLinkIndex,
    )

    private val webcam = CameraDevice("FaceTime HD Camera", "avfoundation://0", "FaceTime HD Camera")
    private val virtual = CameraDevice("OBS Virtual Camera", "avfoundation://1", "OBS Virtual Camera")
    private val card = CameraDevice(
        "DeckLink Mini",
        "decklink://0",
        "DeckLink: DeckLink Mini",
        isDeckLink = true,
        deckLinkIndex = 0,
    )
    private val card2 = CameraDevice(
        "DeckLink Duo",
        "decklink://1",
        "DeckLink: DeckLink Duo",
        isDeckLink = true,
        deckLinkIndex = 1,
    )
    private val all = listOf(card, card2, webcam, virtual)

    // ── Which device the dropdown names ───────────────────────────────────────

    @Test
    fun `an ordinary camera is matched on its stored path`() {
        assertEquals(
            "OBS Virtual Camera",
            selectedCameraName(all, camera(devicePath = "avfoundation://1")),
            "the path is what identifies the device, not its position in the list",
        )
    }

    @Test
    fun `a DeckLink card is matched on its index`() {
        assertEquals(
            "DeckLink: DeckLink Duo",
            selectedCameraName(all, camera(isDeckLink = true, deckLinkIndex = 1)),
        )
    }

    @Test
    fun `a DeckLink source ignores an ordinary camera at the same path`() {
        // Both a card and a webcam could in principle answer to a path; the flag is what decides.
        assertEquals(
            "DeckLink: DeckLink Mini",
            selectedCameraName(listOf(webcam, card), camera(isDeckLink = true, deckLinkIndex = 0)),
        )
    }

    @Test
    fun `an ordinary source ignores a DeckLink card at the same path`() {
        assertEquals(
            "FaceTime HD Camera",
            selectedCameraName(
                listOf(card, webcam),
                camera(devicePath = "avfoundation://0"),
            ),
        )
    }

    @Test
    fun `a camera that is not attached is named by its stored path, not silently replaced`() {
        assertEquals(
            "avfoundation://7",
            selectedCameraName(all, camera(devicePath = "avfoundation://7")),
            "an operator must be able to see that the configured camera is missing",
        )
    }

    @Test
    fun `a source with no device at all falls back to the first camera offered`() {
        assertEquals("DeckLink: DeckLink Mini", selectedCameraName(all, camera()))
    }

    @Test
    fun `a DeckLink card that is not present falls back to the first camera offered`() {
        assertEquals(
            "DeckLink: DeckLink Mini",
            selectedCameraName(all, camera(isDeckLink = true, deckLinkIndex = 9)),
            "there is no stored path to show for a card, so the list's first entry is all there is",
        )
    }

    // ── What choosing a device writes ─────────────────────────────────────────

    @Test
    fun `choosing an ordinary camera stores its path and name`() {
        val updated = cameraSourceOn(camera(), webcam)

        assertEquals("avfoundation://0", updated.devicePath)
        assertEquals("FaceTime HD Camera", updated.deviceName)
        assertEquals(false, updated.isDeckLink)
        assertEquals(-1, updated.deckLinkIndex, "an ordinary camera has no card index")
    }

    @Test
    fun `choosing a DeckLink card stores its index and flags it as one`() {
        val updated = cameraSourceOn(camera(), card2)

        assertEquals(true, updated.isDeckLink)
        assertEquals(1, updated.deckLinkIndex)
        assertEquals("decklink://1", updated.devicePath)
    }

    @Test
    fun `switching device clears the previous device's format and connection`() {
        val configured = camera(
            devicePath = "avfoundation://0", videoFormat = "1920x1080@60", videoConnection = 4,
        )

        val updated = cameraSourceOn(configured, virtual)

        assertEquals("", updated.videoFormat, "a resolution one camera offers another may not")
        assertEquals(0, updated.videoConnection, "and an SDI input means nothing on a webcam")
    }

    @Test
    fun `switching device leaves everything that is not about the device alone`() {
        val configured = camera(devicePath = "avfoundation://0").copy(
            name = "Stage Left", visible = false, locked = true,
        )

        val updated = cameraSourceOn(configured, virtual)

        assertEquals("Stage Left", updated.name)
        assertEquals(false, updated.visible)
        assertEquals(true, updated.locked)
        assertEquals(configured.transform, updated.transform)
    }

    // ── DeckLink video connection ─────────────────────────────────────────────

    private val sdi = DeckLinkManager.VideoConnection("SDI", 1)
    private val hdmi = DeckLinkManager.VideoConnection("HDMI", 2)

    @Test
    fun `the stored connection is the one named`() {
        assertEquals("HDMI", selectedConnectionName(listOf(sdi, hdmi), videoConnection = 2))
    }

    @Test
    fun `a connection this card does not offer falls back to its first`() {
        assertEquals(
            "SDI", selectedConnectionName(listOf(sdi, hdmi), videoConnection = 99),
            "a connection saved against other hardware must not be named as if it were live",
        )
    }

    @Test
    fun `an unset connection falls back to the card's first`() {
        assertEquals("SDI", selectedConnectionName(listOf(sdi, hdmi), videoConnection = 0))
    }

    // ── DeckLink input mode ───────────────────────────────────────────────────

    private val mode1080 = DeckLinkManager.InputMode("1080p59.94", "Hp59")
    private val mode720 = DeckLinkManager.InputMode("720p60", "hp60")

    @Test
    fun `no stored mode reads as Auto`() {
        assertEquals("Auto", selectedModeName(listOf(mode1080), videoFormat = "", autoLabel = "Auto"))
    }

    @Test
    fun `a stored mode is named`() {
        assertEquals(
            "1080p59.94",
            selectedModeName(listOf(mode1080, mode720), videoFormat = "Hp59", autoLabel = "Auto"),
        )
    }

    @Test
    fun `a mode this card does not report reads as Auto`() {
        assertEquals(
            "Auto",
            selectedModeName(listOf(mode720), videoFormat = "Hp59", autoLabel = "Auto"),
            "the dropdown must not name a mode the card cannot be set to",
        )
    }

    @Test
    fun `a mode is looked up while the card is still reporting nothing`() {
        assertEquals(
            "Auto", selectedModeName(emptyList(), videoFormat = "Hp59", autoLabel = "Auto"),
            "modes load asynchronously — until they arrive the dropdown reads Auto, not blank",
        )
    }

    // ── Camera format ─────────────────────────────────────────────────────────

    private val hd = CameraFormat(1920, 1080, 30)
    private val vga = CameraFormat(640, 480, 30)

    @Test
    fun `no stored format reads as Auto`() {
        assertEquals("Auto", selectedFormatName(listOf(hd), videoFormat = "", autoLabel = "Auto"))
    }

    @Test
    fun `a stored format is named as the operator would read it`() {
        assertEquals(
            "1920x1080 @ 30fps",
            selectedFormatName(listOf(hd, vga), videoFormat = "1920x1080@30", autoLabel = "Auto"),
        )
    }

    @Test
    fun `a format this camera does not offer reads as Auto`() {
        assertEquals(
            "Auto",
            selectedFormatName(listOf(vga), videoFormat = "1920x1080@30", autoLabel = "Auto"),
        )
    }

    @Test
    fun `a format is looked up while the camera is still being enumerated`() {
        assertEquals("Auto", selectedFormatName(emptyList(), videoFormat = "1920x1080@30", autoLabel = "Auto"))
    }

    // ── Which enumerator a device path selects ────────────────────────────────

    @Test
    fun `a device path that does not match the platform enumerates nothing`() {
        // The listing is chosen by OS *and* path prefix together — a v4l2 path on Windows, or a
        // DirectShow path on Linux, belongs to a scene file written on another machine. Neither
        // combination below matches, so no tool is run at all.
        withOsName("Windows 11") {
            assertEquals(emptyList(), listCameraFormats("v4l2:///dev/video0", "Elsewhere"))
            assertEquals(emptyList(), listCameraFormats("avfoundation://0", "Elsewhere"))
        }
        withOsName("Linux") {
            assertEquals(emptyList(), listCameraFormats("dshow://:dshow-vdev=Cam", "Elsewhere"))
            assertEquals(emptyList(), listCameraFormats("avfoundation://1", "Elsewhere"))
        }
        withOsName("Mac OS X") {
            assertEquals(emptyList(), listCameraFormats("v4l2:///dev/video1", "Elsewhere"))
            assertEquals(emptyList(), listCameraFormats("dshow://:dshow-vdev=Cam2", "Elsewhere"))
        }
    }

    @Test
    fun `an unknown platform enumerates nothing whatever the path looks like`() {
        withOsName(OS_WITHOUT_ENUMERATOR) {
            assertEquals(emptyList(), listCameraFormats("v4l2:///dev/video2", "Elsewhere"))
        }
    }
}
