package org.churchpresenter.app.churchpresenter.dialogs

import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Pointing a song's background at a camera: what is stored, and what is deliberately reset. */
class SongBackgroundCamerasTest {

    private val webcam = CameraDevice("FaceTime HD Camera", "avfoundation://0", "FaceTime HD Camera")
    private val virtual = CameraDevice("OBS Virtual Camera", "avfoundation://1", "OBS Virtual Camera")
    private val card = CameraDevice(
        "DeckLink Mini", "decklink://0", "DeckLink: DeckLink Mini", isDeckLink = true, deckLinkIndex = 0,
    )
    private val card2 = CameraDevice(
        "DeckLink Duo", "decklink://1", "DeckLink: DeckLink Duo", isDeckLink = true, deckLinkIndex = 1,
    )

    private val inherited = SongBackground()

    private val tuned = SongBackground(
        type = SongBackgroundType.COLOR,
        color = "#123456",
        colorEnd = "#654321",
        image = "/photos/sunrise.jpg",
        video = "/clips/loop.mp4",
        dim = 40,
        blur = 6,
        opacity = 70,
    )

    // ── What it stores ────────────────────────────────────────────────────────

    @Test
    fun `choosing a camera makes it a camera background`() {
        assertEquals(SongBackgroundType.CAMERA, cameraBackground(inherited, webcam).type)
    }

    @Test
    fun `the device's path is stored`() {
        assertEquals("avfoundation://0", cameraBackground(inherited, webcam).camera.devicePath)
    }

    @Test
    fun `the device's name is stored, not its display name`() {
        val chosen = cameraBackground(inherited, card)
        assertEquals("DeckLink Mini", chosen.camera.deviceName, "the display name carries a prefix the name does not")
    }

    @Test
    fun `a chosen camera makes the reference set`() {
        assertFalse(inherited.camera.isSet)
        assertTrue(cameraBackground(inherited, webcam).camera.isSet)
    }

    @Test
    fun `a card records that it is one, and which slot`() {
        val chosen = cameraBackground(inherited, card2)
        assertTrue(chosen.camera.isDeckLink)
        assertEquals(1, chosen.camera.deckLinkIndex)
    }

    @Test
    fun `an ordinary camera is not recorded as a card`() {
        val chosen = cameraBackground(inherited, virtual)
        assertFalse(chosen.camera.isDeckLink)
        assertEquals(-1, chosen.camera.deckLinkIndex)
    }

    // ── What it resets ────────────────────────────────────────────────────────

    @Test
    fun `a format enumerated from another device is not carried over`() {
        val was = tuned.copy(camera = CameraDeviceRef(devicePath = "avfoundation://1", videoFormat = "1920x1080@30"))
        assertEquals("", cameraBackground(was, webcam).camera.videoFormat)
    }

    @Test
    fun `a connection chosen on another card is not carried over`() {
        val was = tuned.copy(
            camera = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 0, videoConnection = 4),
        )
        assertEquals(0, cameraBackground(was, card2).camera.videoConnection)
    }

    @Test
    fun `moving from a card to a webcam stops it being a card`() {
        val was = tuned.copy(camera = CameraDeviceRef(isDeckLink = true, deckLinkIndex = 1))
        val now = cameraBackground(was, webcam)
        assertFalse(now.camera.isDeckLink)
        assertEquals(-1, now.camera.deckLinkIndex)
    }

    // ── What it leaves alone ──────────────────────────────────────────────────

    @Test
    fun `the look settings survive the change`() {
        val chosen = cameraBackground(tuned, webcam)
        assertEquals(40, chosen.dim)
        assertEquals(6, chosen.blur)
        assertEquals(70, chosen.opacity)
    }

    @Test
    fun `the colors and files the background had are kept`() {
        val chosen = cameraBackground(tuned, webcam)
        assertEquals("#123456", chosen.color)
        assertEquals("#654321", chosen.colorEnd)
        assertEquals("/photos/sunrise.jpg", chosen.image, "a picture kept is a picture to come back to")
        assertEquals("/clips/loop.mp4", chosen.video)
    }

    @Test
    fun `a camera background reports no media path, because a device is not a file`() {
        assertEquals("", cameraBackground(tuned, webcam).mediaPath)
    }

    @Test
    fun `only the type and the camera move`() {
        val chosen = cameraBackground(tuned, webcam)
        assertEquals(
            tuned.copy(type = SongBackgroundType.CAMERA, camera = chosen.camera),
            chosen,
        )
    }

    // ── Switching between devices ─────────────────────────────────────────────

    @Test
    fun `re-choosing the same device changes nothing`() {
        val once = cameraBackground(inherited, webcam)
        assertEquals(once, cameraBackground(once, webcam))
    }

    @Test
    fun `choosing another device replaces the whole reference`() {
        val first = cameraBackground(inherited, webcam)
        val second = cameraBackground(first, virtual)
        assertEquals("avfoundation://1", second.camera.devicePath)
        assertEquals("OBS Virtual Camera", second.camera.deviceName)
    }

    @Test
    fun `every device round-trips into a background that names it`() {
        for (device in listOf(webcam, virtual, card, card2)) {
            val chosen = cameraBackground(inherited, device)
            assertEquals(device.path, chosen.camera.devicePath, device.displayName)
            assertEquals(device.name, chosen.camera.deviceName, device.displayName)
            assertEquals(device.isDeckLink, chosen.camera.isDeckLink, device.displayName)
            assertEquals(device.deckLinkIndex, chosen.camera.deckLinkIndex, device.displayName)
        }
    }

    @Test
    fun `a camera background is a custom one, not an inherited one`() {
        assertFalse(inherited.isCustom)
        assertTrue(cameraBackground(inherited, webcam).isCustom)
    }
}
