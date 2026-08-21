@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.app.churchpresenter.data.settings.ScreenAssignment
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the Content Outputs dialog — the per-output panel that decides which content types appear
 * on a given screen.
 *
 * Each toggle is a clickable row rather than a `Checkbox`, so it publishes no `ToggleableState` to
 * assert against. What it does have is the dialog's own "THIS OUTPUT SHOWS" preview, which lists
 * exactly the enabled types: a toggle that is on has its label on screen twice — once in the grid
 * you click, once in the preview — and a toggle that is off has it once. That count, together with
 * the "N of M content types enabled" header, is how each change is checked on screen, and both are
 * derived from the stored [ScreenAssignment] rather than from any local widget state.
 */
class ProjectionSettingsTabContentOutputsTest {

    /** Opens the dialog for assignment row [row]. */
    private fun ComposeUiTest.openContentOutputs(row: Int = 0) {
        gridButton(Grid.contentOutputs(row)).performScrollTo().performClick()
        waitForIdle()
    }

    /** Clicks the content toggle captioned [label] — the clickable copy, not the preview's. */
    private fun ComposeUiTest.toggleContent(label: String) {
        onNode(hasClickAction() and hasTextExactly(label)).performClick()
        waitForIdle()
    }

    /** A toggle that is on appears twice (grid + preview); one that is off appears once. */
    private fun ComposeUiTest.assertShownInPreview(label: String, shown: Boolean) {
        onAllNodesWithText(label).assertCountEquals(if (shown) 2 else 1)
    }

    private fun row0(get: () -> org.churchpresenter.app.churchpresenter.data.settings.AppSettings): ScreenAssignment =
        get().projectionSettings.screenAssignments[0]

    // ── Opening and closing ─────────────────────────────────────────────────────────────────────

    @Test
    fun `the content outputs button opens a dialog for its own screen`() = projectionTab { _ ->
        onAllNodesWithText("Content Outputs — Screen 1").assertCountEquals(0)
        openContentOutputs(row = 0)
        onNodeWithText("Content Outputs — Screen 1").assertExists("the dialog must name the screen it edits")
        onNodeWithText("16 of 17 content types enabled on this screen").assertExists()
    }

    @Test
    fun `the second row opens the dialog for the second screen`() = projectionTab { _ ->
        openContentOutputs(row = 1)
        onNodeWithText("Content Outputs — Screen 2").assertExists()
        onAllNodesWithText("Content Outputs — Screen 1").assertCountEquals(0)
    }

    @Test
    fun `Done closes the dialog`() = projectionTab { _ ->
        openContentOutputs()
        onNodeWithText("Done").performClick()
        waitForIdle()
        onAllNodesWithText("Content Outputs — Screen 1").assertCountEquals(0)
    }

    @Test
    fun `the close button closes the dialog`() = projectionTab { _ ->
        openContentOutputs()
        onNodeWithContentDescription("Done").performClick()
        waitForIdle()
        onAllNodesWithText("Content Outputs — Screen 1").assertCountEquals(0)
    }

    // ── Individual toggles ──────────────────────────────────────────────────────────────────────

    @Test
    fun `turning a content type off stores it and drops it from the preview`() = projectionTab { get ->
        openContentOutputs()
        assertEquals(true, row0(get).showMedia, "media starts on")
        assertShownInPreview("Media", shown = true)

        toggleContent("Media")

        assertEquals(false, row0(get).showMedia, "the toggle must store the change")
        assertShownInPreview("Media", shown = false)
        onNodeWithText("15 of 17 content types enabled on this screen").assertExists()
    }

    @Test
    fun `turning a content type back on restores it`() = projectionTab { get ->
        openContentOutputs()
        toggleContent("Canvas")
        assertEquals(false, row0(get).showCanvas, "first click turns it off")
        assertShownInPreview("Canvas", shown = false)

        toggleContent("Canvas")
        assertEquals(true, row0(get).showCanvas, "second click turns it back on")
        assertShownInPreview("Canvas", shown = true)
        onNodeWithText("16 of 17 content types enabled on this screen").assertExists()
    }

    @Test
    fun `every content toggle stores its own flag`() = projectionTab { get ->
        openContentOutputs()
        val toggles = listOf<Pair<String, (ScreenAssignment) -> Boolean>>(
            "Pictures/Presentation" to { it.showPictures },
            "Media" to { it.showMedia },
            "Lower Third" to { it.showStreaming },
            "Announcements" to { it.showAnnouncements },
            "Web" to { it.showWebsite },
            "Canvas" to { it.showCanvas },
            "Q&A" to { it.showQA },
            "STT" to { it.showSTT },
            "Dictionary" to { it.showDictionary },
        )
        for ((label, read) in toggles) {
            assertEquals(true, read(row0(get)), "$label starts on")
            toggleContent(label)
            assertEquals(false, read(row0(get)), "clicking $label must clear its own flag")
            assertShownInPreview(label, shown = false)
        }
        onNodeWithText("7 of 17 content types enabled on this screen").assertExists()
    }

    @Test
    fun `every background toggle stores its own flag`() = projectionTab { get ->
        openContentOutputs()
        val toggles = listOf<Pair<String, (ScreenAssignment) -> Boolean>>(
            "Background" to { it.showFullscreenBackground },
            "Lower Third Background" to { it.showLowerThirdBackground },
            "Bible Background" to { it.showBibleBackground },
            "Songs Background" to { it.showSongsBackground },
        )
        for ((label, read) in toggles) {
            assertEquals(true, read(row0(get)), "$label starts on")
            toggleContent(label)
            assertEquals(false, read(row0(get)), "clicking $label must clear its own flag")
        }
        onNodeWithText("12 of 17 content types enabled on this screen").assertExists()
    }

