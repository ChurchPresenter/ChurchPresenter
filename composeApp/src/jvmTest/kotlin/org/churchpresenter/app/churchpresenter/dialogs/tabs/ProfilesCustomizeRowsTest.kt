@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

class ProfilesCustomizeRowsTest {

    private val band = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL

    private fun bibleDoc(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        bible = BibleSettings(
            transitionDuration = 555f,
            lowerThirdHeightPercent = 29,
            translations = listOf(
                BibleTranslationSettings(fileName = "kjv.spb", textFontSize = 61),
                BibleTranslationSettings(fileName = "syn.spb", textFontSize = 61),
            ),
        ),
    )

    private fun songDoc(mode: String = Constants.DISPLAY_MODE_FULLSCREEN) = profileDocument(
        mode = mode,
        profile = OutputProfile(songMode = Constants.SONG_LANG_PRIMARY),
        song = SongSettings(
            lyricsChordColor = "#4FD3E8",
            transitionDuration = 444f,
            lowerThirdHeightPercent = 27,
        ),
    )

    private fun SemanticsNodeInteraction.bounds(): Rect = fetchSemanticsNode().boundsInRoot

    private fun assertSameRow(a: Rect, b: Rect, what: String) {
        assertTrue(a.center.y in b.top..b.bottom && b.center.y in a.top..a.bottom, "$what: $a vs $b")
    }

    private fun SkikoComposeUiTest.numberField(value: String) =
        onNode(hasSetTextAction() and hasText(value))

    @Test
    fun `colour, style buttons, font and size share one row`() {
        profilesTab(bibleDoc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertSameRow(styleButton(0, "B").bounds(), numberField("61").bounds(), "size beside the faces")
        }
    }

    @Test
    fun `vertical alignment sits on the horizontal alignment's row`() {
        profilesTab(bibleDoc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertSameRow(
                horizontalAlignButton(0, HAlign.RIGHT).bounds(),
                onNodeWithContentDescription("Align Top").bounds(),
                "vertical beside horizontal",
            )
        }
    }

    @Test
    fun `the reference's position comes before its transform`() {
        profilesTab(bibleDoc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_REFERENCE)
            val position = positionButton(0, above = true).bounds()
            val transform = onNodeWithText("UPPERCASE").bounds()
            val sameLineBefore = position.top < transform.bottom && position.right < transform.left
            assertTrue(
                position.bottom <= transform.top || sameLineBefore,
                "position $position must read before transform $transform",
            )
        }
    }

    @Test
    fun `the arrangement is one strip, each layout as wide as its own name`() {
        profilesTab(bibleDoc()) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            val named = onNodeWithText("Left / Right").bounds()
            val grid = onNodeWithText("2x2").bounds()
            for (label in listOf("Top / Bottom", "1x3", "3x1", "1x4", "4x1")) {
                assertSameRow(named, onNodeWithText(label).bounds(), label)
            }
            assertSameRow(named, grid, "2x2")
            assertTrue(named.width > grid.width, "a long name gets a wider segment: $named vs $grid")
        }
    }

    @Test
    fun `the chord colour sits in the colour row, before the style buttons`() {
        profilesTab(songDoc()) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            val chord = onNodeWithText("#4FD3E8").bounds()
            val bold = styleButton(0, "B").bounds()
            assertSameRow(chord, bold, "chord colour beside the faces")
            assertTrue(chord.right < bold.left, "the chord colour comes first")
        }
    }

    @Test
    fun `a band's height sits on the motion row, for the Bible and for songs`() {
        profilesTab(bibleDoc(band)) { _ ->
            openCustomizePane(CustomizePane.BIBLE, CustomizeElement.BIBLE_TEXT)
            assertSameRow(numberField("555").bounds(), numberField("29").bounds(), "Bible band height")
        }
        profilesTab(songDoc(band)) { _ ->
            openCustomizePane(CustomizePane.SONGS, CustomizeElement.SONG_LYRICS)
            assertSameRow(numberField("444").bounds(), numberField("27").bounds(), "song band height")
        }
    }
}
