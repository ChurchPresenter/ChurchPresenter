@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.ElementOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The band's two hand-rolled layouts honouring an offset — #613, end to end through the real
 * presenter.
 *
 * [BandElementTest] pins the stack-or-float split in isolation and [BibleLowerThirdOffsetTest] pins
 * the stored pair. What neither reaches is the part that was actually hard: **both layouts also run
 * a fit search**, and the search has to know which halves left the column. An offset honoured only
 * where the text is drawn lets a positioned half grow past the band and be clipped; one measured
 * into the stack shrinks the stack for height it no longer occupies. So these drive
 * `BiblePresenter` itself, once down the single-column path and once down the side-by-side pair.
 *
 * Positions rather than pixels, for the reason the sibling render suites give: glyph shape differs
 * across platforms, where a placement is a number.
 */
class BibleBandOffsetRenderTest {

    private fun settings(
        count: Int,
        textOffset: ElementOffset? = null,
        referenceOffset: ElementOffset? = null,
    ) = AppSettings(
        bibleSettings = BibleSettings(primaryBible = KJV).withTranslations(
            TRANSLATIONS.take(count).map { (file, _) ->
                BibleTranslationSettings(
                    fileName = file,
                    lowerThirdTextOffset = textOffset,
                    lowerThirdReferenceOffset = referenceOffset,
                    // Small type, so the text is narrower than the band and the offset has room to
                    // move it through on both axes -- see `Modifier.elementOffset`.
                    lowerThirdTextFontSize = TYPE_SIZE,
                    lowerThirdReferenceFontSize = TYPE_SIZE,
                )
            },
        ),
    )

    private fun verses(count: Int) = TRANSLATIONS.take(count).map { (file, abbreviation) ->
        SelectedVerse(
            translationFileName = file,
            bibleAbbreviation = abbreviation,
            bibleName = abbreviation,
            bookName = "John",
            chapter = 3,
            verseNumber = 16,
            verseText = if (file == KJV) TEXT else TEXT_RU,
        )
    }

    /** Where the node holding [text] landed in a band of [count] translations. */
    private fun place(count: Int, settings: AppSettings, text: String): Pair<Float, Float> {
        var left = -1f
        var top = -1f
        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.size(BAND_W.dp, BAND_H.dp).background(Color.Black)) {
                    BiblePresenter(
                        selectedVerses = verses(count),
                        appSettings = settings,
                        isLowerThird = true,
                    )
                }
            }
            waitForIdle()
            val bounds = onNodeWithText(text, substring = true).getBoundsInRoot()
            left = bounds.left.value
            top = bounds.top.value
        }
        return left to top
    }

    // ── The single column: one translation ──────────────────────────────────────────────────────

    @Test
    fun `a positioned verse leaves the band's column`() {
        val stacked = place(1, settings(1), TEXT).second
        val top = place(1, settings(1, textOffset = TOP_LEFT), TEXT).second
        val bottom = place(1, settings(1, textOffset = BOTTOM_RIGHT), TEXT).second
        assertTrue(top < stacked, "0% should sit above the stacked position: $top vs $stacked")
        assertTrue(top < bottom, "0% should sit above 100%: $top vs $bottom")
    }

    /**
     * The X axis, which is the half that was silently dead.
     *
     * `OutlinedText` applies `fillMaxWidth()` **itself** unless told not to, so dropping it from the
     * modifier left the element still filling the band — and an element as wide as its frame has no
     * horizontal room for `Modifier.elementOffset` to move it through. Both the band layouts and the
     * already-shipped full-screen pair read as positioned and never moved sideways at all.
     */
    @Test
    fun `a positioned verse moves sideways`() {
        val left = place(1, settings(1, textOffset = TOP_LEFT), TEXT).first
        val right = place(1, settings(1, textOffset = BOTTOM_RIGHT), TEXT).first
        assertTrue(right > left, "100% must sit right of 0%: $right vs $left")
        assertTrue(right - left > BAND_W / 4f, "and by most of the band: ${right - left}")
    }

    @Test
    fun `a positioned reference leaves it too, and the verse keeps the column`() {
        val moved = settings(1, referenceOffset = TOP_RIGHT)
        val reference = place(1, moved, REFERENCE)
        val stackedRef = place(1, settings(1), REFERENCE)
        assertTrue(reference.second < stackedRef.second, "the reference rose: $reference vs $stackedRef")
        assertTrue(reference.first > stackedRef.first, "and moved right: $reference vs $stackedRef")
        // The verse stays where a lone stacked element goes, rather than being pushed by a
        // reference that is no longer in the column with it.
        val verse = place(1, moved, TEXT).second
        assertTrue(verse > BAND_H / 2f, "the verse still bottom-aligns in the band: $verse")
    }

    @Test
    fun `nothing positioned leaves the column exactly where it was`() {
        // The path every existing document takes, which the split must not disturb.
        val a = place(1, settings(1), TEXT)
        val b = place(1, settings(1), TEXT)
        assertEquals(a, b, "the unpositioned band must be deterministic")
        assertTrue(a.second > BAND_H / 2f, "a band's single column sits at the bottom: ${a.second}")
    }

    // ── The side-by-side pair: two translations ─────────────────────────────────────────────────

    @Test
    fun `a positioned verse moves within its own half of the pair`() {
        val stacked = place(2, settings(2), TEXT).second
        val moved = place(2, settings(2, textOffset = TOP_LEFT), TEXT).second
        assertTrue(moved < stacked, "it should have risen: $moved vs $stacked")
    }

    @Test
    fun `the second half is positioned inside its own cell, not the whole band`() {
        val primary = place(2, settings(2, textOffset = TOP_LEFT), TEXT).first
        val secondary = place(2, settings(2, textOffset = TOP_LEFT), TEXT_RU).first
        // Both go flush left *of their own cell*, so they land a cell apart rather than on top of
        // each other. The secondary arriving at the primary's x is what applying the offset against
        // the whole band would look like.
        assertTrue(
            secondary - primary > BAND_W / 4f,
            "the halves must stay a cell apart: $secondary vs $primary",
        )
        // And that cell is the one it already occupied: flush left of its own half is exactly where
        // the stacked, width-filling version of it starts.
        assertEquals(place(2, settings(2), TEXT_RU).first, secondary, "it left its cell entirely")
    }

    @Test
    fun `an unpositioned pair still sits at the bottom of both halves`() {
        val primary = place(2, settings(2), TEXT)
        val secondary = place(2, settings(2), TEXT_RU)
        assertTrue(primary.second > BAND_H / 2f, "the left half bottom-aligns: ${primary.second}")
        assertTrue(secondary.second > BAND_H / 2f, "the right half bottom-aligns: ${secondary.second}")
        assertTrue(secondary.first > primary.first, "and they are side by side, not stacked")
    }

    private companion object {
        const val KJV = "kjv.spb"
        const val BAND_W = 1280
        const val BAND_H = 360
        const val TYPE_SIZE = 20

        val TRANSLATIONS = listOf(KJV to "KJV", "rst.spb" to "RST")

        const val TEXT = "For God so loved the world."
        const val TEXT_RU = "Ибо так возлюбил Бог мир."
        const val REFERENCE = "John 3:16"

        val TOP_LEFT = ElementOffset(xPercent = 0, yPercent = 0)
        val TOP_RIGHT = ElementOffset(xPercent = 100, yPercent = 0)
        val BOTTOM_RIGHT = ElementOffset(xPercent = 100, yPercent = 100)
    }
}
