@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.composables.CameraDevice
import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A camera as it reaches a presenter: which one wins, and what carries the device along with it.
 *
 * [ResolvedBackground] is the one thing every presenter draws from, so a camera that survives the
 * ladder here is a camera that reaches a verse, a lyric and every output. What cannot be tested is
 * the picture itself — capture needs hardware and a display — so the split is deliberate: these
 * assert the decisions, and [ResolvedBackground.usesCamera] is the line the renderer keys on.
 */
class PresenterCameraBackgroundTest {

    private val webcam = CameraDeviceRef(
        devicePath = "avfoundation://1", deviceName = "Logitech BRIO", videoFormat = "1920x1080@30",
    )
    private val card = CameraDeviceRef(
        devicePath = "decklink://1", deviceName = "Mini Recorder", isDeckLink = true, deckLinkIndex = 1,
    )

    private fun cameraConfig(camera: CameraDeviceRef = webcam) =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_CAMERA, camera = camera)

    private fun cameraSong(camera: CameraDeviceRef = card) =
        SongBackground(type = SongBackgroundType.CAMERA, camera = camera)

    /**
     * Runs the resolver in a composition and hands back what it decided.
     *
     * [knownCameras] is passed explicitly on every call, and defaults to null — "nothing has
     * enumerated". It must never be left to `CameraDeviceCatalog`: that is process-global and a
     * composition fills it, so a camera property panel rendered by any earlier suite in this fork
     * would decide these assertions instead of the arguments below.
     */
    private fun resolve(
        settings: BackgroundSettings = BackgroundSettings(),
        config: BackgroundConfig = BackgroundConfig(),
        isLowerThird: Boolean = false,
        showBackground: Boolean = true,
        transparentWhenBlank: Boolean = false,
        ownBackground: SongBackground = SongBackground(),
        knownCameras: List<CameraDevice>? = null,
    ): ResolvedBackground {
        lateinit var resolved: ResolvedBackground
        runComposeUiTest {
            setContent {
                resolved = resolveBackground(
                    settings = settings,
                    config = config,
                    isLowerThird = isLowerThird,
                    showBackground = showBackground,
                    transparentWhenBlank = transparentWhenBlank,
                    ownBackground = ownBackground,
                    knownCameras = knownCameras,
                )
            }
        }
        return resolved
    }

    // ── What counts as a camera ─────────────────────────────────────────────────

    @Test
    fun `a camera with a device drawn, one without it not`() {
        assertTrue(ResolvedBackground(Constants.BACKGROUND_CAMERA, "", "", BLACK, camera = webcam).usesCamera)
        assertFalse(ResolvedBackground(Constants.BACKGROUND_CAMERA, "", "", BLACK).usesCamera)
    }

    @Test
    fun `no other type is mistaken for a camera`() {
        assertFalse(ResolvedBackground(Constants.BACKGROUND_VIDEO, "", "/clip.mp4", BLACK, camera = webcam).usesCamera)
        assertFalse(ResolvedBackground(Constants.BACKGROUND_COLOR, "", "", BLACK, camera = webcam).usesCamera)
    }

    // ── The ladder ──────────────────────────────────────────────────────────────

    @Test
    fun `a content surface set to a camera resolves to it`() {
        val resolved = resolve(config = cameraConfig())

        assertEquals(Constants.BACKGROUND_CAMERA, resolved.type)
        assertEquals(webcam, resolved.camera)
        assertTrue(resolved.usesCamera)
    }

    @Test
    fun `a surface on Default inherits the Default card's camera`() {
        val resolved = resolve(
            settings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_CAMERA,
                defaultBackgroundCamera = webcam,
            ),
            config = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
        )

        assertEquals(webcam, resolved.camera)
        assertTrue(resolved.usesCamera)
    }

    @Test
    fun `a lower third on Default inherits the lower-third card's camera`() {
        val resolved = resolve(
            settings = BackgroundSettings(
                defaultBackgroundCamera = webcam,
                defaultLowerThirdBackgroundType = Constants.BACKGROUND_CAMERA,
                defaultLowerThirdBackgroundCamera = card,
            ),
            config = BackgroundConfig(backgroundType = Constants.BACKGROUND_DEFAULT),
            isLowerThird = true,
        )

        assertEquals(card, resolved.camera, "the band's own card, not the full-screen one")
    }

    /** A song brings its own camera, and it outranks the surface's configured one. */
    @Test
    fun `a song's own camera beats the configured background`() {
        val resolved = resolve(config = cameraConfig(), ownBackground = cameraSong())

        assertEquals(card, resolved.camera)
        assertTrue(resolved.usesCamera)
    }

    /** The quick tray is an operator overriding what is on screen right now, so it outranks both. */
    @Test
    fun `the quick tray's camera beats the song's and the surface's`() {
        val resolved = resolve(
            settings = BackgroundSettings(quickBackground = cameraSong(webcam)),
            config = cameraConfig(card),
            ownBackground = cameraSong(card),
        )

        assertEquals(webcam, resolved.camera)
    }

    /**
     * A blanked output shows nothing at all — a camera must not keep running through a blank, which
     * is the one moment the operator has explicitly asked for nothing on screen.
     */
    @Test
    fun `a blanked output draws no camera`() {
        val resolved = resolve(config = cameraConfig(), showBackground = false)

        assertFalse(resolved.usesCamera)
        assertEquals(Constants.BACKGROUND_COLOR, resolved.type)
    }

    @Test
    fun `a song's camera is dropped when this machine does not have the device`() {
        val resolved = resolve(
            config = BackgroundConfig(),
            ownBackground = cameraSong(),
            knownCameras = listOf(
                CameraDevice(name = "Logitech BRIO", path = "avfoundation://1", displayName = "Logitech BRIO")
            ),
        )

        assertFalse(resolved.usesCamera, "the song names a card this machine has not got")
    }

    /**
     * Accepting before the first enumeration answers is deliberate — see `cameraResolves`. A cold
     * start would otherwise drop a perfectly good camera to the settings background for as long as
     * shelling out to ffmpeg takes.
     */
    @Test
    fun `a song's camera is kept while this machine has not been asked`() {
        val resolved = resolve(config = BackgroundConfig(), ownBackground = cameraSong(), knownCameras = null)

        assertTrue(resolved.usesCamera, "accepted while this machine has not been asked")
    }

    private companion object {
        val BLACK = androidx.compose.ui.graphics.Color.Black
    }
}
