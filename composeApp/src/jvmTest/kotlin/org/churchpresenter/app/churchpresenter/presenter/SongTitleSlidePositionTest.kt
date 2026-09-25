@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withTitleSlideOffset
import org.churchpresenter.core.models.songs.LyricSection
import org.churchpresenter.core.models.text.TextOutline
import org.churchpresenter.settings.ElementOffset
import org.churchpresenter.settings.SongCreditStyle
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A positioned title-slide element leaving the column and being placed — #615's second item, drawn.
 *
 * The mapping is pinned by `SongTitleSlideOffsetTest`; what this adds is that the presenter reads it,
 * because a stored offset nothing draws by is exactly the shape of bug the Content Region report in
 * this same batch turned out to be.
 *
 * Asserted on where the text actually lands rather than on a screenshot: the point is a position, and
 * a position is a number.
 */
class SongTitleSlidePositionTest {

    private val section = LyricSection(
        type = "title_slide",
        title = TITLE,
        author = AUTHOR,
        lines = emptyList(),
    )

    // ── How a line is drawn, as distinct from where ─────────────────────────────────────────────
    //
    // The placement above is one half of `TitleSlideText`; the other is that a line can be stroked,
    // can carry the song number ahead of it as a span of its own, and is drawn white on a key
    // output. Each takes a different path through the same composable, and each was reachable only
    // through the real presenter.

    /** The slide with a number, which shares the title's row when the setting says so. */
    private fun numbered() = section.copy(songNumber = 427)

    private fun stroked() = small().copy(
        outlines = SongSettings().outlines.copy(
            title = TextOutline(enabled = true, width = 3, color = "#101820"),
        ),
    )

    @Test
    fun `a stroked title is drawn through the two-pass outline path`() {
        // An outlined line is a Box holding a stroke pass and a fill pass rather than one Text, so
        // the text is found twice. That it is found at all is the assertion: the stroke path builds
        // its own copy of the annotated string with every span painted the outline's colour, and a
        // mistake there throws rather than drawing differently.
        val box = boxOf(stroked(), TITLE)
        assertTrue(box.width > 0f, "the stroked title is drawn")
        assertTrue(box.top >= 0f, "and placed inside the slide")
    }

    @Test
    fun `the number shares the title's line when it is set to lead`() {
        // Drawn as a span ahead of the title inside the same paragraph, in the number's own style,
        // so a long title wraps under it as one line of text would.
        val leading = small().copy(titleSlideNumberBeforeTitle = true)
        val withNumber = boxOf(leading, TITLE, section = numbered()).width
        val without = boxOf(leading, TITLE).width
        assertTrue(
            withNumber >= without,
            "the number is drawn in the title's own line, so that line cannot be narrower: " +
                "$withNumber vs $without",
        )
    }

    @Test
    fun `the number takes a row of its own when it is not set to lead`() {
        val ownRow = small().copy(titleSlideNumberBeforeTitle = false)
        val numberTop = boxOf(ownRow, "427", section = numbered()).top
        val titleTop = boxOf(ownRow, TITLE, section = numbered()).top
        assertTrue(numberTop < titleTop, "its own row sits above the title: $numberTop vs $titleTop")
    }

    @Test
    fun `a key output draws the slide white whatever the styling says`() {
        // The key signal is a matte: every element goes white so the shape is what gets keyed, and
        // an outline goes white with it rather than cutting a dark edge out of the matte.
        val coloured = stroked().copy(titleColor = "#FFD54F")
        val box = boxOf(coloured, TITLE, isKey = true)
        assertTrue(box.width > 0f, "the key output still draws the slide")
    }

    /**
     * Small type deliberately. `Modifier.elementOffset` moves an element through the room the frame
     * has left over, so an element as wide as the frame cannot move on X at all — that is its stated
     * contract, not a bug, and at the stored 70sp default a title is wider than any frame a test can
     * afford to render. Shrunk here so both axes have room, which is what the assertions are about.
     */
    private fun small() = SongSettings(
        titleFontSize = TYPE_SIZE,
        titleSlideAuthor = SongCreditStyle(fontSize = TYPE_SIZE),
    )

