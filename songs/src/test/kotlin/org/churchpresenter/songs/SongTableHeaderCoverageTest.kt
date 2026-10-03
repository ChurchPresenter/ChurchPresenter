@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.songs

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.rightClick
import org.churchpresenter.settings.SongColumnId
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SongTableHeaderCoverageTest {

    private val optional = setOf(
        SongColumnId.SONGBOOK, SongColumnId.TUNE, SongColumnId.PLAY_COUNT, SongColumnId.AUTHOR, SongColumnId.COMPOSER,
    )

    private fun showing(colId: String) = optional - colId

    private fun ComposeUiTest.header(label: String) = onAllNodes(hasText(label))[0]

    private fun ComposeUiTest.clickHeader(label: String) {
        header(label).performClick()
        waitForIdle()
    }

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

    private fun ComposeUiTest.clickAt(x: Float, y: Float) {
        onRoot().performMouseInput { click(Offset(x, y)) }
        waitForIdle()
    }

    private fun ComposeUiTest.favoritesHeaderX(): Float =
        onAllNodes(hasContentDescription("Add to favorites"))[0].fetchSemanticsNode().boundsInRoot.center.x

    private fun ComposeUiTest.headerY(): Float = header("Title").fetchSemanticsNode().boundsInRoot.center.y

    @Test
    fun `clicking Song Book sorts by songbook`() = songsTab(hiddenCols = showing(SongColumnId.SONGBOOK)) { vm, _ ->
        clickHeader("Song Book")

        assertEquals(Constants.SORT_SONGBOOK, vm.sortColumn.value)
        assertTrue(vm.sortAscending.value)
    }

    @Test
    fun `clicking Tune sorts by tune`() = songsTab(hiddenCols = showing(SongColumnId.TUNE)) { vm, _ ->
        clickHeader("Tune")

        assertEquals(Constants.SORT_TUNE, vm.sortColumn.value)
    }

    @Test
    fun `clicking Plays sorts by play count`() = songsTab(hiddenCols = showing(SongColumnId.PLAY_COUNT)) { vm, _ ->
        clickHeader("Plays")

        assertEquals(Constants.SORT_PLAY_COUNT, vm.sortColumn.value)
    }

    @Test
    fun `clicking Author sorts by author`() = songsTab(hiddenCols = showing(SongColumnId.AUTHOR)) { vm, _ ->
        clickHeader("Author")

        assertEquals(Constants.SORT_AUTHOR, vm.sortColumn.value)
    }

    @Test
    fun `clicking Composer sorts by composer`() = songsTab(hiddenCols = showing(SongColumnId.COMPOSER)) { vm, _ ->
        clickHeader("Composer")

        assertEquals(Constants.SORT_COMPOSER, vm.sortColumn.value)
        assertTrue(vm.sortAscending.value)
    }

    @Test
    fun `clicking Composer twice sorts it descending`() =
        songsTab(hiddenCols = showing(SongColumnId.COMPOSER)) { vm, _ ->
            clickHeader("Composer")
            clickHeader("Composer")

            assertEquals(Constants.SORT_COMPOSER, vm.sortColumn.value)
            assertFalse(vm.sortAscending.value)
        }

    @Test
    fun `sorting by author orders the rows by author`() = songsTab(hiddenCols = showing(SongColumnId.AUTHOR)) { _, _ ->
        clickHeader("Author")

        assertEquals(
            listOf("Amazing Love", "Be Thou My Vision", "Amazing Grace"),
            listedTitles().filter { it != "How Great Thou Art" },
            "Charles Wesley, Dallan Forgaill, John Newton",
        )
    }

    @Test
    fun `the favorites header sorts by favorites`() = songsTab(hiddenCols = optional) { vm, _ ->
        clickAt(favoritesHeaderX(), headerY())

        assertEquals(Constants.SORT_FAVORITES, vm.sortColumn.value)
        assertTrue(vm.sortAscending.value)
    }

    @Test
    fun `the favorites header puts starred songs first, then last`() = songsTab(hiddenCols = optional) { vm, _ ->
        vm.toggleFavorite(vm.filteredSongItems.value.first { it.title == "Be Thou My Vision" }.songId)
        waitForIdle()
        val x = favoritesHeaderX()
        val y = headerY()

        clickAt(x, y)
        assertEquals("Be Thou My Vision", listedTitles().first(), "ascending puts the starred song first")
        clickAt(x, y)

        assertFalse(vm.sortAscending.value)
        assertEquals("Be Thou My Vision", listedTitles().last())
    }

    @Test
    fun `the add-to-schedule header does not sort`() = songsTab(hiddenCols = optional) { vm, _ ->
        val star = onAllNodes(hasContentDescription("Add to favorites"))[0].fetchSemanticsNode().boundsInRoot
        val add = onAllNodes(hasContentDescription("Add to Schedule")).fetchSemanticsNodes()
            .map { it.boundsInRoot }
            .first { it.center.y == star.center.y && it.center.x < star.center.x }

        clickAt(add.center.x, headerY())

        assertEquals("", vm.sortColumn.value)
    }

    private fun assertResizeWrites(colId: String, label: String, read: (SongSettings) -> Int) =
        songsTab(hiddenCols = showing(colId)) { _, reports ->
            dragDivider(label, dx = 60f)

            val saved = assertNotNull(reports.settingsAfterChange?.songSettings, "nothing saved for $label")
            assertTrue(read(saved) > read(SongSettings()), "$label: ${read(SongSettings())} -> ${read(saved)}")
        }

    @Test
    fun `resizing Tune writes the tune width`() =
        assertResizeWrites(SongColumnId.TUNE, "Tune") { it.colWidthTune }

    @Test
    fun `resizing Plays writes the play count width`() =
        assertResizeWrites(SongColumnId.PLAY_COUNT, "Plays") { it.colWidthPlayCount }

    @Test
    fun `resizing Author writes the author width`() =
        assertResizeWrites(SongColumnId.AUTHOR, "Author") { it.colWidthAuthor }

    @Test
    fun `resizing Composer writes the composer width`() =
        assertResizeWrites(SongColumnId.COMPOSER, "Composer") { it.colWidthComposer }

    @Test
    fun `a resize saves the column order and hidden set alongside the widths`() =
        songsTab(hiddenCols = showing(SongColumnId.TUNE)) { _, reports ->
            dragDivider("Tune", dx = 60f)

            val saved = assertNotNull(reports.settingsAfterChange)
            assertEquals(showing(SongColumnId.TUNE), saved.songHiddenCols)
            assertTrue(SongColumnId.TUNE in saved.songColOrder)
        }

    @Test
    fun `unhiding from the right-click menu brings a column back and saves it`() = songsTab { _, reports ->
        assertTrue(onAllNodesWithText("Tune").fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty())
        header("Title").performMouseInput { rightClick() }
        waitForIdle()

        val items = onAllNodesWithText("Tune")
        items[items.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()

        assertEquals(2, onAllNodesWithText("Tune").fetchSemanticsNodes().size, "header plus the menu item")
        assertFalse(reports.settingsAfterChange?.songHiddenCols?.contains(SongColumnId.TUNE) ?: true)
    }
}
