@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.songlibrary.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.songs.SongItem
import org.churchpresenter.core.models.songs.SongLibrary
import kotlinx.coroutines.Dispatchers
import org.churchpresenter.theme.AppThemeWrapper
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.nio.file.Files

/**
 * A real library folder on disk, and the window opened on it.
 *
 * The window reads and writes `.song` files, so the tests do too — a fake in front of the folder
 * would leave the half of this module that matters (a save that renames a file, a delete that
 * removes one) asserted against something that never touches a disk.
 */
internal fun withLibrary(
    songs: List<SongItem> = STOCK,
    onClose: (() -> Unit)? = {},
    songEditor: (@Composable (SongEditorRequest) -> Unit)? = null,
    body: ComposeUiTest.(folder: File) -> Unit,
) {
    val folder = Files.createTempDirectory("songlibrary-ui").toFile()
    try {
        val library = SongLibrary(folder)
        songs.forEach { library.writeNew(it) }
        runComposeUiTest {
            setContent {
                AppThemeWrapper(theme = ThemeMode.LIGHT) {
                    // Unconfined so the folder read finishes inline: the window is under test, not
                    // the thread it reads on.
                    SongLibraryApp(
                        libraryFolder = folder,
                        onClose = onClose,
                        songEditor = songEditor,
                        io = Dispatchers.Unconfined,
                    )
                }
            }
            if (songs.isNotEmpty()) awaitRow(songs.first().title)
            body(folder)
        }
    } finally {
        folder.deleteRecursively()
    }
}

/** The window shows a skeleton until the folder has been read; this waits for the real grid. */
internal fun ComposeUiTest.awaitRow(title: String) {
    waitUntil("the row '$title' appeared", 10_000L) {
        onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty()
    }
}

internal fun ComposeUiTest.rowTitles(of: List<SongItem> = STOCK): List<String> =
    of.map { it.title }.filter { onAllNodesWithText(it).fetchSemanticsNodes().isNotEmpty() }

/**
 * Turns every column off but the title, which is what brings the row's own buttons into a window
 * this narrow — the actions column sits past the right edge of a full-width grid.
 */
internal fun ComposeUiTest.narrowToTitleOnly() {
    click(Text.COLUMNS)
    listOf("Number", "Secondary Title", "Song Book", "Author", "Composer", "Tune", "CCLI")
        .forEach { clickLast(it) }
    // Dismissed by clicking the window's own title — somewhere with nothing on it. Not by pressing
    // the Columns button again, which lands on the panel's "Show all" row and puts every column
    // back, and not with Escape, which leaves the next click to be eaten by the closing popup.
    click("Song Library Manager")
}

/** Ticks row [index] of the grid; index 0 is the header's own select-all box. */
internal fun ComposeUiTest.tickRow(index: Int) {
    onAllNodes(isToggleable())[index + 1].performClick()
    waitForIdle()
}

internal fun ComposeUiTest.selectAll() {
    onAllNodes(isToggleable())[0].performClick()
    waitForIdle()
}

/** Clicks the cell showing [value] and types [replacement] into it, committing with Enter. */
internal fun ComposeUiTest.retypeCell(value: String, replacement: String) {
    onNodeWithText(value).performClick()
    typeIntoOpenCell(replacement)
}

/** The same, for a value that appears in more than one row — the first row's cell is taken. */
internal fun ComposeUiTest.retypeFirstCell(value: String, replacement: String) {
    onAllNodesWithText(value)[0].performClick()
    typeIntoOpenCell(replacement)
}

private fun ComposeUiTest.typeIntoOpenCell(replacement: String) {
    val field = onNode(hasSetTextAction() and isFocused())
    field.performTextReplacement(replacement)
    field.performKeyInput { pressKey(Key.Enter) }
    waitForIdle()
}

internal fun ComposeUiTest.click(text: String) {
    onNodeWithText(text).performClick()
    waitForIdle()
}

internal fun ComposeUiTest.clickFirst(text: String) {
    onAllNodesWithText(text)[0].performClick()
    waitForIdle()
}

/**
 * Clicks the last node showing [text].
 *
 * A menu or dialog is composed over the grid, so its rows come after the grid's — "Chorus Book"
 * names both a cell and the filter entry that selects it, and "Delete" both the bulk-bar button and
 * the confirmation. The last one is the one that just opened.
 */
internal fun ComposeUiTest.clickLast(text: String) {
    val nodes = onAllNodesWithText(text)
    nodes[nodes.fetchSemanticsNodes().size - 1].performClick()
    waitForIdle()
}

/** The text field of whatever opened last, rather than the search box that is always on screen. */
internal fun ComposeUiTest.typeIntoLastField(text: String) {
    val fields = onAllNodes(hasSetTextAction())
    fields[fields.fetchSemanticsNodes().size - 1].performTextInput(text)
    waitForIdle()
}

/** Clicks a control whose label carries a number in it — "Apply to 6 songs" and friends. */
internal fun ComposeUiTest.clickContaining(text: String) {
    onAllNodesWithText(text, substring = true)[0].performClick()
    waitForIdle()
}

/** Whether any node's text contains [text] — for a line built from a message and a format string. */
internal fun ComposeUiTest.isShowingText(text: String): Boolean =
    onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()

internal fun ComposeUiTest.countShowing(text: String): Int =
    onAllNodesWithText(text).fetchSemanticsNodes().size

internal fun ComposeUiTest.isShowing(text: String): Boolean =
    onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()

internal fun song(
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

/** Books of different sizes, a nested book, a loose song, and fields filled in only sometimes. */
internal val STOCK = listOf(
    song("001", "Amazing Grace", "Hymnal", author = "John Newton", composer = "Excell", tune = "NEW BRITAIN"),
    song("002", "Be Thou My Vision", "Hymnal", author = "Dallan Forgaill", tune = "SLANE"),
    song("010", "Holy Holy Holy", "Hymnal", author = "Reginald Heber"),
    song("001", "Here I Am to Worship", "Chorus Book", author = "Tim Hughes", ccli = "3266032"),
    song("001", "Silent Night", "Christmas/Carols", author = "Joseph Mohr"),
    song("", "Doxology", ""),
)

/** The English strings the window draws, which is what a test run renders. */
internal object Text {
    const val COLUMNS = "Columns"
    const val ALL_BOOKS = "All Song Books"
    const val NEW_BOOK_MENU = "New Song Book…"
    const val NEW_SONG = "New Song"
    const val BATCH_EDIT = "Batch Edit…"
    const val DELETE = "Delete"
    const val CLEAR = "Clear"
    const val SAVE = "Save Changes"
    const val REVERT = "Revert"
    const val RESET_FILTERS = "Reset filters"
    const val EMPTY_LIBRARY = "This library has no songs yet"
    const val NO_MATCHES = "No songs match your filters"
    const val SHOW_ALL = "Show all"
    const val CREATE = "Create"
    const val CANCEL = "Cancel"
}

@Composable
internal fun Themed(content: @Composable () -> Unit) {
    AppThemeWrapper(theme = ThemeMode.LIGHT) { content() }
}
