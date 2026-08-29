@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Each of the four content surfaces, and the gradient only two of them offer.
 *
 * The editor is one set of controls serving six surfaces, so what these prove is that a write lands
 * on the surface the rail has open and on no other — the thing a shared editor can most easily get
 * wrong.
 */
class BackgroundSettingsTabColumnsTest {

    private fun scopeOf(surface: Surface): BackgroundScope = when (surface) {
        Surface.DEFAULT -> BackgroundScope.DEFAULT
        Surface.DEFAULT_LOWER_THIRD -> BackgroundScope.DEFAULT_LOWER_THIRD
        Surface.BIBLE -> BackgroundScope.BIBLE
        Surface.BIBLE_LOWER_THIRD -> BackgroundScope.BIBLE_LOWER_THIRD
        Surface.SONG -> BackgroundScope.SONG
        Surface.SONG_LOWER_THIRD -> BackgroundScope.SONG_LOWER_THIRD
    }

    private fun configOf(settings: AppSettings, surface: Surface): BackgroundConfig =
        settings.backgroundSettings.configFor(scopeOf(surface))

    @Test
    fun `every surface stores the type it is given`() = backgroundTab { settings ->
        Surface.entries.forEach { surface ->
            setSurfaceType(surface, TypeLabel.TRANSPARENT)
            assertEquals(
                Constants.BACKGROUND_TRANSPARENT,
                configOf(settings(), surface).backgroundType,
                "${surface.name} must store what it was given",
            )
        }
    }

