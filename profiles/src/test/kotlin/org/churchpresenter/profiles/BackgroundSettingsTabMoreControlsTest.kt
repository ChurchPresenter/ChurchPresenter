@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.profiles

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.QuickBackground
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class BackgroundSettingsTabMoreControlsTest {

    private fun bandFixture() = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_GRADIENT,
                gradientEnabled = true,
                gradientBottomOpacity = 0.7f,
                aboveBandType = Constants.BACKGROUND_COLOR,
                aboveBandColor = "#313233",
                aboveBandOpacity = 0.5f,
            ),
        ),
    )

    private fun AppSettings.band() = backgroundSettings.bibleLowerThirdBackground

    @Test
    fun `the gradient's bottom opacity is stored`() = backgroundTab(bandFixture()) { settings ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        val bottom = dragSlider("BOTTOM OPACITY", 0.25f)
        assertEquals(bottom, (settings().band().gradientBottomOpacity * 100).toInt())
    }

    @Test
    fun `the wash above the band takes a color, an opacity and whether it fills behind`() =
        backgroundTab(bandFixture()) { settings ->
            openSurface(Surface.BIBLE_LOWER_THIRD)
            recolor("#313233", "#414243")
            assertEquals("#414243", settings().band().aboveBandColor.lowercase())
            val opacity = dragSlider("FILL OPACITY", 0.8f)
            assertEquals(opacity, (settings().band().aboveBandOpacity * 100).toInt())
            inControls("Fill behind the band too").scrollThenClick()
            waitForIdle()
            assertFalse(settings().band().aboveBandFillsBehindBand)
        }

    @Test
    fun `a look preset sets the dim and the blur together`() = backgroundTab { settings ->
        setSurfaceType(Surface.SONG, TypeLabel.COLOR)
        inControls("Cinema").scrollThenClick()
        waitForIdle()
        val song = settings().backgroundSettings.songBackground
        assertEquals(65, song.dim)
        assertEquals(12, song.blur)
    }

    @Test
    fun `dragging a tile along the strip moves it to another slot`() {
        val tiles = AppSettings(
            quickBackgrounds = listOf("#112233", "#445566").mapIndexed { index, color ->
                QuickBackground(
                    id = "tile$index",
                    background = SongBackground(type = SongBackgroundType.COLOR, color = color),
                )
            },
        )
        backgroundTab(tiles) { settings ->
            onNodeWithText("1").performTouchInput {
                down(center)
                repeat(12) { moveBy(Offset(10f, 0f)) }
                up()
            }
            waitForIdle()
            assertEquals(listOf("tile1", "tile0"), settings().quickBackgrounds.map { it.id })
        }
    }
}
