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
}
