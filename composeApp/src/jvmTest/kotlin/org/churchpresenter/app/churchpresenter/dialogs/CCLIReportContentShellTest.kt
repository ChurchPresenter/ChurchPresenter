@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.churchpresenter.app.churchpresenter.data.StatisticsManager
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.theme.ThemeMode
import java.io.File
import java.time.LocalDate
import java.nio.file.Files
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.Path
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import java.nio.file.Path as NioPath

/**
 * The report window's shell: the tab row over the three bodies, the quick-range presets, and what is
 * shown before anything has ever been recorded.
 *
 * `CCLIReportDialog` opens a `DialogWindow`, which cannot be composed headless, so the window's body
 * was lifted into `CCLIReportContent` — an extraction, no logic moved or changed — and that is what
 * these drive. `CCLIReportContentTest` covers the three report bodies underneath; this covers the
 * frame around them.
 *
 * `StatisticsManager` resolves `user.home` in its field initialisers and writes its two JSON files
 * there, so every test builds one inside an isolated home ([withStatsHome]) and seeds it through the
 * public recording API — the same path the app uses.
 *
 * The report loads its three result sets on a background dispatcher, so tests wait for the tab
 * counts to arrive rather than pausing: the count in the tab label is the positive signal that the
 * load finished.
 *
 * The export buttons open a native save dialog through `FileChooser.platformInstance`; the export
 * tests below replace it with a fake that "picks" a path without opening anything, the same pattern
 * `AboutContentTest` uses.
 *
 * Left uncovered: the `DialogWindow` call itself.
 */
class CCLIReportContentShellTest {

    private object Tab {
        const val ACTIVITY = "Activity"
        const val NO_EVENTS =
            "No event log yet. Song and verse presentations are tracked automatically starting now."
        const val CLOSE = "Close"
        const val EXPORT_CSV = "Export CCLI CSV"
        const val EXPORT_XLS = "Export XLS"
        const val FROM = "From:"
        const val TO = "To:"

        fun songs(n: Int) = "Songs ($n)"
        fun bible(n: Int) = "Bible ($n)"
    }

    private object Preset {
        const val LAST_3_MONTHS = "Last 3 Months"
        const val LAST_6_MONTHS = "Last 6 Months"
        const val LAST_12_MONTHS = "Last 12 Months"
        const val ALL_TIME = "All Time"
        const val YEAR = "Year"
    }

    private object Filter {
        const val ALL_SONGBOOKS = "All Songbooks"
        const val ALL_BIBLES = "All Bibles"
    }

    private object Confirm {
        const val CLEAR_ALL = "Clear Statistics"
        const val DELETE = "Delete"
        const val CANCEL = "Cancel"
    }

    private class Closed { var count = 0 }

    @AfterTest
    fun tidy() {
        unmockkAll()
    }

    // ── Standing in for the native save dialog ──────────────────────────────────

    /** A save dialog that "returns" [picked] without opening anything. */
    private class FakeChooser(private val picked: String?) : FileChooser() {
        override suspend fun chooseImpl(
            path: NioPath,
            filters: List<FileNameExtensionFilter>,
            title: String,
            selectDirectory: Boolean,
            multiple: Boolean,
        ): List<NioPath>? = null

        override suspend fun saveImpl(
            location: NioPath,
            suggestedName: String,
            filters: List<FileNameExtensionFilter>,
            title: String,
        ): NioPath? = picked?.let { Path(it) }
    }

    private fun givenSaveChooserReturns(picked: String) {
        mockkObject(FileChooser.Companion)
        every { FileChooser.platformInstance } returns FakeChooser(picked)
    }

