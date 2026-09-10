@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The Above The Band section: which surfaces offer it, what it offers them, and what it writes.
 *
 * The wash is drawn with the band's own controls, so its segments read "Color" and "Transparent"
 * exactly as the type row above it does. That is the point, and it means the two rows are told
 * apart here by order — [inControls] with `nth = 1` — rather than by wording.
 */
class BackgroundAboveBandSectionTest {

    private companion object {
        const val CAPTION = "ABOVE THE BAND"
        const val FILL_COLOR = "FILL COLOR"
        const val FILL_OPACITY = "FILL OPACITY"
    }

    /** The wash's own segment: the second node in the column reading [label]. */
    private fun ComposeUiTest.washSegment(label: String) = inControls(label, nth = 1)

    @Test
    fun `only a lower third has an area above a band to wash`() = backgroundTab { _ ->
        val offered = Surface.entries.filter { surface ->
            openSurface(surface)
            controlsCount(CAPTION) == 1
        }
        assertEquals(
            listOf(Surface.DEFAULT_LOWER_THIRD, Surface.BIBLE_LOWER_THIRD, Surface.SONG_LOWER_THIRD),
            offered,
            "a full screen paints the whole output and has nothing above a band",
        )
    }

    @Test
    fun `the default lower third is the top of the wash chain and offers no Default`() =
        backgroundTab { _ ->
            openSurface(Surface.DEFAULT_LOWER_THIRD)
            // Its band offers Follow Default; its wash offers neither that nor Default, because a
            // full screen has no area above a band for this one to follow.
            assertEquals(0, controlsCount(TypeLabel.DEFAULT), "no Default segment anywhere")
            assertEquals(1, controlsCount(TypeLabel.FOLLOW_DEFAULT), "that is the band's, not the wash's")
            assertEquals(2, controlsCount(TypeLabel.COLOR), "the band's Color and the wash's")
            assertEquals(2, controlsCount(TypeLabel.TRANSPARENT), "likewise Transparent")
        }

    @Test
    fun `a content lower third can defer its wash to the default`() = backgroundTab { _ ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        assertEquals(2, controlsCount(TypeLabel.DEFAULT), "the band's Default and the wash's")
    }

    @Test
    fun `a lower third starts deferring and shows no colour of its own`() = backgroundTab { _ ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        assertEquals(0, controlsCount(FILL_COLOR), "nothing to set while it follows the default")
        assertEquals(0, controlsCount(FILL_OPACITY))
    }

    @Test
    fun `choosing Color reveals the colour field and the opacity slider`() = backgroundTab { get ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        washSegment(TypeLabel.COLOR).performScrollTo().performClick()
        waitForIdle()
        assertEquals(1, controlsCount(FILL_COLOR), "the wash's colour field arrives")
        assertEquals(1, controlsCount(FILL_OPACITY), "and its opacity")
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.bibleLowerThirdBackground.aboveBandType,
        )
    }

    @Test
    fun `choosing Color leaves the band untouched`() = backgroundTab { get ->
        openSurface(Surface.SONG_LOWER_THIRD)
        val before = get().backgroundSettings.songLowerThirdBackground.backgroundType
        washSegment(TypeLabel.COLOR).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            before,
            get().backgroundSettings.songLowerThirdBackground.backgroundType,
            "the wash and the band are two decisions",
        )
    }

    @Test
    fun `choosing Transparent stores it and hides the colour controls`() = backgroundTab { get ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        washSegment(TypeLabel.COLOR).performScrollTo().performClick()
        waitForIdle()
        washSegment(TypeLabel.TRANSPARENT).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.BACKGROUND_TRANSPARENT,
            get().backgroundSettings.bibleLowerThirdBackground.aboveBandType,
        )
        assertEquals(0, controlsCount(FILL_COLOR), "there is no colour to set")
        assertEquals(0, controlsCount(FILL_OPACITY))
    }

    @Test
    fun `the default lower third writes its wash to the flat fields`() = backgroundTab { get ->
        openSurface(Surface.DEFAULT_LOWER_THIRD)
        washSegment(TypeLabel.COLOR).performScrollTo().performClick()
        waitForIdle()
        assertEquals(
            Constants.BACKGROUND_COLOR,
            get().backgroundSettings.defaultLowerThirdAboveBandType,
        )
    }

    @Test
    fun `the wash starts black, as the band's own colour does`() = backgroundTab { get ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        washSegment(TypeLabel.COLOR).performScrollTo().performClick()
        waitForIdle()
        assertEquals("#000000", get().backgroundSettings.bibleLowerThirdBackground.aboveBandColor)
    }

    @Test
    fun `the wash offers no picture, clip, camera or gradient`() = backgroundTab { _ ->
        openSurface(Surface.BIBLE_LOWER_THIRD)
        // One each — the band's. A second would be the wash offering something it cannot draw.
        listOf(TypeLabel.IMAGE, TypeLabel.VIDEO, TypeLabel.CAMERA, TypeLabel.GRADIENT).forEach {
            assertEquals(1, controlsCount(it), "the wash must not offer $it")
        }
    }
}
