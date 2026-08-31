package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.core.models.camera.CameraDeviceRef
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Whether a stored camera may be opened on this machine.
 *
 * The rule this pins is that a **path is not an identity**. A `.song` file and a settings export
 * both travel, and `avfoundation://0` written on one machine names a camera on the next one too —
 * a different camera, pointed somewhere else in the room. Opening it would put the wrong picture
 * behind the lyrics, which is worse than showing nothing, so the match is on the name.
 */
class CameraDeviceCatalogTest {

    private val faceTime = CameraDevice(
        name = "FaceTime HD Camera", path = "avfoundation://0", displayName = "FaceTime HD Camera",
    )
    private val webcam = CameraDevice(
        name = "Logitech BRIO", path = "avfoundation://1", displayName = "Logitech BRIO",
    )
    private val card = CameraDevice(
        name = "DeckLink Mini Recorder", path = "decklink://1", displayName = "DeckLink: Mini Recorder",
        isDeckLink = true, deckLinkIndex = 1,
    )

    private fun ref(name: String, path: String) = CameraDeviceRef(devicePath = path, deviceName = name)

    @Test
    fun `a device this machine has resolves`() {
        assertTrue(cameraResolves(ref("Logitech BRIO", "avfoundation://1"), listOf(faceTime, webcam)))
    }

    @Test
    fun `a device this machine does not have does not resolve`() {
        assertFalse(cameraResolves(ref("Logitech BRIO", "avfoundation://1"), listOf(faceTime)))
    }

    /**
     * The travelling-song case, and the reason this function exists: the path is one this machine
     * happens to use, but for a different camera. It must not open.
     */
    @Test
    fun `a path that means a different camera here does not resolve`() {
        assertFalse(cameraResolves(ref("Logitech BRIO", "avfoundation://0"), listOf(faceTime)))
    }

    /** The same camera moved to another port is still that camera, and still resolves. */
    @Test
    fun `a device that has changed path is still recognised by its name`() {
        assertTrue(cameraResolves(ref("FaceTime HD Camera", "avfoundation://9"), listOf(faceTime)))
    }

    @Test
    fun `a DeckLink is matched on the card index, which is its identity`() {
        val onCardOne = CameraDeviceRef(
            devicePath = "decklink://1", deviceName = "renamed", isDeckLink = true, deckLinkIndex = 1,
        )

        assertTrue(cameraResolves(onCardOne, listOf(card)))
        assertFalse(cameraResolves(onCardOne.copy(deckLinkIndex = 2), listOf(card)))
    }

    @Test
    fun `nothing chosen never resolves, whatever this machine has`() {
        assertFalse(cameraResolves(CameraDeviceRef(), listOf(faceTime)))
        assertFalse(cameraResolves(CameraDeviceRef(), null))
    }

    /**
     * Before anything has enumerated, a configured camera is accepted. Rejecting would drop it to
     * the settings background for as long as the enumeration takes, on every cold start, and the
     * capture layer draws black anyway if it turns out not to open.
     */
    @Test
    fun `a camera is accepted while this machine has not yet been asked`() {
        assertTrue(cameraResolves(ref("FaceTime HD Camera", "avfoundation://0"), null))
    }

    @Test
    fun `a machine with no cameras at all resolves nothing`() {
        assertFalse(cameraResolves(ref("FaceTime HD Camera", "avfoundation://0"), emptyList()))
    }
}