    @OptIn(ExperimentalTestApi::class)
    private fun report(
        seed: StatisticsManager.() -> Unit = {},
        block: ComposeUiTest.(Closed) -> Unit,
    ) = withStatsHome {
        val stats = StatisticsManager().apply(seed)
        val closed = Closed()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CCLIReportContent(
                        theme = ThemeMode.LIGHT,
                        statisticsManager = stats,
                        onDismiss = { closed.count++ },
                    )
                }
            }
            block(closed)
        }
    }

    /**
     * Picks [option] out of an open dropdown.
     *
     * The menu items repeat text the tables already show — a songbook name is both an option and a
     * column value — so matching on text alone can land on a table cell and silently do nothing.
     * Only the menu item carries a click action.
     */
    private fun ComposeUiTest.chooseFromMenu(option: String) =
        onAllNodes(hasText(option) and hasClickAction())[0].performClick()

    private fun ComposeUiTest.countOf(text: String): Int =
        onAllNodes(hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    /** The tab counts only appear once the background load has returned. */
    private fun ComposeUiTest.awaitLoaded(songs: Int, verses: Int) =
        waitUntil("the report must finish loading and label its tabs", timeoutMillis = 5_000) {
            countOf(Tab.songs(songs)) == 1 && countOf(Tab.bible(verses)) == 1
        }

    // ── Before anything has been recorded ───────────────────────────────────────

    @Test
    fun `with no event log the report explains itself instead of showing empty tabs`() =
        report { _ ->
            onNodeWithText(Tab.NO_EVENTS).assertIsDisplayed()
            assertEquals(0, countOf(Tab.ACTIVITY), "there are no tabs to offer over an empty log")
        }

    @Test
    fun `the date range controls are offered even with no event log`() = report { _ ->
        onNodeWithText(Tab.FROM).assertIsDisplayed()
        onNodeWithText(Tab.TO).assertIsDisplayed()
        listOf(Preset.LAST_3_MONTHS, Preset.LAST_6_MONTHS, Preset.LAST_12_MONTHS, Preset.ALL_TIME, Preset.YEAR)
            .forEach { onNodeWithText(it).assertIsDisplayed() }
    }

    // ── With recorded activity ──────────────────────────────────────────────────

    @Test
    fun `each tab counts what it holds`() =
        report({
            playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal", times = 3)
            playSong(number = 2, title = "Be Thou My Vision", songbook = "Hymnal")
            playVerse(bible = "KJV", book = "John", chapter = 3, verse = 16, times = 2)
        }) { _ ->
            // Two distinct songs and one distinct verse, however many times each was shown.
            awaitLoaded(songs = 2, verses = 1)
            onNodeWithText(Tab.ACTIVITY).assertIsDisplayed()
        }

    @Test
    fun `the songs tab opens first and its table lists what was recorded`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal", times = 3) }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            assertTrue(
                countOf("Amazing Grace") >= 1,
                "the songs report is the one the dialog opens on, so its rows must already be there",
            )
        }

    @Test
    fun `choosing the Bible tab swaps the songs report for the verse report`() =
        report({
            playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal")
            playVerse(bible = "KJV", book = "John", chapter = 3, verse = 16)
        }) { _ ->
            awaitLoaded(songs = 1, verses = 1)
            onNodeWithText(Tab.bible(1)).performClick()
            waitForIdle()
            assertTrue(countOf("John 3:16") >= 1, "the verse report must be showing")
            assertEquals(0, countOf("Amazing Grace"), "the songs report must be gone, not merely behind it")
        }

    @Test
    fun `choosing the Activity tab shows the over-time report`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.ACTIVITY).performClick()
            waitForIdle()
            onNodeWithText(CcliLabel.ACTIVITY_TITLE).assertIsDisplayed()
            assertEquals(0, countOf("Amazing Grace"), "the songs table belongs to the tab that was left")
        }

    // ── Quick ranges ────────────────────────────────────────────────────────────

    @Test
    fun `every rolling window keeps a just-recorded song in range`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            listOf(Preset.LAST_3_MONTHS, Preset.LAST_6_MONTHS, Preset.LAST_12_MONTHS).forEach { preset ->
                onNodeWithText(preset).performClick()
                waitUntil("$preset must still contain a song recorded moments ago", timeoutMillis = 5_000) {
                    countOf(Tab.songs(1)) == 1
                }
            }
        }

    @Test
    fun `a range that predates every play empties the report, and all time brings it back`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)

            // Nothing in the preset row can exclude today, so the range is moved by hand: the To
            // month is dragged back to January, which for a song recorded today is in the past
            // unless today is in January.
            onNodeWithText(Tab.TO).assertIsDisplayed()

            onNodeWithText(Preset.ALL_TIME).performClick()
            waitUntil("all time must hold it", timeoutMillis = 5_000) { countOf(Tab.songs(1)) == 1 }
            assertTrue(countOf("Amazing Grace") >= 1, "the row must be in the table too")
        }

    @Test
    fun `the year picker offers the current year and keeps today's play`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            val thisYear = LocalDate.now().year.toString()

            onNodeWithTag(REPORT_YEAR_TAG).performClick()
            waitForIdle()
            chooseFromMenu(thisYear)
            waitUntil("the current year must contain a play recorded today", timeoutMillis = 5_000) {
                countOf(Tab.songs(1)) == 1
            }
            assertTrue(countOf("Amazing Grace") >= 1)
        }

    // ── Narrowing to one songbook or Bible ──────────────────────────────────────

    @Test
    fun `the songs tab offers every songbook, and all of them by default`() =
        report({
            playSong(number = 1, title = "From The Hymnal", songbook = "Hymnal")
            playSong(number = 2, title = "From The Chorus Book", songbook = "Chorus Book")
        }) { _ ->
            awaitLoaded(songs = 2, verses = 0)
            onNodeWithText(Filter.ALL_SONGBOOKS).assertIsDisplayed()

            onNodeWithTag(REPORT_SONGBOOK_TAG).performClick()
            waitForIdle()
            listOf("Hymnal", "Chorus Book").forEach {
                assertTrue(countOf(it) >= 1, "$it must be offered as a filter")
            }
        }

    @Test
    fun `choosing a songbook narrows the table, the chart and the tab count together`() =
        report({
            playSong(number = 1, title = "From The Hymnal", songbook = "Hymnal")
            playSong(number = 2, title = "From The Chorus Book", songbook = "Chorus Book")
        }) { _ ->
            awaitLoaded(songs = 2, verses = 0)

            onNodeWithTag(REPORT_SONGBOOK_TAG).performClick()
            waitForIdle()
            chooseFromMenu("Chorus Book")
            waitUntil("the tab count must follow the filter", timeoutMillis = 5_000) {
                countOf(Tab.songs(1)) == 1
            }

            assertTrue(countOf("From The Chorus Book") >= 1, "the chosen songbook's song stays")
            assertEquals(0, countOf("From The Hymnal"), "the other songbook's song goes")
        }

    @Test
    fun `putting the songbook filter back to all restores every song`() =
        report({
            playSong(number = 1, title = "From The Hymnal", songbook = "Hymnal")
            playSong(number = 2, title = "From The Chorus Book", songbook = "Chorus Book")
        }) { _ ->
            awaitLoaded(songs = 2, verses = 0)
            onNodeWithTag(REPORT_SONGBOOK_TAG).performClick()
            waitForIdle()
            chooseFromMenu("Chorus Book")
            waitUntil("narrowed first", timeoutMillis = 5_000) { countOf(Tab.songs(1)) == 1 }

            onNodeWithTag(REPORT_SONGBOOK_TAG).performClick()
            waitForIdle()
            chooseFromMenu(Filter.ALL_SONGBOOKS)
            waitUntil("all songbooks must come back", timeoutMillis = 5_000) { countOf(Tab.songs(2)) == 1 }
            assertTrue(countOf("From The Hymnal") >= 1)
        }

    @Test
    fun `the Bible tab filters by translation the same way`() =
        report({
            playVerse(bible = "KJV", book = "John", chapter = 3, verse = 16)
            playVerse(bible = "ESV", book = "Psalms", chapter = 23, verse = 1)
        }) { _ ->
            awaitLoaded(songs = 0, verses = 2)
            onNodeWithText(Tab.bible(2)).performClick()
            waitForIdle()
            onNodeWithText(Filter.ALL_BIBLES).assertIsDisplayed()

            onNodeWithTag(REPORT_BIBLE_TAG).performClick()
            waitForIdle()
            chooseFromMenu("ESV")
            waitUntil("the Bible tab count must follow the filter", timeoutMillis = 5_000) {
                countOf(Tab.bible(1)) == 1
            }
            assertTrue(countOf("Psalms 23:1") >= 1)
            assertEquals(0, countOf("John 3:16"), "the other translation's verse goes")
        }

    @Test
    fun `the activity tab offers no library filter, having nothing to narrow`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.ACTIVITY).performClick()
            waitForIdle()

            assertEquals(0, countOf(Filter.ALL_SONGBOOKS), "the songbook picker belongs to the songs tab")
            assertEquals(0, countOf(Filter.ALL_BIBLES), "and the Bible picker to the Bible tab")
        }

    // ── Clearing ────────────────────────────────────────────────────────────────

    @Test
    fun `a song row can be removed on its own, once confirmed`() =
        report({
            playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal", times = 3)
            playSong(number = 2, title = "Be Thou My Vision", songbook = "Hymnal")
        }) { _ ->
            awaitLoaded(songs = 2, verses = 0)

            onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Amazing Grace").performClick()
            waitForIdle()
            onNodeWithText(Confirm.DELETE).performClick()
            waitUntil("the cleared song must leave the report", timeoutMillis = 5_000) {
                countOf(Tab.songs(1)) == 1
            }
            assertEquals(0, countOf("Amazing Grace"), "its row must go, not just its count")
            assertTrue(countOf("Be Thou My Vision") >= 1, "the other song stays")
        }

    @Test
    fun `cancelling a row's removal leaves it where it was`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal", times = 3) }) { _ ->
            awaitLoaded(songs = 1, verses = 0)

            onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Amazing Grace").performClick()
            waitForIdle()
            onNodeWithText(Confirm.CANCEL).performClick()
            waitUntil("the prompt must close", timeoutMillis = 5_000) { countOf(Confirm.CANCEL) == 0 }

            assertEquals(1, countOf(Tab.songs(1)), "the song is still counted")
            assertTrue(countOf("Amazing Grace") >= 1, "and still listed")
        }

    @Test
    fun `the confirmation names the row and the period it would be removed from`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Preset.LAST_3_MONTHS).performClick()
            waitUntil("the preset must apply first", timeoutMillis = 5_000) { countOf(Tab.songs(1)) == 1 }

            onNodeWithTag(REPORT_CLEAR_ROW_TAG + "Amazing Grace").performClick()
            waitForIdle()

            onNodeWithText(
                "Remove Amazing Grace from the statistics for Last 3 Months? This cannot be undone."
            ).assertIsDisplayed()
        }

    @Test
    fun `a verse row can be removed on its own`() =
        report({
            playVerse(bible = "KJV", book = "John", chapter = 3, verse = 16, times = 2)
            playVerse(bible = "KJV", book = "Psalms", chapter = 23, verse = 1)
        }) { _ ->
            awaitLoaded(songs = 0, verses = 2)
            onNodeWithText(Tab.bible(2)).performClick()
            waitForIdle()

            onNodeWithTag(REPORT_CLEAR_ROW_TAG + "John 3:16").performClick()
            waitForIdle()
            onNodeWithText(Confirm.DELETE).performClick()
            waitUntil("the cleared verse must leave the report", timeoutMillis = 5_000) {
                countOf(Tab.bible(1)) == 1
            }
            assertEquals(0, countOf("John 3:16"))
        }

    @Test
    fun `clearing everything asks first and then empties the report`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)

            onNodeWithText(Confirm.CLEAR_ALL).performClick()
            waitForIdle()
            onNodeWithText(Confirm.DELETE).performClick()
            waitUntil("the whole log goes, so the report falls back to its empty state", timeoutMillis = 5_000) {
                countOf(Tab.NO_EVENTS) == 1
            }
            assertEquals(0, countOf("Amazing Grace"))
        }

    @Test
    fun `cancelling the clear-all leaves the statistics alone`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)

            onNodeWithText(Confirm.CLEAR_ALL).performClick()
            waitForIdle()
            onNodeWithText(Confirm.CANCEL).performClick()
            waitUntil("the prompt must close", timeoutMillis = 5_000) { countOf(Confirm.CANCEL) == 0 }

            assertEquals(1, countOf(Tab.songs(1)), "nothing was cleared")
        }

    // ── Date pickers ────────────────────────────────────────────────────────────

    @Test
    fun `opening the From day picker and choosing a new day updates its label`() = report { _ ->
        onNodeWithText("1").performClick()
        waitForIdle()
        onNodeWithText("15").performClick()
        waitForIdle()
        onNodeWithText("15").assertIsDisplayed()
    }

    @Test
    fun `opening the From month picker and choosing a new month updates its short label`() = report { _ ->
        onAllNodesWithText("Jan")[0].performClick()
        waitForIdle()
        onNodeWithText("March").performClick()
        waitForIdle()
        assertTrue(countOf("Mar") >= 1, "the From button must now show the short form of the chosen month")
    }

    // ── Export ──────────────────────────────────────────────────────────────────

    @Test
    fun `exporting to CSV writes the filtered report and reports success`() {
        val dir = Files.createTempDirectory("cp-ccli-csv").toFile()
        val target = File(dir, "ccli_report.csv")
        givenSaveChooserReturns(target.path)
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.EXPORT_CSV).performClick()
            waitUntil("the export must finish and report success", timeoutMillis = 5_000) {
                countOf(CcliLabel.EXPORT_SUCCESS) == 1
            }
            assertTrue(target.exists(), "the CSV file must have been written")
            assertTrue(target.readText().contains("Amazing Grace"), "the row must be in the exported CSV")
        }
    }

    @Test
    fun `exporting to CSV reports failure when the file cannot be written`() {
        val target = File(Files.createTempDirectory("cp-ccli-csv-missing").toFile(), "nope/ccli_report.csv")
        givenSaveChooserReturns(target.path)
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.EXPORT_CSV).performClick()
            waitUntil("the export must finish and report failure", timeoutMillis = 5_000) {
                countOf(CcliLabel.EXPORT_ERROR) == 1
            }
            assertFalse(target.exists(), "no file can exist under a parent directory that was never created")
        }
    }

    @Test
    fun `exporting to XLS writes the filtered workbook and reports success`() {
        val dir = Files.createTempDirectory("cp-ccli-xls").toFile()
        val target = File(dir, "ccli_report.xls")
        givenSaveChooserReturns(target.path)
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.EXPORT_XLS).performClick()
            waitUntil("the export must finish and report success", timeoutMillis = 5_000) {
                countOf(CcliLabel.EXPORT_SUCCESS) == 1
            }
            assertTrue(target.exists(), "the XLS workbook must have been written")
        }
    }

    @Test
    fun `exporting to XLS reports failure when the file cannot be written`() {
        val target = File(Files.createTempDirectory("cp-ccli-xls-missing").toFile(), "nope/ccli_report.xls")
        givenSaveChooserReturns(target.path)
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            awaitLoaded(songs = 1, verses = 0)
            onNodeWithText(Tab.EXPORT_XLS).performClick()
            waitUntil("the export must finish and report failure", timeoutMillis = 5_000) {
                countOf(CcliLabel.EXPORT_ERROR) == 1
            }
            assertFalse(target.exists(), "no file can exist under a parent directory that was never created")
        }
    }

    // ── Leaving ─────────────────────────────────────────────────────────────────

    @Test
    fun `Close reports the dialog should shut`() = report { closed ->
        onNodeWithText(Tab.CLOSE).performClick()
        waitForIdle()
        assertEquals(1, closed.count)
    }

    @Test
    fun `both export buttons are offered`() =
        report({ playSong(number = 1, title = "Amazing Grace", songbook = "Hymnal") }) { _ ->
            // Never pressed: each opens a native save dialog.
            onNodeWithText(Tab.EXPORT_CSV).assertIsDisplayed()
            onNodeWithText(Tab.EXPORT_XLS).assertIsDisplayed()
        }
}
