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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A title slide set to no background (#1016), as [resolveBackground] decides it. */
class TitleSlideBackgroundResolveTest {

    private val songs = BackgroundConfig(backgroundType = Constants.BACKGROUND_COLOR, backgroundColor = "#336699")
    private val own = SongBackground(type = SongBackgroundType.COLOR, color = "#CC2200")

    @Suppress("LongParameterList")
    private fun resolve(
        hide: Boolean,
        isLowerThird: Boolean = false,
        transparentWhenBlank: Boolean = false,
        quick: SongBackground? = null,
        ownBackground: SongBackground = SongBackground(),
        showBackground: Boolean = true,
    ): ResolvedBackground {
        val settings = BackgroundSettings(quickBackground = quick, quickLowerThirdBackground = quick)
        lateinit var resolved: ResolvedBackground
        runComposeUiTest {
            setContent {
                resolved = resolveBackground(
                    settings = settings,
                    config = songs,
                    isLowerThird = isLowerThird,
                    showBackground = showBackground,
                    transparentWhenBlank = transparentWhenBlank,
                    ownBackground = ownBackground,
                    hideOwnBackground = hide,
                    knownCameras = null,
                )
            }
        }
        return resolved
    }

    @Test
    fun `none on a window draws black and is marked bare`() {
        val bg = resolve(hide = true)

        assertEquals(Constants.BACKGROUND_COLOR, bg.type)
        assertEquals(Color.Black, bg.color)
        assertTrue(bg.bare)
    }

    @Test
    fun `none on an output that keys draws nothing`() {
        val bg = resolve(hide = true, transparentWhenBlank = true)

        assertEquals(Constants.BACKGROUND_TRANSPARENT, bg.type)
        assertTrue(bg.bare)
    }

    @Test
    fun `none on a lower third draws nothing whatever the output keys`() {
        val bg = resolve(hide = true, isLowerThird = true)

        assertEquals(Constants.BACKGROUND_TRANSPARENT, bg.type)
        assertTrue(bg.bare)
    }

    @Test
    fun `none beats the song's own background`() {
        val bg = resolve(hide = true, ownBackground = own)

        assertTrue(bg.bare)
        assertEquals(Color.Black, bg.color)
    }

    @Test
    fun `the quick tray's pick still wins over none`() {
        val bg = resolve(hide = true, quick = SongBackground(type = SongBackgroundType.COLOR, color = "#00FF00"))

        assertEquals(Constants.BACKGROUND_COLOR, bg.type)
        assertEquals(Color.Green, bg.color)
        assertFalse(bg.bare)
    }

    @Test
    fun `a slide that keeps its background draws it as before`() {
        val bg = resolve(hide = false, ownBackground = own)

        assertEquals(Color(0xFFCC2200), bg.color)
        assertFalse(bg.bare)
    }

    @Test
    fun `a blanked output stays blank and is not marked bare`() {
        val bg = resolve(hide = true, showBackground = false)

        assertEquals(Color.Black, bg.color)
        assertFalse(bg.bare, "blanking is the output's, not the title slide's")
    }
}
