package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.core.models.camera.CameraDeviceRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The two decisions the background camera picker takes once a machine reports hardware — driven as
 * functions, because no fixture can plug a capture card in.
 */
class BackgroundCameraPickerModelTest {

    private val webcam = CameraDevice("FaceTime HD Camera", "avfoundation://0", "FaceTime HD Camera")
    private val virtual = CameraDevice("OBS Virtual Camera", "avfoundation://1", "OBS Virtual Camera")
    private val card = CameraDevice(
        "DeckLink Mini", "decklink://0", "DeckLink: DeckLink Mini", isDeckLink = true, deckLinkIndex = 0,
    )
    private val card2 = CameraDevice(
        "DeckLink Duo", "decklink://1", "DeckLink: DeckLink Duo", isDeckLink = true, deckLinkIndex = 1,
    )
    private val all = listOf(card, card2, webcam, virtual)

    // ── Which device the dropdown names ───────────────────────────────────────

    @Test
    fun `an ordinary camera is matched on its stored path`() {
        val ref = CameraDeviceRef(devicePath = "avfoundation://1", deviceName = "OBS Virtual Camera")
        assertEquals("OBS Virtual Camera", selectedBackgroundCameraName(all, ref))
    }

    @Test
    fun `a card is matched on its slot rather than its path`() {
        val ref = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 1, devicePath = "decklink://9")
        assertEquals("DeckLink: DeckLink Duo", selectedBackgroundCameraName(all, ref))
    }

    @Test
    fun `a card that is no longer fitted falls back to the first device`() {
        val ref = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 4)
        assertEquals(card.displayName, selectedBackgroundCameraName(all, ref))
    }

    @Test
    fun `a path this machine does not have is shown as itself`() {
        val ref = CameraDeviceRef(devicePath = "v4l2:///dev/video7", deviceName = "Booth camera")
        assertEquals(
            "v4l2:///dev/video7",
            selectedBackgroundCameraName(all, ref),
            "an operator must see that the stored device is missing, not a different one",
        )
    }

    @Test
    fun `a background with no camera yet falls back to the first device`() {
        assertEquals(card.displayName, selectedBackgroundCameraName(all, CameraDeviceRef()))
    }

    @Test
    fun `an ordinary path is never matched against a card`() {
        val ref = CameraDeviceRef(devicePath = "decklink://0")
        assertEquals(
            "decklink://0",
            selectedBackgroundCameraName(listOf(card, webcam), ref),
            "a ref that does not say DeckLink must not be matched to one",
        )
    }

    @Test
    fun `a card is matched even when it is not the only one`() {
        val ref = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 0)
        assertEquals("DeckLink: DeckLink Mini", selectedBackgroundCameraName(all, ref))
    }

    // ── Pointing a background at a device ─────────────────────────────────────

    @Test
    fun `choosing a device stores its path and its name`() {
        val chosen = cameraRefOn(CameraDeviceRef(), webcam)
        assertEquals("avfoundation://0", chosen.devicePath)
        assertEquals("FaceTime HD Camera", chosen.deviceName)
    }

    @Test
    fun `choosing a device makes the reference set`() {
        assertFalse(CameraDeviceRef().isSet)
        assertTrue(cameraRefOn(CameraDeviceRef(), webcam).isSet)
    }

    @Test
    fun `a format enumerated from another device is not carried over`() {
        val was = CameraDeviceRef(devicePath = "avfoundation://1", videoFormat = "1920x1080@30")
        assertEquals("", cameraRefOn(was, webcam).videoFormat)
    }

    @Test
    fun `a connection chosen on another card is not carried over`() {
        val was = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 0, videoConnection = 4)
        assertEquals(0, cameraRefOn(was, card2).videoConnection)
    }

    @Test
    fun `choosing a card records its slot`() {
        val chosen = cameraRefOn(CameraDeviceRef(), card2)
        assertTrue(chosen.isDeckLink)
        assertEquals(1, chosen.deckLinkIndex)
    }

    @Test
    fun `moving from a card to a webcam stops it being a card`() {
        val was = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 1, devicePath = "decklink://1")
        val now = cameraRefOn(was, webcam)
        assertFalse(now.isDeckLink)
        assertEquals(-1, now.deckLinkIndex)
    }

    @Test
    fun `the stored reference names the device the dropdown then shows`() {
        val chosen = cameraRefOn(CameraDeviceRef(), virtual)
        assertEquals(
            virtual.displayName,
            selectedBackgroundCameraName(all, chosen),
            "what is stored must be what the picker reads back",
        )
    }
}
