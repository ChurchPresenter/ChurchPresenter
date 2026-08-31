package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.app.churchpresenter.composables.SharedCameraFrameCache
import org.churchpresenter.app.churchpresenter.composables.cameraSourceOn
import org.churchpresenter.app.churchpresenter.dialogs.tabs.cameraRefOn
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.camera.asCameraSource
import org.churchpresenter.core.models.scene.SceneSource
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.BackgroundConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Picking a camera as a background, and the one property that has to hold across all three places
 * a camera can be picked.
 *
 * The capture behind a camera is shared and ref-counted on the device's identity alone, and a
 * device opens **once**: a second key for the same hardware is a second ffmpeg process that fails
 * `DEVICE_BUSY`, or two DeckLink handles fighting over one input. So a background and a Canvas
 * layer pointed at the same device must produce the same key — which is what these assert, against
 * the real `keyFor` rather than against a restatement of it.
 */
class SongBackgroundCameraTest {

    private val webcam = CameraDevice(
        name = "Logitech BRIO", path = "avfoundation://1", displayName = "Logitech BRIO",
    )
    private val card = CameraDevice(
        name = "DeckLink Mini Recorder", path = "decklink://1", displayName = "DeckLink: Mini Recorder",
        isDeckLink = true, deckLinkIndex = 1,
    )

    private fun blankLayer() = SceneSource.CameraSource(id = "layer", name = "Stage")

    private fun keyOfLayer(device: CameraDevice) =
        SharedCameraFrameCache.keyFor(cameraSourceOn(blankLayer(), device))

    @Test
    fun `a song background on a device keys the same capture as a canvas layer on it`() {
        val song = cameraBackground(SongBackground(), webcam)

        assertEquals(
            keyOfLayer(webcam),
            SharedCameraFrameCache.keyFor(song.camera.asCameraSource()),
        )
    }

    @Test
    fun `a settings background on a device keys the same capture as a canvas layer on it`() {
        val config = BackgroundConfig(camera = cameraRefOn(CameraDeviceRef(), webcam))

        assertEquals(
            keyOfLayer(webcam),
            SharedCameraFrameCache.keyFor(config.camera.asCameraSource()),
        )
    }

    @Test
    fun `a DeckLink keys the same from either door`() {
        val song = cameraBackground(SongBackground(), card)
        val config = BackgroundConfig(camera = cameraRefOn(CameraDeviceRef(), card))

        assertEquals(keyOfLayer(card), SharedCameraFrameCache.keyFor(song.camera.asCameraSource()))
        assertEquals(keyOfLayer(card), SharedCameraFrameCache.keyFor(config.camera.asCameraSource()))
    }

    @Test
    fun `choosing a device makes the background a camera and records what it is`() {
        val song = cameraBackground(SongBackground(), card)

        assertEquals(SongBackgroundType.CAMERA, song.type)
        assertEquals("decklink://1", song.camera.devicePath)
        assertEquals("DeckLink Mini Recorder", song.camera.deviceName)
        assertEquals(1, song.camera.deckLinkIndex)
    }

    /**
     * A mode enumerated from one device means nothing on another, so both pickers clear it — the
     * same thing `cameraSourceOn` does for a Canvas layer.
     */
    @Test
    fun `changing device clears the format and connection the old one was set to`() {
        val pinned = CameraDeviceRef(
            devicePath = "decklink://1", deviceName = "DeckLink Mini Recorder",
            videoFormat = "1920x1080@30", videoConnection = 4, isDeckLink = true, deckLinkIndex = 1,
        )

        val moved = cameraRefOn(pinned, webcam)
        val movedSong = cameraBackground(SongBackground(camera = pinned), webcam)

        assertEquals("", moved.videoFormat)
        assertEquals(0, moved.videoConnection)
        assertEquals("", movedSong.camera.videoFormat)
        assertEquals(0, movedSong.camera.videoConnection)
    }

    /** The trap named in the class comment, stated as an assertion: same device, different format. */
    @Test
    fun `the same device at a different format is a different capture`() {
        val auto = cameraRefOn(CameraDeviceRef(), webcam)
        val pinned = auto.copy(videoFormat = "1920x1080@30")

        assert(
            SharedCameraFrameCache.keyFor(auto.asCameraSource()) !=
                SharedCameraFrameCache.keyFor(pinned.asCameraSource())
        ) { "auto and a pinned format must not be treated as one capture" }
    }
}
