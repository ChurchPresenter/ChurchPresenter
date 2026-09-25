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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleElement
import org.churchpresenter.app.churchpresenter.dialogs.tabs.SongStyleTarget
import org.churchpresenter.app.churchpresenter.dialogs.tabs.withTitleSlideOffset
import org.churchpresenter.core.models.songs.LyricSection
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

    private fun boxOf(settings: SongSettings, text: String): Box2D {
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
                        isKey = false,
                        scaleFactor = 1f,
                        contentAlignment = Alignment.Center,
                    )
                }
            }
            waitForIdle()
            val node = onNodeWithText(text, substring = true)
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
