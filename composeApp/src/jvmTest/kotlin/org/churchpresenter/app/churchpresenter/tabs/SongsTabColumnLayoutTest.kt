@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import org.churchpresenter.settings.SongSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Rearranging the song table's columns by dragging its headers — reordering by dragging a header
 * sideways, and resizing by dragging the divider between two of them.
 *
 * Both persist through `onSettingsChange`, and that is the point: an operator sets the table up once
 * to suit their screen and expects it back next Sunday. Neither is reachable from a menu, so
 * dragging is the only way either happens.
 *
 * `SongColumnsTest` covers `moveColumn` and `computeNewIdx` as arithmetic; this covers the gesture
 * that feeds them and the settings write that follows, which is where a drag can be wired to the
 * wrong column or drop its save.
 *
 * The header cells are addressed by their labels. The **resize divider is not addressable** — it is a
 * bare 6dp `Box` with no semantics — so it is reached positionally, just past the right edge of the
 * header it belongs to. That is stated rather than hidden: if the header layout changes, these two
 * resize tests are the ones to re-check.
 *
 * Only the columns shown by default — number, title, songbook — have a resize test. Tune, Plays,
 * Author and Composer lay out past the right edge of the test window, so their dividers cannot be
 * pressed: the header node exists, the drag lands outside the window and nothing moves. Their arms
 * of `setColWidth` are therefore uncovered.
 *
 * See `SongsTabTestSupport.kt` for the harness.
 */
class SongsTabColumnLayoutTest {

    /** No hidden columns, so every header is on screen and orderable. */
    private val allShown = emptySet<String>()

    private fun ComposeUiTest.header(label: String) = onAllNodes(hasText(label))[0]

    /** Drags a header sideways by [dx] pixels, which is the reorder gesture. */
    private fun ComposeUiTest.dragHeader(label: String, dx: Float) {
        val bounds = header(label).fetchSemanticsNode().boundsInRoot
        val y = bounds.center.y
        val startX = bounds.center.x
        onRoot().performMouseInput {
            moveTo(Offset(startX, y))
            press()
            moveTo(Offset(startX + dx / 2f, y))
            moveTo(Offset(startX + dx, y))
            release()
        }
        waitForIdle()
    }

    /**
     * Drags the divider on the right-hand edge of [label]'s header by [dx] pixels.
     *
     * The divider has no semantics of its own, so it is found by geometry: it is the 6dp strip
     * immediately after the header cell, and the header's own label ends a little before it.
     */
    private fun ComposeUiTest.dragDivider(label: String, dx: Float) {
        val bounds = header(label).fetchSemanticsNode().boundsInRoot
        val y = bounds.center.y
        val x = bounds.right + 3f
        onRoot().performMouseInput {
            moveTo(Offset(x, y))
            press()
            moveTo(Offset(x + dx / 2f, y))
            moveTo(Offset(x + dx, y))
            release()
        }
        waitForIdle()
    }

    // ── Reordering ──────────────────────────────────────────────────────────────

    @Test
    fun `dragging a header far enough sideways reorders the columns and saves it`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            val before = header(Col.TITLE).fetchSemanticsNode().boundsInRoot

            // Dragged *left*, past Number. The table's default order is number-then-title, so
            // asserting that title ends up after number would have been true before the drag too —
            // the direction is chosen so the assertion can only pass if something moved.
            dragHeader(Col.TITLE, dx = -(before.width + 120f))

            val order = reports.settingsAfterChange?.songColOrder
            assertNotNull(order, "a reorder must be written to settings to survive a restart")
            assertTrue(
                order.indexOf("title") < order.indexOf("number"),
                "title must have moved ahead of number, which it does not start as: $order",
            )
        }

    @Test
    fun `a small nudge does not reorder anything`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            dragHeader(Col.TITLE, dx = 3f)

            // Brushing a header while reaching for the sort must not rearrange the table.
            assertEquals(null, reports.settingsAfterChange?.songColOrder, "a nudge is not a reorder")
        }

    @Test
    fun `reordering leaves every column present`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            val before = header(Col.TITLE).fetchSemanticsNode().boundsInRoot
            dragHeader(Col.TITLE, dx = -(before.width + 120f))

            val order = reports.settingsAfterChange?.songColOrder
            assertNotNull(order)
            // A reorder that dropped or duplicated a column would leave the table missing one.
            assertEquals(order.size, order.distinct().size, "no column may be duplicated: $order")
            assertTrue("title" in order && "number" in order, "nor lost: $order")
        }

    // ── Resizing ────────────────────────────────────────────────────────────────

    @Test
    fun `dragging the divider right widens the column and saves the width`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            val before = header(Col.TITLE).fetchSemanticsNode().boundsInRoot.width

            dragDivider(Col.TITLE, dx = 80f)

            val saved = reports.settingsAfterChange?.songSettings?.colWidthTitle
            assertNotNull(saved, "a resize must be written to settings on drop")
            val after = header(Col.TITLE).fetchSemanticsNode().boundsInRoot.width
            assertTrue(after > before, "the column must actually get wider ($before -> $after)")
        }

    /**
     * Drags [label]'s divider in a composition of its own and asserts the width [read]s back wider.
     *
     * One composition per column on purpose: dragging several in the same table pushes the headers
     * after them along, so the next divider is no longer where it was measured.
     */
    private fun assertResizeWritesOwnField(label: String, read: (SongSettings) -> Int) =
        songsTab(hiddenCols = allShown) { _, reports ->
            dragDivider(label, dx = 60f)

            val saved = assertNotNull(
                reports.settingsAfterChange?.songSettings,
                "dragging \"$label\" wrote nothing to settings",
            )
            assertTrue(
                read(saved) > read(SongSettings()),
                "\"$label\" did not widen its own field: ${read(SongSettings())} -> ${read(saved)}",
            )
        }

    // Each header routes to its own settings field. Wired to the wrong one, a resize would silently
    // move a different column — invisible until the table is reopened next Sunday.

    @Test
    fun `resizing Number writes the number width`() =
        assertResizeWritesOwnField(Col.NUMBER) { it.colWidthNumber }

    @Test
    fun `resizing Song Book writes the songbook width`() =
        assertResizeWritesOwnField(Col.SONG_BOOK) { it.colWidthSongbook }

    @Test
    fun `a column cannot be dragged narrower than its minimum`() =
        songsTab(hiddenCols = allShown) { _, reports ->
            // Far more than the column's width, so without a floor it would go to zero or negative
            // and the header would vanish from the table entirely.
            dragDivider(Col.TITLE, dx = -4000f)

            val saved = reports.settingsAfterChange?.songSettings?.colWidthTitle
            assertNotNull(saved)
            assertTrue(saved >= 60, "title's floor is 60dp, was $saved")
            assertTrue(
                header(Col.TITLE).fetchSemanticsNode().boundsInRoot.width > 0f,
                "the header must survive being squeezed",
            )
        }

    private object Col {
        const val NUMBER = "Number"
        const val TITLE = "Title"
        const val SONG_BOOK = "Song Book"
        const val TUNE = "Tune"
        const val PLAY_COUNT = "Plays"
        const val AUTHOR = "Author"
        const val COMPOSER = "Composer"
    }
}