    /** Song look-ahead is the one toggle that starts off, and it is gated on Songs being on. */
    @Test
    fun `the song look-ahead toggle starts off and can be turned on`() = projectionTab { get ->
        openContentOutputs()
        assertEquals(false, row0(get).songLookAhead, "look-ahead starts off")
        assertShownInPreview("Song LA", shown = false)

        toggleContent("Song LA")

        assertEquals(true, row0(get).songLookAhead, "the toggle must store the change")
        onNodeWithText("17 of 17 content types enabled on this screen").assertExists()
    }

    // ── Quick select ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Clear All turns every content type off`() = projectionTab { get ->
        openContentOutputs()
        onNodeWithText("Clear All").performClick()
        waitForIdle()

        val assignment = row0(get)
        assertEquals(Constants.SONG_LANG_OFF, assignment.bibleMode, "Bible must be switched off")
        assertEquals(Constants.SONG_LANG_OFF, assignment.songMode, "Songs must be switched off")
        assertEquals(false, assignment.showMedia)
        assertEquals(false, assignment.showCanvas)
        assertEquals(false, assignment.showFullscreenBackground)
        onNodeWithText("0 of 17 content types enabled on this screen").assertExists()
    }

    @Test
    fun `Select All turns every content type on`() = projectionTab { get ->
        openContentOutputs()
        onNodeWithText("Clear All").performClick()
        waitForIdle()
        onNodeWithText("0 of 17 content types enabled on this screen").assertExists()

        onNodeWithText("Select All").performClick()
        waitForIdle()

        val assignment = row0(get)
        assertEquals(true, assignment.showMedia)
        assertEquals(true, assignment.showSongsBackground)
        assertEquals(true, assignment.songLookAhead, "Select All includes the look-ahead")
        onNodeWithText("17 of 17 content types enabled on this screen").assertExists()
    }

    // ── Bible and Songs language modes ──────────────────────────────────────────────────────────

    @Test
    fun `unticking Bible switches the output off without touching Songs`() = projectionTab { get ->
        openContentOutputs()
        assertEquals(Constants.SONG_LANG_BOTH, row0(get).bibleMode, "Bible starts on")

        // The on/off control lives on the master row inside the translation-picker dropdown, not
        // on the collapsed trigger, so it must be opened first. Per-translation ticks only appear
        // with more than one translation configured, which this fixture does not have.
        onNodeWithContentDescription("Bible Translations").performClick()
        waitForIdle()
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_OFF, row0(get).bibleMode, "unticking must be stored")
        assertEquals(Constants.SONG_LANG_BOTH, row0(get).songMode, "Songs must be untouched")
    }

    @Test
    fun `the Songs language dropdown stores the picked mode`() = projectionTab { get ->
        openContentOutputs()
        // Songs is the only dropdown left in the dialog.
        onAllNodesWithText("Both")[0].performClick()
        waitForIdle()
        onNodeWithText("Language 1").performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_PRIMARY, row0(get).songMode, "picking Language 1 must be stored")
        assertEquals(Constants.SONG_LANG_BOTH, row0(get).bibleMode, "Bible must be untouched")
        onNodeWithText("Songs · Language 1").assertExists("the preview must name the chosen mode")
    }

    @Test
    fun `switching Bible off drops it from the count and the preview`() = projectionTab { get ->
        openContentOutputs()
        onNodeWithContentDescription("Bible Translations").performClick()
        waitForIdle()
        onAllNodes(isToggleable())[0].performScrollTo().performClick()
        waitForIdle()

        assertEquals(Constants.SONG_LANG_OFF, row0(get).bibleMode, "unticking must be stored")
        onNodeWithText("15 of 17 content types enabled on this screen").assertExists()
        // The preview chip is gone (Bible is off), leaving the trigger's own label plus the master
        // row's label inside the still-open dropdown -- toggling it does not dismiss the menu, so
        // more can be picked without reopening it.
        onAllNodesWithText("Bible").assertCountEquals(2)
    }

    // ── Back on the tab ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the row's summary button follows what the dialog changed`() = projectionTab { get ->
        gridButton(Grid.contentOutputs(row = 0)).assertTextEquals("16 of 17 enabled")

        openContentOutputs(row = 0)
        toggleContent("Media")
        toggleContent("Canvas")
        onNodeWithText("Done").performClick()
        waitForIdle()

        assertEquals(false, row0(get).showMedia)
        gridButton(Grid.contentOutputs(row = 0)).assertTextEquals("14 of 17 enabled")
        gridButton(Grid.contentOutputs(row = 1)).assertTextEquals("16 of 17 enabled")
    }

    @Test
    fun `editing one screen leaves the other alone`() = projectionTab { get ->
        openContentOutputs(row = 1)
        onNodeWithText("Clear All").performClick()
        waitForIdle()
        onNodeWithText("Done").performClick()
        waitForIdle()

        assertEquals(false, get().projectionSettings.screenAssignments[1].showMedia, "screen 2 was cleared")
        assertEquals(true, get().projectionSettings.screenAssignments[0].showMedia, "screen 1 must be untouched")
        gridButton(Grid.contentOutputs(row = 0)).assertTextEquals("16 of 17 enabled")
        gridButton(Grid.contentOutputs(row = 1)).assertTextEquals("0 of 17 enabled")
    }
}
