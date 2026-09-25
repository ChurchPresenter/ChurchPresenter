@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.withQuickBackground
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A quick-tray pick as it reaches a lower-third band: from the tile, through
 * [withQuickBackground], to what [resolveBackground] hands the band to draw.
 */
class QuickLowerThirdBackgroundTest {

    /** The band an output was configured with: a transparent one, keyed downstream. */
    private val keyedBand = BackgroundConfig(backgroundType = Constants.BACKGROUND_TRANSPARENT)

    private fun tile(lowerThird: SongBackground) = QuickBackground(
        id = "tile",
        background = SongBackground(type = SongBackgroundType.COLOR, color = "#000000"),
        lowerThirdBackground = lowerThird,
    )

    private fun resolveBand(picked: QuickBackground): ResolvedBackground {
        val settings: BackgroundSettings = withQuickBackground(AppSettings(), picked).backgroundSettings
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
    fun `a tile whose lower third inherits leaves the configured band on screen`() {
        val band = resolveBand(tile(lowerThird = SongBackground()))

        assertEquals(Constants.BACKGROUND_TRANSPARENT, band.type, "the band stays transparent, not black")
    }

    @Test
    fun `a tile with a lower third of its own still overrides the band`() {
        val band = resolveBand(
            tile(lowerThird = SongBackground(type = SongBackgroundType.COLOR, color = "#ff0000")),
        )

        assertEquals(Constants.BACKGROUND_COLOR, band.type)
        assertEquals(Color.Red, band.color)
    }
}