    /** Where the node holding one line landed, and how wide it came out. */
    private data class Box2D(val left: Float, val top: Float, val width: Float)

    private fun boxOf(
        settings: SongSettings,
        text: String,
        section: LyricSection = this.section,
        isKey: Boolean = false,
    ): Box2D {
        var left = -1f
        var top = -1f
        var width = -1f
        runSkikoComposeUiTest(size = Size(FRAME.toFloat(), FRAME.toFloat()), density = Density(1f)) {
            setContent {
                Box(modifier = Modifier.size(FRAME.dp)) {
                    SongTitleSlideContent(
                        section = section,
                        settings = settings,
                        target = SongStyleTarget.FULL_SCREEN,
                        languages = listOf(0),
                        isKey = isKey,
                        scaleFactor = 1f,
                        contentAlignment = Alignment.Center,
                    )
                }
            }
            waitForIdle()
            // `onFirst`: a stroked line is a stroke pass and a fill pass over each other, so its
            // text is two nodes rather than one. They are the same box by construction.
            val node = onAllNodesWithText(text, substring = true).onFirst()
            node.assertIsDisplayed()
            val bounds = node.getBoundsInRoot()
            left = bounds.left.value
            top = bounds.top.value
            width = bounds.right.value - bounds.left.value
        }
        return Box2D(left, top, width)
    }

    @Test
    fun `an unpositioned title slide draws its lines in one centred column`() {
        val settings = small()
        val titleTop = boxOf(settings, TITLE).top
        val authorTop = boxOf(settings, AUTHOR).top
        assertTrue(titleTop < authorTop, "the title should sit above the author: $titleTop vs $authorTop")
    }

    @Test
    fun `positioning the author moves it out of the column`() {
        // Flush to the top-left, which nothing in a centred column reaches.
        val settings = small().withTitleSlideOffset(
            SongStyleElement.AUTHOR,
            SongStyleTarget.FULL_SCREEN,
            ElementOffset(xPercent = 0, yPercent = 0),
        )
        val stacked = boxOf(small(), AUTHOR)
        val moved = boxOf(settings, AUTHOR)
        assertTrue(moved.top < stacked.top, "it should have gone up: ${moved.top} vs ${stacked.top}")
        assertTrue(moved.top < 1f, "flush to the top of the frame")
        assertTrue(moved.left < 1f, "flush to the left of the frame")
        // Left is 0 either way -- a stacked line fills the width, so it starts at 0 too. What
        // distinguishes them is that a positioned line stops filling: without that it would have no
        // horizontal room and X would silently do nothing, which is what the 100% case below shows.
        assertEquals(FRAME.toFloat(), stacked.width, "a stacked line fills the frame")
        assertTrue(moved.width < stacked.width, "a positioned line is sized to itself: ${moved.width}")
    }

    @Test
    fun `positioning the title leaves the author where a lone credit would be`() {
        // The stack closes up over the line that left, which is the trade the cornered number makes.
        val settings = small().withTitleSlideOffset(
            SongStyleElement.TITLE,
            SongStyleTarget.FULL_SCREEN,
            ElementOffset(xPercent = 100, yPercent = 100),
        )
        val authorWithTitle = boxOf(small(), AUTHOR).top
        val authorAlone = boxOf(settings, AUTHOR).top
        assertTrue(
            authorAlone < authorWithTitle,
            "the author should have risen into the freed height: $authorAlone vs $authorWithTitle",
        )
    }

    @Test
    fun `a positioned line is still drawn`() {
        // The float is an easy thing to lose: filter the line out of the column and forget to draw it.
        val settings = small().withTitleSlideOffset(
            SongStyleElement.TITLE,
            SongStyleTarget.FULL_SCREEN,
            ElementOffset(xPercent = 100, yPercent = 100),
        )
        val box = boxOf(settings, TITLE)
        val left = box.left
        val top = box.top
        assertTrue(top > FRAME / 2f, "flush to the bottom half: $top")
        assertTrue(left > 0f, "and moved off the left edge: $left")
    }

    private companion object {
        const val FRAME = 400
        const val TYPE_SIZE = 24
        const val TITLE = "Amazing Grace"
        const val AUTHOR = "John Newton"
    }
}
