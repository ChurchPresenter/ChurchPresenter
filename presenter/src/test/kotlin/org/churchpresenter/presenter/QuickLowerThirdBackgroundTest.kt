@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

class QuickLowerThirdBackgroundTest {

    /** The band an output was configured with: a transparent one, keyed downstream. */
    private val keyedBand = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT)

    private fun resolveBand(quickLowerThird: SongBackground?): ResolvedBackground {
        val settings = BackgroundSettings(
            quickBackground = SongBackground(type = SongBackgroundType.COLOR, color = "#000000"),
            quickLowerThirdBackground = quickLowerThird,
        )
        lateinit var resolved: ResolvedBackground
        runComposeUiTest {
            setContent {
                resolved = resolveBackground(
                    settings = settings,
                    config = keyedBand,
                    isLowerThird = true,
                    showBackground = true,
                    transparentWhenBlank = false,
                    knownCameras = null,
                )
            }
        }
        return resolved
    }

    @Test
    fun `a quick pick with no lower third of its own leaves the configured band on screen`() {
        val band = resolveBand(quickLowerThird = null)

        assertEquals(Constants.BACKGROUND_TRANSPARENT, band.type, "the band stays transparent, not black")
    }

    @Test
    fun `a quick pick with a lower third of its own still overrides the band`() {
        val band = resolveBand(SongBackground(type = SongBackgroundType.COLOR, color = "#ff0000"))

        assertEquals(Constants.BACKGROUND_COLOR, band.type)
        assertEquals(Color.Red, band.color)
    }
}
