@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.bible.SelectedVerse
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.BibleSettings
import org.churchpresenter.settings.BibleTranslationSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A bordered backdrop keeping all four of its sides.
 *
 * The bug: `drawTextBackdrop` draws its plate outside the text's own box on purpose, and its comment
 * names the condition that makes that safe -- *only an ancestor that clips would cut it off*. The
 * presenters clip, and their text fills the clipped box, so on the Bible's **default left-aligned**
 * text the box lost its left edge, and a line wide enough to reach both edges lost the box entirely
 * and drew as two horizontal rules.
 *
 * Asserted by counting the vertical strokes on a row through the middle of the box, because that is
 * the one place only the two sides can be: the horizontal rules survive the clipping, so a test that
 * merely looked for border-coloured pixels passed against the bug.
 */
class BibleBackdropBorderRenderTest {

    private val border = Color(0xFFFFD54F)

    private fun settings(alignment: String) = AppSettings(
        bibleSettings = BibleSettings(primaryBible = KJV).withTranslations(
            listOf(
                BibleTranslationSettings(
                    fileName = KJV,
                    textHorizontalAlignment = alignment,
                    textBackdrop = TextBackdrop(
                        border = true,
                        borderColor = "#FFD54F",
                        borderWidth = 6,
                        borderPadding = 18,
                        borderRadius = 12,
                    ),
                ),
            ),
        ),
    )

    private fun verse(text: String) = SelectedVerse(
        translationFileName = KJV,
        bibleAbbreviation = "KJV",
        bibleName = "KJV",
        bookName = "John",
        chapter = 3,
        verseNumber = 16,
        verseText = text,
        verseRange = "",
    )

    /** How many separate runs of border colour sit on the row [fraction] of the way down the box. */
    private fun verticalStrokesAcross(alignment: String, text: String): Int {
        var runs = 0
        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.testTag("out").size(1280.dp, 720.dp).background(Color.Black)) {
                    BiblePresenter(selectedVerses = listOf(verse(text)), appSettings = settings(alignment))
                }
            }
            waitForIdle()
            val map = onNodeWithTag("out").captureToImage().toPixelMap()
            // The row with the most border pixels is a horizontal rule; the row we want is between
            // the two rules, where only the left and right strokes are painted. Take the row
            // halfway between the topmost and bottommost border pixel.
            val rows = (0 until map.height).filter { y ->
                (0 until map.width).any { x -> map[x, y].isBorder() }
            }
            if (rows.isEmpty()) return@runComposeUiTest
            val middle = (rows.first() + rows.last()) / 2
            var inRun = false
            for (x in 0 until map.width) {
                val hit = map[x, middle].isBorder()
                if (hit && !inRun) runs++
                inRun = hit
            }
        }
        return runs
    }

    private fun Color.isBorder(): Boolean =
        kotlin.math.abs(red - border.red) < TOLERANCE &&
            kotlin.math.abs(green - border.green) < TOLERANCE &&
            kotlin.math.abs(blue - border.blue) < TOLERANCE &&
            alpha > 0.5f

    @Test
    fun `left-aligned text keeps both sides of its box`() {
        // The default alignment, and the one the bug hit: the left stroke was clipped away.
        assertEquals(2, verticalStrokesAcross(Constants.LEFT, SHORT), "expected a left and a right stroke")
    }

    @Test
    fun `centred text keeps both sides of its box`() {
        assertEquals(2, verticalStrokesAcross(Constants.CENTER, SHORT))
    }

    @Test
    fun `a line wide enough to fill the frame still keeps both sides`() {
        // The worse half of the bug: wide text reached both edges and lost the whole box, leaving
        // two horizontal rules with nothing joining them.
        assertEquals(2, verticalStrokesAcross(Constants.LEFT, LONG), "a wide line must not lose its box")
    }

    private companion object {
        const val KJV = "kjv.spb"
        const val TOLERANCE = 0.08f
        const val SHORT = "For God so loved the world."
        const val LONG = "For God so loved the world, that he gave his only begotten Son, " +
            "that whosoever believeth in him should not perish, but have everlasting life."
    }
}
