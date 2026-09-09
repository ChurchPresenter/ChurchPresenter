package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Re-finding a saved AVFoundation camera when the index it was saved at no longer holds it.
 *
 * AVFoundation addresses a device by its position in one listing that also contains virtual
 * cameras and the machine's own displays. Virtual cameras register when their host app starts, so
 * that listing is a different length at different moments — a real reporter's Mac was seen
 * alternating between five and six entries minutes apart. A stored index is therefore a snapshot,
 * and the device at it can become a *different camera* or `Capture screen 0`, whose picture is the
 * operator's display: opening that is a screen recording, which is why macOS asked that reporter
 * for Screen Recording permission on every launch (issue #478).
 *
 * The fixtures below are that machine's real listing, from the reporter's own terminal output in
 * issue #431, and are driven through the production parser rather than hand-built device lists.
 */
class AvfoundationIndexDriftTest {

    /** The reporter's Mac with everything running: four video devices, then the display. */
    private val fullListing = """
        [AVFoundation indev @ 0x7f8b1bf052c0] AVFoundation video devices:
        [AVFoundation indev @ 0x7f8b1bf052c0] [0] Meld Studio Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [1] OBS Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [2] USB3 Video
        [AVFoundation indev @ 0x7f8b1bf052c0] [3] NDI Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [4] Capture screen 0
        [AVFoundation indev @ 0x7f8b1bf052c0] AVFoundation audio devices:
        [AVFoundation indev @ 0x7f8b1bf052c0] [0] MacBook Pro Microphone
    """.trimIndent()

    /**
     * The same Mac at login, before OBS and Meld have started. The capture card is at 0 now, and
     * index 2 — where it used to be — is the display.
     */
    private val coldListing = """
        [AVFoundation indev @ 0x7f8b1bf052c0] AVFoundation video devices:
        [AVFoundation indev @ 0x7f8b1bf052c0] [0] USB3 Video
        [AVFoundation indev @ 0x7f8b1bf052c0] [1] NDI Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [2] Capture screen 0
        [AVFoundation indev @ 0x7f8b1bf052c0] AVFoundation audio devices:
        [AVFoundation indev @ 0x7f8b1bf052c0] [0] MacBook Pro Microphone
    """.trimIndent()

    /** The card unplugged, so nothing but the virtual cameras and the display are left. */
    private val unpluggedListing = """
        [AVFoundation indev @ 0x7f8b1bf052c0] AVFoundation video devices:
        [AVFoundation indev @ 0x7f8b1bf052c0] [0] Meld Studio Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [1] OBS Virtual Camera
        [AVFoundation indev @ 0x7f8b1bf052c0] [2] Capture screen 0
    """.trimIndent()

    private fun devices(ffmpegOutput: String) = parseMacCameras("", ffmpegOutput)

    /** The capture card as the operator saved it, back when it was at index 2. */
    private fun savedCard(path: String = "avfoundation://2") = SceneSource.CameraSource(
        id = "layer-1", name = "Camera", devicePath = path, deviceName = "USB3 Video",
    )

    @Test
    fun `the listing puts the display in the same index space as the cameras`() {
        val parsed = devices(fullListing)
        assertEquals(5, parsed.size, "the display is a video device to AVFoundation, so it is listed")
        assertEquals("avfoundation://4", parsed.last().path)
        assertTrue(isScreenCaptureDevice(parsed.last().name))
        assertFalse(isScreenCaptureDevice(parsed[2].name))
    }

    @Test
    fun `an index that still holds the saved device is confirmed`() {
        assertEquals(
            AvfResolution.At("avfoundation://2"),
            resolveAvfoundationDevice(savedCard(), devices(fullListing)),
        )
    }

    @Test
    fun `an index that has drifted is corrected to where the device is now`() {
        assertEquals(
            AvfResolution.At("avfoundation://0"),
            resolveAvfoundationDevice(savedCard(), devices(coldListing)),
            "the card is at 0 before the virtual cameras register; opening 2 would grab the screen",
        )
    }

    @Test
    fun `an index now holding the display is refused rather than opened`() {
        assertEquals(AvfResolution.Gone, resolveAvfoundationDevice(savedCard(), devices(unpluggedListing)))
        assertEquals("Capture screen 0", avfDeviceNameAt(savedCard(), devices(unpluggedListing)))
    }

    @Test
    fun `an index now holding a different camera is refused just as firmly`() {
        val saved = savedCard(path = "avfoundation://1")
        assertEquals(AvfResolution.Gone, resolveAvfoundationDevice(saved, devices(unpluggedListing)))
        assertEquals(
            "OBS Virtual Camera",
            avfDeviceNameAt(saved, devices(unpluggedListing)),
            "the wrong picture behind the lyrics is a worse failure than a black screen",
        )
    }

    @Test
    fun `an index past the end of the listing names nothing`() {
        val saved = savedCard(path = "avfoundation://9")
        assertEquals(AvfResolution.Gone, resolveAvfoundationDevice(saved, devices(unpluggedListing)))
        assertEquals("", avfDeviceNameAt(saved, devices(unpluggedListing)))
    }

    @Test
    fun `nothing is resolved before an enumeration has run`() {
        assertEquals(AvfResolution.NotApplicable, resolveAvfoundationDevice(savedCard(), null))
        assertTrue(needsAvfResolution(savedCard()), "but the caller is told to go and enumerate")
    }

    @Test
    fun `an enumeration that listed nothing is not evidence the device is gone`() {
        assertEquals(
            AvfResolution.NotApplicable,
            resolveAvfoundationDevice(savedCard(), emptyList()),
            "a listing we could not read means we do not know, and blacking out a working camera " +
                "on no evidence is a worse trade than the rare stale index this guards against",
        )
    }

    @Test
    fun `a device saved without a name has nothing to match on`() {
        val nameless = SceneSource.CameraSource(
            id = "layer-1", name = "Camera", devicePath = "avfoundation://2", deviceName = "",
        )
        assertFalse(needsAvfResolution(nameless))
        assertEquals(AvfResolution.NotApplicable, resolveAvfoundationDevice(nameless, devices(fullListing)))
    }

    @Test
    fun `devices in other index spaces are left alone`() {
        val windows = SceneSource.CameraSource(
            id = "layer-1", name = "Camera",
            devicePath = "dshow://:dshow-vdev=USB3 Video", deviceName = "USB3 Video",
        )
        val card = SceneSource.CameraSource(
            id = "layer-1", name = "Camera", devicePath = "decklink://1",
            deviceName = "DeckLink Mini Recorder", isDeckLink = true, deckLinkIndex = 1,
        )
        assertFalse(needsAvfResolution(windows))
        assertFalse(needsAvfResolution(card))
        assertEquals(AvfResolution.NotApplicable, resolveAvfoundationDevice(windows, devices(fullListing)))
        assertEquals(AvfResolution.NotApplicable, resolveAvfoundationDevice(card, devices(fullListing)))
    }
}
