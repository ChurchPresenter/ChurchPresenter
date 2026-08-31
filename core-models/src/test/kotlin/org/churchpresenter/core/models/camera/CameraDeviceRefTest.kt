package org.churchpresenter.core.models.camera

import org.churchpresenter.core.models.scene.SceneSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The device identity a background stores, and its conversion to the scene source the capture is
 * keyed on.
 *
 * The conversion exists so that a background and a Canvas layer pointed at one device cannot
 * disagree about what that device is. The round trip below is what holds it to that: a field added
 * to one side and forgotten on the other fails here rather than as a camera that will not open on
 * a Sunday morning.
 */
class CameraDeviceRefTest {

    private fun full() = CameraDeviceRef(
        devicePath = "decklink://1",
        deviceName = "DeckLink Mini Recorder",
        videoFormat = "1920x1080@30",
        videoConnection = 4,
        isDeckLink = true,
        deckLinkIndex = 1,
    )

    @Test
    fun `every field survives the trip through a scene source`() {
        assertEquals(full(), full().asCameraSource().asDeviceRef())
    }

    @Test
    fun `the scene source names the same device the ref does`() {
        val source = full().asCameraSource()

        assertEquals("decklink://1", source.devicePath)
        assertEquals("DeckLink Mini Recorder", source.deviceName)
        assertEquals("1920x1080@30", source.videoFormat)
        assertEquals(4, source.videoConnection)
        assertTrue(source.isDeckLink)
        assertEquals(1, source.deckLinkIndex)
    }

    /**
     * A Canvas layer and a background on one device must produce sources the cache cannot tell
     * apart. The cache's key is built from path, format, connection and card index, so those four
     * are what this compares — the id and the layer's name are deliberately not among them.
     */
    @Test
    fun `a ref matches the canvas layer for the same device on every field the capture keys on`() {
        val layer = SceneSource.CameraSource(
            id = "layer-7",
            name = "Stage Left",
            devicePath = "avfoundation://0",
            deviceName = "FaceTime HD Camera",
            videoFormat = "1280x720@30",
        )

        val fromBackground = layer.asDeviceRef().asCameraSource()

        assertEquals(layer.devicePath, fromBackground.devicePath)
        assertEquals(layer.videoFormat, fromBackground.videoFormat)
        assertEquals(layer.videoConnection, fromBackground.videoConnection)
        assertEquals(layer.deckLinkIndex, fromBackground.deckLinkIndex)
    }

    @Test
    fun `a ref is set only once a device has been chosen`() {
        assertFalse(CameraDeviceRef().isSet)
        assertFalse(CameraDeviceRef(deviceName = "named but not pointed anywhere").isSet)
        assertTrue(CameraDeviceRef(devicePath = "v4l2:///dev/video2").isSet)
    }
}