    @Test
    fun `a type written to one surface reaches no other`() = backgroundTab { settings ->
        setSurfaceType(Surface.BIBLE_LOWER_THIRD, TypeLabel.TRANSPARENT)
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            configOf(settings(), Surface.BIBLE_LOWER_THIRD).backgroundType,
        )
        listOf(Surface.BIBLE, Surface.SONG, Surface.SONG_LOWER_THIRD).forEach {
            assertEquals(
                Constants.BACKGROUND_COLOR,
                configOf(settings(), it).backgroundType,
                "${it.name} must be left as it was",
            )
        }
    }

    @Test
    fun `a surface can be put back on Color`() = backgroundTab { settings ->
        setSurfaceType(Surface.SONG, TypeLabel.TRANSPARENT)
        chooseBackgroundType(TypeLabel.COLOR)
        assertEquals(Constants.BACKGROUND_COLOR, configOf(settings(), Surface.SONG).backgroundType)
    }

    @Test
    fun `the inherit button takes a surface off its own look and back on again`() =
        backgroundTab { settings ->
            openSurface(Surface.BIBLE)
            assertEquals(Constants.BACKGROUND_COLOR, configOf(settings(), Surface.BIBLE).backgroundType)

            onNodeWithText("Use Default").performClick()
            waitForIdle()
            assertEquals(
                Constants.BACKGROUND_DEFAULT,
                configOf(settings(), Surface.BIBLE).backgroundType,
                "the button must put it back to inheriting",
            )
            onNodeWithText("Following Default").assertIsDisplayed()

            onNodeWithText("Following Default").performClick()
            waitForIdle()
            assertEquals(
                Constants.BACKGROUND_COLOR,
                configOf(settings(), Surface.BIBLE).backgroundType,
                "and pressing it again gives it a look of its own",
            )
        }

    @Test
    fun `choosing Gradient also raises the gradientEnabled flag`() = backgroundTab { settings ->
        setSurfaceType(Surface.BIBLE_LOWER_THIRD, TypeLabel.GRADIENT)
        val config = configOf(settings(), Surface.BIBLE_LOWER_THIRD)
        assertEquals(Constants.BACKGROUND_GRADIENT, config.backgroundType)
        assertTrue(config.gradientEnabled, "the presenters draw the overlay off this flag")
    }

    @Test
    fun `leaving Gradient for another type clears the flag`() = backgroundTab { settings ->
        setSurfaceType(Surface.SONG_LOWER_THIRD, TypeLabel.GRADIENT)
        chooseBackgroundType(TypeLabel.COLOR)
        val config = configOf(settings(), Surface.SONG_LOWER_THIRD)
        assertEquals(Constants.BACKGROUND_COLOR, config.backgroundType)
        assertFalse(config.gradientEnabled, "a colour must not keep drawing a gradient over itself")
    }

    @Test
    fun `the inherit button clears the gradient flag too`() = backgroundTab { settings ->
        setSurfaceType(Surface.BIBLE_LOWER_THIRD, TypeLabel.GRADIENT)
        onNodeWithText("Use Default").performClick()
        waitForIdle()
        assertFalse(
            configOf(settings(), Surface.BIBLE_LOWER_THIRD).gradientEnabled,
            "an inheriting surface must not carry a gradient of its own",
        )
    }

    private fun gradientFixture() = AppSettings(
        backgroundSettings = BackgroundSettings(
            bibleLowerThirdBackground = BackgroundConfig(
                backgroundType = Constants.BACKGROUND_GRADIENT,
                gradientEnabled = true,
                gradientTopColor = "#212223",
                gradientTopOpacity = 0.2f,
                gradientBottomColor = "#242526",
                gradientBottomOpacity = 0.7f,
                gradientPosition = 0.4f,
            ),
        ),
    )

    @Test
    fun `the gradient sliders read back what is stored`() = backgroundTab(gradientFixture()) { _ ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        assertSliderShows("TOP OPACITY", 20, "the top opacity slider")
        assertSliderShows("BOTTOM OPACITY", 70, "the bottom opacity slider")
        assertSliderShows("TRANSITION POSITION", 40, "the transition position slider")
    }

    @Test
    fun `the gradient colour fields store the confirmed hex`() = backgroundTab(gradientFixture()) { settings ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        // The picker normalises what it stores to upper case, so compare on that footing.
        recolor("#212223", "#aabbcc")
        assertEquals(
            "#aabbcc",
            settings().backgroundSettings.bibleLowerThirdBackground.gradientTopColor.lowercase(),
        )
        recolor("#242526", "#ddeeff")
        assertEquals(
            "#ddeeff",
            settings().backgroundSettings.bibleLowerThirdBackground.gradientBottomColor.lowercase(),
        )
    }

    @Test
    fun `the gradient sliders store new values`() = backgroundTab(gradientFixture()) { settings ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        val top = dragSlider("TOP OPACITY", 0.5f)
        assertEquals(
            top,
            (settings().backgroundSettings.bibleLowerThirdBackground.gradientTopOpacity * 100).toInt(),
        )
        val position = dragSlider("TRANSITION POSITION", 0.75f)
        assertEquals(
            position,
            (settings().backgroundSettings.bibleLowerThirdBackground.gradientPosition * 100).toInt(),
        )
    }

    @Test
    fun `a surface's colour is stored against that surface only`() = backgroundTab { settings ->
        setSurfaceType(Surface.SONG, TypeLabel.COLOR)
        recolor(BackgroundConfig().backgroundColor, "#272829")
        assertEquals("#272829", settings().backgroundSettings.songBackground.backgroundColor.lowercase())
        assertEquals(
            BackgroundConfig().backgroundColor,
            settings().backgroundSettings.bibleBackground.backgroundColor,
            "the Bible surface must be untouched",
        )
    }

    @Test
    fun `a surface's look sliders are stored against that surface only`() = backgroundTab { settings ->
        setSurfaceType(Surface.SONG_LOWER_THIRD, TypeLabel.COLOR)
        val dim = dragSlider(SliderCaption.DIM, 0.5f)
        assertEquals(dim, settings().backgroundSettings.songLowerThirdBackground.dim)
        assertEquals(0, settings().backgroundSettings.bibleLowerThirdBackground.dim)
        assertEquals(0, settings().backgroundSettings.defaultBackgroundDim)
    }

    @Test
    fun `copying a look puts it on the other surface of the same shape`() = backgroundTab { settings ->
        setSurfaceType(Surface.BIBLE, TypeLabel.COLOR)
        recolor(BackgroundConfig().backgroundColor, "#334455")
        val dim = dragSlider(SliderCaption.DIM, 0.5f)

        // Copy targets are named by the group they belong to.
        inControls("Songs").performClick()
        waitForIdle()
        val song = settings().backgroundSettings.songBackground
        assertEquals("#334455", song.backgroundColor.lowercase(), "the colour must travel")
        assertEquals(dim, song.dim, "and so must the look")
        assertEquals(Constants.BACKGROUND_COLOR, song.backgroundType)
    }

    @Test
    fun `an unrecognised stored type is left alone rather than rewritten`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleBackground = BackgroundConfig(backgroundType = "Hologram"),
            ),
        )
        backgroundTab(settings) { current ->
            openSurface(Surface.BIBLE)
            assertEquals(
                "Hologram",
                current().backgroundSettings.bibleBackground.backgroundType,
                "opening a surface must never rewrite what it holds",
            )
        }
    }

    @Test
    fun `a surface set to a picture shows its file and its opacity`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_IMAGE,
                    backgroundImage = "/library/stage.jpg",
                    backgroundOpacity = 0.5f,
                ),
            ),
        )
        backgroundTab(settings) { _ ->
            openSurface(Surface.SONG)
            onNodeWithText("stage.jpg", substring = true).assertIsDisplayed()
            assertSliderShows(SliderCaption.OPACITY, 50, "the opacity slider")
        }
    }

    @Test
    fun `a surface set to a clip shows it`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                songLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_VIDEO,
                    backgroundVideo = "/library/waves.mov",
                ),
            ),
        )
        backgroundTab(settings) { _ ->
            openSurface(Surface.SONG_LOWER_THIRD)
            onNodeWithText("waves.mov", substring = true).assertIsDisplayed()
        }
    }
}
