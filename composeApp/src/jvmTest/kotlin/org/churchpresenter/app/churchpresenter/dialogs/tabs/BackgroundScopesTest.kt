package org.churchpresenter.app.churchpresenter.dialogs.tabs

import org.churchpresenter.core.models.camera.CameraDeviceRef
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which part of the output each surface paints, and how tall its band is. */
class BackgroundScopesTest {

    @Test
    fun `every lower-third surface paints the band, the default one included`() {
        BackgroundScope.entries.filter { it.lowerThird }.forEach {
            assertEquals(BackgroundCoverage.BAND, it.coverage, "$it paints the band")
        }
    }

    @Test
    fun `every full-screen surface paints the whole output`() {
        BackgroundScope.entries.filterNot { it.lowerThird }.forEach {
            assertEquals(BackgroundCoverage.FULL_SCREEN, it.coverage, "$it paints the whole output")
        }
    }

    @Test
    fun `every surface offers a camera`() {
        BackgroundScope.entries.forEach {
            assertTrue(Constants.BACKGROUND_CAMERA in it.typeOptions(), "$it offers a camera")
        }
    }

    /**
     * The two Default surfaces spell their background out in flat fields rather than carrying a
     * config, so each one needs its camera copied in both directions by hand. Miss either and the
     * camera works for the four content surfaces and silently fails to save for the Defaults.
     */
    @Test
    fun `a camera survives being written to a surface and read back`() {
        val camera = CameraDeviceRef(
            devicePath = "avfoundation://1", deviceName = "Logitech BRIO", videoFormat = "1920x1080@30",
        )
        val config = BackgroundConfig(backgroundType = Constants.BACKGROUND_CAMERA, camera = camera)

        BackgroundScope.entries.forEach { scope ->
            val stored = BackgroundSettings().withConfigFor(scope, config)

            assertEquals(camera, stored.configFor(scope).camera, "$scope keeps its camera")
        }
    }

    @Test
    fun `a content surface measures its own band, and a Default surface the taller of the two`() {
        val settings = AppSettings(
            bibleSettings = BibleSettings(lowerThirdHeightPercent = 25),
            songSettings = SongSettings(lowerThirdHeightPercent = 40),
        )
        assertEquals(0.25f, settings.bandFractionFor(BackgroundScope.BIBLE_LOWER_THIRD), 0.001f)
        assertEquals(0.40f, settings.bandFractionFor(BackgroundScope.SONG_LOWER_THIRD), 0.001f)
        assertEquals(
            0.40f,
            settings.bandFractionFor(BackgroundScope.DEFAULT_LOWER_THIRD),
            0.001f,
            "a Default surface sits behind both bands and takes the taller",
        )
    }

    @Test
    fun `only the two content bands offer a Lottie band`() {
        BackgroundScope.entries.forEach {
            val offers = Constants.BACKGROUND_LOTTIE in it.typeOptions()
            val isContentBand = it == BackgroundScope.BIBLE_LOWER_THIRD || it == BackgroundScope.SONG_LOWER_THIRD
            assertEquals(isContentBand, offers, "$it offers Lottie")
        }
    }

    @Test
    fun `above the band offers a picture, a clip and a camera but never a gradient or a Lottie`() {
        BackgroundScope.entries.forEach {
            val offered = it.aboveBandTypeOptions()
            val media = listOf(Constants.BACKGROUND_IMAGE, Constants.BACKGROUND_VIDEO, Constants.BACKGROUND_CAMERA)
            media.forEach { type -> assertTrue(type in offered, "$it offers $type above the band") }
            assertFalse(Constants.BACKGROUND_GRADIENT in offered, "$it offers no gradient above the band")
            assertFalse(Constants.BACKGROUND_LOTTIE in offered, "$it offers no Lottie above the band")
        }
    }

    /** The same hand-copying hazard as the band's camera above, for the Default Lower Third's flat fields. */
    @Test
    fun `what is above the band survives being written to a surface and read back`() {
        val camera = CameraDeviceRef(devicePath = "avfoundation://1", deviceName = "Logitech BRIO")
        val config = BackgroundConfig(
            aboveBandType = Constants.BACKGROUND_VIDEO,
            aboveBandImage = "/pictures/hall.jpg",
            aboveBandVideo = "/clips/loop.mp4",
            aboveBandCamera = camera,
        )

        BackgroundScope.entries.filter { it.lowerThird }.forEach { scope ->
            val read = BackgroundSettings().withConfigFor(scope, config).configFor(scope)

            assertEquals("/pictures/hall.jpg", read.aboveBandImage, "$scope keeps its picture")
            assertEquals("/clips/loop.mp4", read.aboveBandVideo, "$scope keeps its clip")
            assertEquals(camera, read.aboveBandCamera, "$scope keeps its camera")
        }
    }
}
