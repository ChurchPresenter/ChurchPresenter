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
import org.churchpresenter.app.churchpresenter.data.StrongsEntry
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.settings.DictionarySettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The dictionary card's Strong's badge keeping the top and bottom of its box.
 *
 * The horizontal twin of [BibleBackdropBorderRenderTest], and the reason it is a separate suite: the
 * dictionary card clips *vertically* and not horizontally. Its `.verticalScroll` is the innermost
 * modifier, so the viewport is the content's own box on the vertical axis, while
 * `clipScrollableContainer` inflates the clip sideways — which leaves the plate's **top and bottom**
 * as the edges with nowhere to go, the opposite pair from the Bible's.
 *
 * So this counts runs of border colour down a **column** through the middle of the box, where only
 * the top and bottom strokes can be. The two sides survive the clipping, so a test that looked for
 * border-coloured pixels — or counted across a row, as the Bible's does — would pass against the bug.
 */
class DictionaryBackdropBorderRenderTest {

    private val border = Color(0xFFFFD54F)

    private val settings = DictionarySettings(
        cardBackgroundColor = "#000000",
        referenceBackdrop = TextBackdrop(
            border = true,
            borderColor = "#FFD54F",
            borderWidth = 6,
            borderPadding = 18,
            borderRadius = 12,
        ),
    )

    /**
     * The badge alone: a blank transliteration, word and definition drop every other element, so the
     * only box in the picture is the one being measured.
     */
    private val entry = StrongsEntry(
        number = "H430",
        word = "",
        transliteration = "",
        pronunciation = "",
        definition = "",
    )

    /**
     * How many separate runs of border colour sit on the column through the middle of the box.
     *
     * Fails outright when the picture holds no border at all, rather than reporting zero strokes:
     * a backdrop that stopped being drawn is a different bug from one whose plate is being clipped,
     * and against this bug the count really is 0 — both horizontal strokes were lost, not one.
     */
    private fun horizontalStrokesDown(): Int {
        var runs = 0
        runComposeUiTest {
            setContent {
                Box(modifier = Modifier.testTag("out").size(1280.dp, 720.dp).background(Color.Black)) {
                    DictionaryPresenter(entry = entry, dictionarySettings = settings)
                }
            }
            waitForIdle()
            val map = onNodeWithTag("out").captureToImage().toPixelMap()
            val columns = (0 until map.width).filter { x ->
                (0 until map.height).any { y -> map[x, y].isBorder() }
            }
            assertTrue(columns.isNotEmpty(), "no border was drawn at all — the backdrop itself is gone")
            val middle = (columns.first() + columns.last()) / 2
            var inRun = false
            for (y in 0 until map.height) {
                val hit = map[middle, y].isBorder()
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
    fun `the badge keeps the top and bottom of its box`() {
        assertEquals(2, horizontalStrokesDown(), "expected a top and a bottom stroke")
    }

    private companion object {
        const val TOLERANCE = 0.08f
    }
}
