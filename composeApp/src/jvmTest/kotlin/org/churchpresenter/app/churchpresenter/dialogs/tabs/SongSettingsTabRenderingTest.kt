@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedPixels
import org.churchpresenter.ui.styleButton

/**
 * Proves the icon buttons *look* different depending on what is stored, for the controls that
 * publish no `Selected` or `ToggleableState` semantics: the alignment icons, the above/below
 * position pair, and the B/I/U/S style toggles. Their only feedback to the user is the border and
 * fill colour, so a test that does not look at pixels cannot tell a working control from one whose
 * selected styling was deleted.
 *
 * Each test renders the same button twice from two fixtures that differ **only** in the setting
 * under test, and asserts the two renderings differ. Nothing is clicked, so no focus ring, hover
 * highlight or press ripple can account for the difference — the stored value is the only variable.
 * That matters: clicking a button changes its own pixels through focus indication alone, so a
 * before/after comparison on a clicked node passes even when selection is not drawn at all. The
 * interaction tests in the sibling classes assert the click writes the setting; these assert the
 * setting reaches the screen.
 *
 * Pixels are compared for equality only, never against expected colours, so the assertions hold on
 * all three target platforms.
 */
class SongSettingsTabRenderingTest {

    private fun settingsWith(change: SongSettings.() -> SongSettings): AppSettings =
        AppSettings().let { it.copy(songSettings = it.songSettings.change()) }

    /** Renders the tab from [settings] and returns the pixels of the button [locate] picks out. */
    private fun pixelsOf(
        settings: AppSettings,
        locate: ComposeUiTest.() -> SemanticsNodeInteraction,
    ): IntArray {
        var pixels = IntArray(0)
        songTab(initial = settings) { _ -> pixels = locate().performScrollTo().renderedPixels() }
        return pixels
    }

    private fun assertPaintsDifferently(what: String, a: IntArray, b: IntArray) {
        assertTrue(a.isNotEmpty() && b.isNotEmpty(), "$what: both renderings must have been captured")
        assertFalse(a.contentEquals(b), "$what must be painted differently when it is the stored choice")
    }

    private fun assertPaintsIdentically(what: String, a: IntArray, b: IntArray) {
        assertTrue(a.isNotEmpty(), "$what: the rendering must have been captured")
        assertTrue(a.contentEquals(b), "$what must be painted identically when nothing it shows changed")
    }

    // ── Horizontal alignment icons ──────────────────────────────────────────────────────────────

    @Test
    fun `an alignment button is painted differently when it holds the stored alignment`() {
        val group = HAlignGroup.TITLE_FULLSCREEN
        val whenChosen = pixelsOf(settingsWith { copy(titleHorizontalAlignment = Constants.LEFT) }) {
            horizontalAlignButton(group, HAlign.LEFT)
        }
        val whenNotChosen = pixelsOf(settingsWith { copy(titleHorizontalAlignment = Constants.CENTER) }) {
            horizontalAlignButton(group, HAlign.LEFT)
        }
        assertPaintsDifferently("the left-align button", whenChosen, whenNotChosen)
    }

    @Test
    fun `an alignment button is untouched by a change to a different block's alignment`() {
        val group = HAlignGroup.TITLE_FULLSCREEN
        val base = pixelsOf(AppSettings()) { horizontalAlignButton(group, HAlign.LEFT) }
        val otherBlockChanged = pixelsOf(settingsWith { copy(lyricsHorizontalAlignment = Constants.LEFT) }) {
            horizontalAlignButton(group, HAlign.LEFT)
        }
        assertPaintsIdentically("the title's left-align button", base, otherBlockChanged)
    }

    @Test
    fun `each alignment in a group paints its own button as the selected one`() {
        val group = HAlignGroup.LYRICS_FULLSCREEN
        val rightChosen = pixelsOf(settingsWith { copy(lyricsHorizontalAlignment = Constants.RIGHT) }) {
            horizontalAlignButton(group, HAlign.RIGHT)
        }
        val rightNotChosen = pixelsOf(settingsWith { copy(lyricsHorizontalAlignment = Constants.LEFT) }) {
            horizontalAlignButton(group, HAlign.RIGHT)
        }
        assertPaintsDifferently("the right-align button", rightChosen, rightNotChosen)

        val centreChosen = pixelsOf(settingsWith { copy(lyricsHorizontalAlignment = Constants.CENTER) }) {
            horizontalAlignButton(group, HAlign.CENTER)
        }
        val centreNotChosen = pixelsOf(settingsWith { copy(lyricsHorizontalAlignment = Constants.LEFT) }) {
            horizontalAlignButton(group, HAlign.CENTER)
        }
        assertPaintsDifferently("the centre-align button", centreChosen, centreNotChosen)
    }

    // ── Vertical alignment icons ────────────────────────────────────────────────────────────────

    @Test
    fun `the lyrics vertical alignment buttons paint the stored alignment as selected`() {
        val topChosen = pixelsOf(settingsWith { copy(lyricsAlignment = Constants.TOP) }) {
            onNodeWithContentDescription("Align Top")
        }
        val topNotChosen = pixelsOf(settingsWith { copy(lyricsAlignment = Constants.MIDDLE) }) {
            onNodeWithContentDescription("Align Top")
        }
        assertPaintsDifferently("the align-top button", topChosen, topNotChosen)

        val bottomChosen = pixelsOf(settingsWith { copy(lyricsAlignment = Constants.BOTTOM) }) {
            onNodeWithContentDescription("Align Bottom")
        }
        val bottomNotChosen = pixelsOf(settingsWith { copy(lyricsAlignment = Constants.MIDDLE) }) {
            onNodeWithContentDescription("Align Bottom")
        }
        assertPaintsDifferently("the align-bottom button", bottomChosen, bottomNotChosen)
    }

