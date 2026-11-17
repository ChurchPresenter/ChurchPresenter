@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ScrollWheel
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.core.models.songs.SongField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The grid's three parts: Number and Title frozen on the left, the row's buttons on the right, and
 * only the columns between them scrolling sideways — so a song's title and its buttons are on
 * screen together — and the tint on the row under the pointer.
 */
class TableLineTest {

    @Test
    fun `Number and Title are frozen and the rest scroll, in the grid's order`() {
        val (frozen, scrolling) = splitFrozen(
            listOf(SongField.NUMBER, SongField.TITLE, SongField.SECONDARY_TITLE, SongField.SONGBOOK, SongField.CCLI),
        )
        assertEquals(listOf(SongField.NUMBER, SongField.TITLE), frozen)
        assertEquals(listOf(SongField.SECONDARY_TITLE, SongField.SONGBOOK, SongField.CCLI), scrolling)
    }

    @Test
    fun `with Number hidden only the Title is frozen`() {
        val (frozen, scrolling) = splitFrozen(listOf(SongField.TITLE, SongField.AUTHOR))
        assertEquals(listOf(SongField.TITLE), frozen)
        assertEquals(listOf(SongField.AUTHOR), scrolling)
    }

    @Test
    fun `a row's buttons are on screen without scrolling, at the window's opening width`() =
        withLibrary(songEditor = {}) { _ ->
            val window = onRoot().fetchSemanticsNode().boundsInRoot
            listOf("Edit song", "Delete song").forEach { button ->
                val bounds = onAllNodesWithContentDescription(button).fetchSemanticsNodes()[0].boundsInRoot
                assertTrue(bounds.right <= window.right && bounds.left >= window.left, "$button is inside the window")
            }
        }

    @Test
    fun `scrolling sideways moves the middle columns and leaves the title and buttons where they were`() =
        withLibrary(songEditor = {}) { _ ->
            val title = textBounds("Amazing Grace")
            val author = textBounds("John Newton")
            val edit = onAllNodesWithContentDescription("Edit song").fetchSemanticsNodes()[0].boundsInRoot

            onAllNodesWithText("John Newton")[0].performMouseInput {
                moveTo(center)
                scroll(SCROLL_NOTCHES, ScrollWheel.Horizontal)
            }
            waitForIdle()

            assertTrue(textBounds("John Newton").left < author.left, "the author column moved left")
            assertEquals(title, textBounds("Amazing Grace"), "the title stayed put")
            assertEquals(
                edit,
                onAllNodesWithContentDescription("Edit song").fetchSemanticsNodes()[0].boundsInRoot,
                "and so did the row's Edit button",
            )
        }

    @Test
    fun `the row under the pointer is tinted, and only while the pointer is on it`() = withLibrary { _ ->
        val before = rowPixel()

        onAllNodesWithTag(SONG_ROW_TAG)[0].performMouseInput { moveTo(center) }
        waitForIdle()
        val hovered = rowPixel()

        onAllNodesWithTag(SONG_ROW_TAG)[0].performMouseInput { exit() }
        waitForIdle()

        assertNotEquals(before, hovered, "hovering changes how the row is drawn")
        assertEquals(before, rowPixel(), "and leaving it changes it back")
    }

    @Test
    fun `hovering one row leaves its neighbour as it was`() = withLibrary { _ ->
        val neighbour = rowPixel(index = 1)

        onAllNodesWithTag(SONG_ROW_TAG)[0].performMouseInput { moveTo(center) }
        waitForIdle()

        assertEquals(neighbour, rowPixel(index = 1))
    }

    private fun ComposeUiTest.textBounds(text: String): Rect =
        onAllNodesWithText(text).fetchSemanticsNodes()[0].boundsInRoot

    /**
     * A pixel of row [index]'s own background: just right of the ticked-row stripe, above the
     * checkbox, where nothing is drawn over the row.
     */
    private fun ComposeUiTest.rowPixel(index: Int = 0) =
        onAllNodesWithTag(SONG_ROW_TAG)[index].captureToImage().toPixelMap()[STRIPE_CLEAR_PX, 1]

    private companion object {
        const val SCROLL_NOTCHES = 3f
        const val STRIPE_CLEAR_PX = 4
    }
}
