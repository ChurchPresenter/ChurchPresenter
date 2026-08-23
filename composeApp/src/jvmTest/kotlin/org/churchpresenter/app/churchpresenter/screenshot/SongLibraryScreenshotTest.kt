@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.songlibrary.ui.SongLibraryApp
import java.nio.file.Files
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import org.churchpresenter.ui.screenshot.RENDER_TIMEOUT_MS
import org.churchpresenter.ui.screenshot.captureTo
import org.churchpresenter.ui.screenshot.stackedThemes

/**
 * The Song Library Manager window, in every state it can be in, in both themes.
 *
 * Shot through `SongLibraryApp` itself — the same entry point the app's Help menu opens — against a
 * real temp folder of `.song` files. Nothing here reaches past that entry point: each state is
 * arrived at by clicking, exactly as a person would, so an image that is wrong means the window is
 * wrong rather than the fixture. The grid is laid out in the test window rather than the 1420x880
 * the real one opens at, so the columns past the right edge are scrolled to, as they are in a
 * narrow window.
 *
 * The grid, both menus and all four dialogs are covered. Two things deliberately are not:
 * - **The editor a row opens belongs to the host.** Inside the app that is its own Edit Song
 *   dialog, covered by `EditSongDialogScreenshotTest`; with no host editor the row shows no Edit
 *   button, so there is no state of this window to shoot.
 * - **A save in flight.** `SongLibraryState.isWriting` turns the footer buttons off while the
 *   folder is written; holding a write open long enough to photograph it would mean a dispatcher
 *   parked mid-save, and the state is one frame of the footer rather than a state of the window.
 *
 * The state *while the folder is being read* is covered, and is only reachable because
 * [SongLibraryApp] takes the dispatcher it reads on: [Gated] holds the load until the frame has
 * been taken, so the skeleton is captured deterministically rather than by racing a disk.
 */
class SongLibraryScreenshotTest {

    // ── The grid ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the library as it opens`() = shoot("library")

    /** Only reachable because the load can be held: the grid is in it for as long as the disk takes. */
    @Test
    fun `the skeleton shown while the folder is being read`() = shoot("loading", hold = true)

    @Test
    fun `a folder with no songs in it`() = shoot("empty", songs = emptyList())

    @Test
    fun `a search that matches nothing`() = shoot("no_matches") {
        onNode(hasSetTextAction()).performTextInput("bagpipes")
        waitForIdle()
    }

    @Test
    fun `rows ticked, which brings up the bulk bar`() = shoot("selection") { tickRows(2) }

    @Test
    fun `an edited cell, which makes the footer dirty`() = shoot("unsaved") {
        onNodeWithText("John Newton").performClick()
        // The focused one: a cell being typed in is the second text field on screen, beside the
        // search box that is always there.
        val cell = onNode(hasSetTextAction() and isFocused())
        cell.performTextReplacement("Wesley")
        cell.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
    }

    @Test
    fun `columns turned off, which narrows the grid`() = shoot("columns_hidden") {
        onNodeWithText(COLUMNS).performClick()
        // Named, not indexed — and none of these three collides with its own column header, which
        // the menu draws in upper case beside it. "CCLI" is upper case either way and matches both.
        listOf("Number", "Composer", "Tune").forEach { onNodeWithText(it).performClick() }
        dismissPopup()
    }

