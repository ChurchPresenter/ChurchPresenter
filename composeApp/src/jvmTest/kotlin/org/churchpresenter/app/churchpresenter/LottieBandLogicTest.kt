package org.churchpresenter.app.churchpresenter

import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LottieBandLogicTest {

    private fun lottie(path: String) =
        BackgroundConfig(backgroundType = Constants.BACKGROUND_LOTTIE, backgroundLottie = path)

    @Test
    fun `a band is a Lottie band only with the type and a path`() {
        assertTrue(usesBibleLottieBand(lottie("/a.json")))
        assertFalse(usesBibleLottieBand(lottie("")))
        val colorWithPath = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundLottie = "/a.json")
        assertFalse(usesBibleLottieBand(colorWithPath))
    }

    @Test
    fun `the global band wins, then the first output override, per content`() {
        val global =
            AppSettings(backgroundSettings = BackgroundSettings(bibleLowerThirdBackground = lottie("/bible.json")))
        assertEquals("/bible.json", lottieBandPath(global, Presenting.BIBLE))
        assertNull(lottieBandPath(global, Presenting.LYRICS), "songs have no band configured")
        assertNull(lottieBandPath(global, Presenting.PICTURES), "and pictures never have one")

        val overridden = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(),
                    ScreenAssignment(
                        backgroundOverride = BackgroundSettings(songLowerThirdBackground = lottie("/mine.json")),
                    ),
                ),
            ),
        )
        assertEquals("/mine.json", lottieBandPath(overridden, Presenting.LYRICS))
        assertNull(lottieBandPath(AppSettings(), Presenting.BIBLE))
    }
}