    // ── Above / below position icons ────────────────────────────────────────────────────────────

    @Test
    fun `the position buttons paint the stored position as selected`() {
        val group = PositionGroup.SONG_NUMBER_FULLSCREEN
        val aboveChosen = pixelsOf(settingsWith { copy(songNumberPosition = Constants.ABOVE_VERSE) }) {
            positionButton(group, above = true)
        }
        val aboveNotChosen = pixelsOf(settingsWith { copy(songNumberPosition = Constants.BELOW_VERSE) }) {
            positionButton(group, above = true)
        }
        assertPaintsDifferently("the above-verse button", aboveChosen, aboveNotChosen)

        val belowChosen = pixelsOf(settingsWith { copy(songNumberPosition = Constants.BELOW_VERSE) }) {
            positionButton(group, above = false)
        }
        val belowNotChosen = pixelsOf(settingsWith { copy(songNumberPosition = Constants.ABOVE_VERSE) }) {
            positionButton(group, above = false)
        }
        assertPaintsDifferently("the below-verse button", belowChosen, belowNotChosen)
    }

    @Test
    fun `the lower-third position buttons follow their own setting`() {
        val group = PositionGroup.SONG_NUMBER_LOWER_THIRD
        val chosen = pixelsOf(settingsWith { copy(songNumberLowerThirdPosition = Constants.ABOVE_VERSE) }) {
            positionButton(group, above = true)
        }
        val notChosen = pixelsOf(AppSettings()) { positionButton(group, above = true) }
        assertPaintsDifferently("the lower-third above-verse button", chosen, notChosen)

        // Moving the *fullscreen* number must not repaint the lower-third pair.
        val fullscreenMoved = pixelsOf(settingsWith { copy(songNumberPosition = Constants.ABOVE_VERSE) }) {
            positionButton(group, above = true)
        }
        assertPaintsIdentically("the lower-third above-verse button", notChosen, fullscreenMoved)
    }

    // ── B / I / U / S style toggles ─────────────────────────────────────────────────────────────

    @Test
    fun `the bold button is painted differently when bold is on`() {
        val group = StyleGroup.TITLE_FULLSCREEN
        val on = pixelsOf(settingsWith { copy(titleBold = true) }) { styleButton(group, "B") }
        val off = pixelsOf(AppSettings()) { styleButton(group, "B") }
        assertPaintsDifferently("the bold button", on, off)
    }

    @Test
    fun `the italic button is painted differently when italic is on`() {
        val group = StyleGroup.TITLE_FULLSCREEN
        val on = pixelsOf(settingsWith { copy(titleItalic = true) }) { styleButton(group, "I") }
        val off = pixelsOf(AppSettings()) { styleButton(group, "I") }
        assertPaintsDifferently("the italic button", on, off)
    }

    @Test
    fun `the underline button is painted differently when underline is on`() {
        val group = StyleGroup.TITLE_FULLSCREEN
        val on = pixelsOf(settingsWith { copy(titleUnderline = true) }) { styleButton(group, "U") }
        val off = pixelsOf(AppSettings()) { styleButton(group, "U") }
        assertPaintsDifferently("the underline button", on, off)
    }

    @Test
    fun `the shadow button is painted differently when the shadow is on`() {
        val group = StyleGroup.TITLE_FULLSCREEN
        val on = pixelsOf(settingsWith { copy(titleShadow = true) }) { styleButton(group, "S") }
        val off = pixelsOf(AppSettings()) { styleButton(group, "S") }
        assertPaintsDifferently("the shadow button", on, off)
    }

    @Test
    fun `each styled block's style buttons follow only its own flags`() {
        val group = StyleGroup.LYRICS_FULLSCREEN
        val off = pixelsOf(AppSettings()) { styleButton(group, "B") }
        val ownFlagOn = pixelsOf(settingsWith { copy(lyricsBold = true) }) { styleButton(group, "B") }
        val otherBlockOn = pixelsOf(settingsWith { copy(titleBold = true) }) { styleButton(group, "B") }
        assertPaintsDifferently("the lyrics bold button", ownFlagOn, off)
        assertPaintsIdentically("the lyrics bold button", off, otherBlockOn)
    }

    @Test
    fun `the look-ahead next block is drawn italic out of the box`() {
        // lookAheadNextItalic defaults on, so this block's italic button starts in the active state
        // while its neighbours' do not — the one place the eight blocks do not start identical.
        val lookAheadNext = pixelsOf(AppSettings()) { styleButton(StyleGroup.LOOK_AHEAD_NEXT, "I") }
        val turnedOff = pixelsOf(settingsWith { copy(lookAheadNextItalic = false) }) {
            styleButton(StyleGroup.LOOK_AHEAD_NEXT, "I")
        }
        assertPaintsDifferently("the look-ahead next italic button", lookAheadNext, turnedOff)
    }
}