    // ── The two menus ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `the song book filter, open`() = shoot("songbook_menu", rootIndex = 1, trim = true) {
        onNodeWithText(ALL_SONG_BOOKS).performClick()
        waitForIdle()
    }

    @Test
    fun `the columns menu, open`() = shoot("columns_menu", rootIndex = 1, trim = true) {
        onNodeWithText(COLUMNS).performClick()
        waitForIdle()
    }

    // ── The dialogs ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the new song book dialog`() = shoot("new_song_book", rootIndex = 1, trim = true) {
        onNodeWithText(ALL_SONG_BOOKS).performClick()
        onNodeWithText("New Song Book…").performClick()
        waitForIdle()
    }

    /** With a selection behind it, so the "file the selected songs under it" row is in the shot. */
    @Test
    fun `the new song book dialog offering to take the selection`() =
        shoot("new_song_book_with_selection", rootIndex = 1, trim = true) {
            tickRows(2)
            onNodeWithText(ALL_SONG_BOOKS).performClick()
            onNodeWithText("New Song Book…").performClick()
            waitForIdle()
        }

    @Test
    fun `the batch edit dialog`() = shoot("batch_edit", rootIndex = 1, trim = true) {
        tickRows(3)
        onNodeWithText("Batch Edit…").performClick()
        waitForIdle()
    }

    /** One song rather than several, which is the singular wording of the same dialog. */
    @Test
    fun `deleting a single song`() = shoot("delete_one", rootIndex = 1, trim = true) {
        tickRows(1)
        onAllNodesWithText("Delete")[0].performClick()
        waitForIdle()
    }

    @Test
    fun `deleting a whole selection`() = shoot("delete_selection", rootIndex = 1, trim = true) {
        tickRows(3)
        onAllNodesWithText("Delete")[0].performClick()
        waitForIdle()
    }

    // ── Getting there ───────────────────────────────────────────────────────────────────────────

    private fun shoot(
        name: String,
        songs: List<SongItem> = STOCK,
        rootIndex: Int = 0,
        trim: Boolean = false,
        hold: Boolean = false,
        drive: ComposeUiTest.() -> Unit = {},
    ) = stackedThemes(SECTION, name, trim) { mode, file ->
        val folder = Files.createTempDirectory("songlibrary-shot").toFile()
        try {
            val library = SongLibrary(folder)
            songs.forEach { library.writeNew(it) }
            val gate = Gated()
            runComposeUiTest {
                setContent {
                    ChurchPresenterTheme(themeMode = mode) {
                        Box(Modifier.fillMaxSize()) {
                            SongLibraryApp(
                                libraryFolder = folder,
                                onClose = {},
                                io = if (hold) gate else Dispatchers.IO,
                            )
                        }
                    }
                }
                if (!hold) {
                    val settled = if (songs.isEmpty()) EMPTY_LIBRARY else songs.first().title
                    waitUntil("the folder had been read", RENDER_TIMEOUT_MS) {
                        onAllNodesWithText(settled).fetchSemanticsNodes().isNotEmpty()
                    }
                    drive()
                    waitForIdle()
                }
                captureTo(file, rootIndex)
            }
            // Let the held load finish rather than leaving a coroutine parked past the test.
            gate.release()
        } finally {
            folder.deleteRecursively()
        }
    }

    /** Ticks [count] rows, skipping index 0 — the header's own select-all box. */
    private fun ComposeUiTest.tickRows(count: Int) {
        repeat(count) { row -> onAllNodes(isToggleable())[row + 1].performClick() }
        waitForIdle()
    }

    /** Closes an open menu by clicking its anchor again, so the grid behind it is what is shot. */
    private fun ComposeUiTest.dismissPopup() {
        onAllNodesWithText(COLUMNS)[0].performClick()
        waitForIdle()
    }

    /**
     * A dispatcher that queues instead of running, so the work handed to it never finishes.
     *
     * The window shows its skeleton until the folder has been read, which on a real disk is the few
     * hundred milliseconds nobody can capture reliably. Held here, that state lasts as long as the
     * shot needs and ends when [release] is called.
     */
    private class Gated : CoroutineDispatcher() {
        private val queued = mutableListOf<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            synchronized(queued) { queued += block }
        }

        fun release() {
            val pending = synchronized(queued) { queued.toList().also { queued.clear() } }
            pending.forEach { it.run() }
        }
    }

    private companion object {
        const val SECTION = "songLibrary"
        // The window's own English strings, which is what a test run renders.
        const val COLUMNS = "Columns"
        const val ALL_SONG_BOOKS = "All Song Books"
        const val EMPTY_LIBRARY = "This library has no songs yet"

        /**
         * A library with the shape a real one has: books of different sizes, a nested book, songs
         * filed loose in the root, and fields that are filled in for some songs and not others —
         * so the grid is shot with both its populated and its empty cells.
         */
        val STOCK = listOf(
            song(
                "001", "Amazing Grace", "Hymnal",
                author = "John Newton", composer = "Excell", tune = "NEW BRITAIN", ccli = "22025",
            ),
            song("002", "Be Thou My Vision", "Hymnal", author = "Dallán Forgaill", tune = "SLANE"),
            song(
                "010", "Holy, Holy, Holy", "Hymnal",
                author = "Reginald Heber", composer = "John Dykes", tune = "NICAEA",
            ),
            song("011", "It Is Well With My Soul", "Hymnal", author = "Horatio Spafford"),
            song("001", "Here I Am to Worship", "Chorus Book", author = "Tim Hughes", ccli = "3266032"),
            song(
                "002", "10,000 Reasons", "Chorus Book",
                author = "Matt Redman", composer = "Jonas Myrin", ccli = "6016351",
            ),
            song(
                "001", "Silent Night", "Christmas/Carols",
                author = "Joseph Mohr", composer = "Franz Gruber",
            ),
            song("002", "O Come All Ye Faithful", "Christmas/Carols", author = "John Wade"),
            song("", "Doxology", ""),
        )

        @Suppress("LongParameterList")
        fun song(
            number: String,
            title: String,
            songbook: String,
            author: String = "",
            composer: String = "",
            tune: String = "",
            ccli: String = "",
        ) = SongItem(
            number = number,
            title = title,
            songbook = songbook,
            author = author,
            composer = composer,
            tune = tune,
            ccliNumber = ccli,
            lyrics = listOf("[Verse 1]", "A line of the song"),
        )
    }
}
