@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BackgroundConfig
import org.churchpresenter.settings.BackgroundSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/** What the tab is made of: the rail's six surfaces, and the editor each of them opens. */
class BackgroundSettingsTabStructureTest {

    @Test
    fun `the rail groups its six surfaces under three headings`() = backgroundTab { _ ->
        listOf("BACKGROUNDS", "DEFAULTS", "BIBLE", "SONGS").forEach {
            onNodeWithText(it).assertIsDisplayed()
        }
        assertEquals(1, railCount("Default"), "one Default row")
        assertEquals(1, railCount("Default Lower Third"), "one Default Lower Third row")
        assertEquals(2, railCount("Full Screen"), "Bible and Songs each get a full-screen row")
        assertEquals(2, railCount("Lower Third"), "Bible and Songs each get a lower-third row")
    }

    @Test
    fun `each rail row opens the surface it names`() = backgroundTab { _ ->
        Surface.entries.forEach { surface ->
            openSurface(surface)
            assertEquals(
                1,
                headerCount(surface.title),
                "${'$'}{surface.name} must put its own name in the editor header",
            )
        }
    }

    @Test
    fun `the tab opens on the default surface`() = backgroundTab { _ ->
        assertEquals(1, headerCount(Surface.DEFAULT.title), "the Default surface is open")
        assertEquals(1, controlsCount(TypeLabel.COLOR), "and it starts on Color")
    }

    @Test
    fun `only a surface that can inherit offers the types it inherits with`() = backgroundTab { _ ->
        openSurface(Surface.DEFAULT)
        assertEquals(0, controlsCount(TypeLabel.DEFAULT), "the chain ends at Default, which inherits nothing")
        assertEquals(0, controlsCount(TypeLabel.FOLLOW_DEFAULT), "nor does it follow anything")

        openSurface(Surface.DEFAULT_LOWER_THIRD)
        assertEquals(1, controlsCount(TypeLabel.FOLLOW_DEFAULT), "the default lower third follows the default")

        openSurface(Surface.BIBLE)
        assertEquals(1, controlsCount(TypeLabel.DEFAULT), "a content surface inherits by saying Default")
    }

    @Test
    fun `gradient is offered only by the two content lower thirds`() = backgroundTab { _ ->
        val offered = Surface.entries.filter { surface ->
            openSurface(surface)
            controlsCount(TypeLabel.GRADIENT) == 1
        }
        assertEquals(
            listOf(Surface.BIBLE_LOWER_THIRD, Surface.SONG_LOWER_THIRD),
            offered,
            "a band drawn over something else is the one place a fade to transparent means anything",
        )
    }

    @Test
    fun `every surface offers the five types that stand on their own`() = backgroundTab { _ ->
        Surface.entries.forEach { surface ->
            openSurface(surface)
            listOf(TypeLabel.IMAGE, TypeLabel.VIDEO, TypeLabel.CAMERA).forEach {
                assertEquals(1, controlsCount(it), "${surface.name} must offer $it")
            }
            // A lower third also carries the wash's own type row, which offers a Color and a
            // Transparent of its own — deliberately the same two words, since it is the same
            // choice one rectangle up. See BackgroundAboveBandSectionTest.
            val rows = if (surface.title.contains("Lower Third")) 2 else 1
            listOf(TypeLabel.COLOR, TypeLabel.TRANSPARENT).forEach {
                assertEquals(rows, controlsCount(it), "${surface.name} must offer $it")
            }
        }
    }

    @Test
    fun `a surface on Color shows a colour field and the three look sliders`() = backgroundTab { _ ->
        openSurface(Surface.DEFAULT)
        assertEquals(1, controlsCount("COLOR"), "the colour field is captioned")
        listOf(SliderCaption.OPACITY, SliderCaption.DIM, SliderCaption.BLUR).forEach {
            assertEquals(1, controlsCount(it), "$it must be on offer")
        }
    }

    @Test
    fun `a surface on Transparent has nothing to adjust`() = backgroundTab { _ ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.TRANSPARENT)
        assertEquals(0, controlsCount("COLOR"), "there is no colour to set")
        listOf(SliderCaption.OPACITY, SliderCaption.DIM, SliderCaption.BLUR).forEach {
            assertEquals(0, controlsCount(it), "$it has nothing to act on")
        }
    }

    @Test
    fun `switching to Image replaces the colour field with a picker row`() = backgroundTab { _ ->
        setSurfaceType(Surface.DEFAULT, TypeLabel.IMAGE)
        assertEquals(0, controlsCount("COLOR"), "the colour field goes")
        assertEquals(1, controlsCount("IMAGE FILE"), "and the picker arrives")
        assertEquals(1, controlsCount(SliderCaption.BLUR), "a picture is still adjustable")
    }

    @Test
    fun `a surface set to a video shows the video picker`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultBackgroundVideo = "/library/waves.mov",
            ),
        )
        backgroundTab(settings) { _ ->
            assertEquals(1, controlsCount("VIDEO FILE"), "the video picker must be captioned")
            assertEquals(0, controlsCount("IMAGE FILE"), "and the image picker must not be there")
        }
    }

    @Test
    fun `the look sliders survive on every adjustable type`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                defaultBackgroundType = Constants.BACKGROUND_VIDEO,
                defaultBackgroundVideo = "/library/waves.mov",
            ),
        )
        backgroundTab(settings) { _ ->
            assertEquals(1, controlsCount(SliderCaption.DIM), "a clip can be dimmed")
            assertEquals(1, controlsCount(SliderCaption.BLUR), "and blurred")
        }
    }


    @Test
    fun `a lower-third surface on Gradient shows the gradient controls`() {
        val settings = AppSettings(
            backgroundSettings = BackgroundSettings(
                bibleLowerThirdBackground = BackgroundConfig(
                    backgroundType = Constants.BACKGROUND_GRADIENT,
                    gradientEnabled = true,
                ),
            ),
        )
        backgroundTab(settings) { _ ->
            openSurface(Surface.BIBLE_LOWER_THIRD)
            assertEquals(1, controlsCount("TOP COLOR:"), "a gradient has two ends")
            assertEquals(1, controlsCount("BOTTOM COLOR:"), "and this is the other")
        }
    }
}
