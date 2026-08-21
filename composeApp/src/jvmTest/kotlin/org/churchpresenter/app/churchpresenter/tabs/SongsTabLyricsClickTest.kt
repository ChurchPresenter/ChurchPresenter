@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.churchpresenter.settings.SongSettings
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Clicking directly in the lyrics pane: the title-slide card, a lyric section's own background, and
 * — in per-line display mode — an individual line.
 *
 * None of these were driven from a click before; `SongsTabSelectionTest`/`SongsTabKeyboardTest` only
 * reach sections and lines through Go Live and the arrow keys. Double-clicking uses two plain,
 * back-to-back `performClick()`s: the tab's own double-click detection runs on wall-clock time, not
 * Compose's simulated gesture timestamps, so no special gesture sequence is needed.
 *
 * "Amazing Grace" is picked by name throughout rather than relying on which song opens selected —
 * the library is grouped by songbook first, so that is "How Great Thou Art", not the first fixture
 * in the list.
 *
 * See `SongsTabTestSupport.kt` for the harness and `verseMode()`.
 */
class SongsTabLyricsClickTest {

    private fun ComposeUiTest.clickRow(title: String) {
        onAllNodes(hasText(title))[0].performClick()
        waitForIdle()
    }

    private val lyricLine = "a line of Amazing Grace"

    /** Upper-cased on screen: the title slide wears the same section chip a verse does. */
    private val TITLE_SLIDE = "SONG TITLE SLIDE"

    // ── Title slide ─────────────────────────────────────────────────────────────

    @Test
    fun `clicking the title slide selects it and previews it, without going live`() =
        songsTab(songSettings = SongSettings(titleSlideEnabled = true)) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()

            assertEquals(0, reports.sectionIndex)
            assertEquals(0, reports.lineIndex)
            assertTrue(reports.presenting.isEmpty(), "a single click must only preview, not go live")
        }

    @Test
    fun `the title slide shows the author as its credit line`() =
        songsTab(songSettings = SongSettings(titleSlideEnabled = true)) { _, _ ->
            clickRow("Amazing Grace")
            assertTrue(showsContaining("John Newton"), rendered().toString())
        }

    @Test
    fun `double-clicking the title slide sends it live`() =
        songsTab(songSettings = SongSettings(titleSlideEnabled = true)) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()
            onNodeWithText(TITLE_SLIDE).performClick()
            waitForIdle()

            assertEquals(listOf(Presenting.LYRICS), reports.presenting)
            assertNotNull(reports.selectedSection)
        }

    // ── A lyric section, as a whole ────────────────────────────────────────────

    @Test
    fun `clicking a section in verse mode selects it and pushes it to the output pane`() =
        songsTab(songSettings = verseMode()) { vm, reports ->
            clickRow("Amazing Grace")
            onNodeWithText(lyricLine).performClick()
            waitForIdle()

            assertEquals(0, vm.selectedSectionIndex.value)
            assertEquals("Amazing Grace", reports.selectedSection?.title)
        }

    @Test
    fun `clicking a section while presenting also pushes it`() =
        songsTab(songSettings = verseMode(), isPresenting = true) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText(lyricLine).performClick()
            waitForIdle()

            assertNotNull(reports.selectedSection, "while presenting, a click must reach the output")
        }

    @Test
    fun `double-clicking a section sends it live regardless of presenting state`() =
        songsTab(songSettings = verseMode()) { _, reports ->
            clickRow("Amazing Grace")
            onNodeWithText(lyricLine).performClick()
            waitForIdle()
            onNodeWithText(lyricLine).performClick()
            waitForIdle()

            assertEquals(listOf(Presenting.LYRICS), reports.presenting)
            assertNotNull(reports.selectedSection)
        }

    // ── An individual line ──────────────────────────────────────────────────────

    @Test
    fun `clicking a specific line in line mode selects that exact line`() = songsTab { vm, reports ->
        // Default settings are already line mode (SongsTabKeyboardTest pins this).
        clickRow("Amazing Grace")
        onNodeWithText(lyricLine).performClick()
        waitForIdle()

        assertEquals(0, vm.selectedLineIndex.value)
        assertEquals(0, reports.lineIndex)
    }

    @Test
    fun `double-clicking a line sends the song live at that line`() = songsTab { vm, reports ->
        clickRow("Amazing Grace")
        onNodeWithText(lyricLine).performClick()
        waitForIdle()
        onNodeWithText(lyricLine).performClick()
        waitForIdle()

        assertEquals(listOf(Presenting.LYRICS), reports.presenting)
        assertEquals(0, vm.selectedLineIndex.value)
        assertNotNull(reports.selectedSection)
    }
}
