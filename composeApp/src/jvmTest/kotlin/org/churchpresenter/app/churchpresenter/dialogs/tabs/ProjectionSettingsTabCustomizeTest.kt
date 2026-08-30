@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The per-output Customize button on each assignment row, and the dialog it opens.
 *
 * The dialog shows what the row's display mode can actually use, so most of these drive a row into
 * a mode first and then assert on which panes and which style profile turn up. Settings assertions
 * go through `get()`; nothing here trusts a control's own text right after a click.
 */
class ProjectionSettingsTabCustomizeTest {

    private fun rows(vararg modes: String): AppSettings = AppSettings(
        projectionSettings = ProjectionSettings(
            screenAssignments = modes.map { ScreenAssignment(displayMode = it) },
        ),
    )

    private fun ComposeUiTest.openCustomize(row: Int) {
        gridButton(Grid.customize(row)).performScrollTo().performClick()
        waitForIdle()
    }

    // ── The button ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `every assignment row offers a Customize button`() = projectionTab { _ ->
        for (row in 0..1) {
            gridButton(Grid.customize(row)).performScrollTo().assertTextEquals("Customize")
        }
    }

    @Test
    fun `an untouched output follows the global settings`() = projectionTab { get ->
        openCustomize(row = 0)
        onNodeWithTag(CUSTOMIZE_STATUS_TAG).assertTextEquals("Following the global settings")
        assertFalse(get().projectionSettings.screenAssignments[0].isCustomized)
        onNodeWithText("Reset to Global").assertDoesNotExist()
    }

    // ── Which panes each display mode offers ────────────────────────────────────────────────────

    @Test
    fun `a stage monitor row offers its zones and the dictionary, not Bible or Songs`() {
        projectionTab(rows(Constants.DISPLAY_MODE_STAGE_MONITOR, Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            onNodeWithText("Stage Monitor").assertExists("its own settings must be the first pane")
            onNodeWithText("Dictionary").assertExists("a stage monitor draws the dictionary card too")
            // A stage monitor draws its zones, not the Bible profile.
            onNodeWithText("Bible").assertDoesNotExist()
            onNodeWithText("Songs").assertDoesNotExist()
        }
    }

    @Test
    fun `a fullscreen row offers Bible, Songs, Lower Third and Dictionary`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            for (pane in listOf("Bible", "Songs", "Lower Third", "Dictionary")) {
                onNodeWithText(pane).assertExists("$pane must be offered")
            }
            onNodeWithText("Stage Monitor").assertDoesNotExist()
        }
    }

    // ── Which style profile the Bible and Song panes show ───────────────────────────────────────

    @Test
    fun `a fullscreen row shows the full-screen profile alone`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            onNodeWithText("Songs").performClick()
            waitForIdle()

            onNodeWithText("Fullscreen Display").assertExists()
            // A fullscreen output cannot obey a lower-third setting, so it must not be offered.
            onNodeWithText("Lower Third Display").assertDoesNotExist()
            onAllNodesWithText("Lower Third Size").assertCountEquals(0)
        }
    }

    @Test
    fun `a lower-third row shows the lower-third profile alone`() {
        projectionTab(rows(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { _ ->
            openCustomize(row = 0)
            onNodeWithText("Songs").performClick()
            waitForIdle()

            onNodeWithText("Lower Third Display").assertExists()
            onNodeWithText("Fullscreen Display").assertDoesNotExist()
            onAllNodesWithText("Full Screen").assertCountEquals(0)
        }
    }

    @Test
    fun `the Bible pane drops the library sections when it is editing one output`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            onNodeWithText("Bible").performClick()
            waitForIdle()

            // The folder, the stack and the browsing panels are one per install, not per output.
            onNodeWithText("Bible Selection").assertDoesNotExist()
            // What the output actually draws with stays.
            onNodeWithText("Text Margins").assertExists()
        }
    }

    // ── Editing creates the override ────────────────────────────────────────────────────────────

    @Test
    fun `editing in the dialog stores an override on that row alone`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN, Constants.DISPLAY_MODE_FULLSCREEN)) { get ->
            openCustomize(row = 0)
            onNodeWithText("Bible").performClick()
            waitForIdle()
            onNode(hasSetTextAction() and hasText("54")).performScrollTo().performTextReplacement("31")
            waitForIdle()

            val edited = get().projectionSettings.screenAssignments[0]
            assertTrue(edited.isCustomized, "an edit must create this output's override")
            assertEquals(31, assertNotNull(edited.bibleOverride).marginTop)
            assertFalse(
                get().projectionSettings.screenAssignments[1].isCustomized,
                "the other row must still be following the global settings",
            )
            assertEquals(
                54,
                get().bibleSettings.marginTop,
                "and the global document itself must be untouched",
            )
        }
    }

    @Test
    fun `Reset to Global clears every override on the row`() {
        val customized = AppSettings(
            projectionSettings = ProjectionSettings(
                screenAssignments = listOf(
                    ScreenAssignment(
                        bibleOverride = org.churchpresenter.settings.BibleSettings(marginTop = 12),
                        songOverride = org.churchpresenter.settings.SongSettings(marginTop = 12),
                    ),
                ),
            ),
        )
        projectionTab(customized) { get ->
            openCustomize(row = 0)
            onNodeWithTag(CUSTOMIZE_STATUS_TAG).assertTextEquals("Customized for this output only")

            onNodeWithText("Reset to Global").performClick()
            waitForIdle()

            val reset = get().projectionSettings.screenAssignments[0]
            assertNull(reset.bibleOverride)
            assertNull(reset.songOverride)
            assertNull(reset.stageMonitorOverride)
            assertNull(reset.dictionaryOverride)
            assertFalse(reset.isCustomized)
        }
    }

    // ── The lower third's orientation ───────────────────────────────────────────────────────────

    @Test
    fun `a lower-third row picks its orientation in the dialog`() {
        projectionTab(rows(Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL)) { get ->
            openCustomize(row = 0)
            onNodeWithText("Lower Third").performClick()
            waitForIdle()

            onNodeWithText("Orientation").assertExists()
            onNodeWithText("Vertical Lower Third").performClick()
            waitForIdle()

            val assignment = get().projectionSettings.screenAssignments[0]
            assertTrue(assignment.isLowerThirdVertical, "the picked orientation must be stored")
            assertTrue(assignment.isLowerThird, "and it is still a lower third")
        }
    }

    @Test
    fun `a fullscreen row is offered no orientation`() {
        projectionTab(rows(Constants.DISPLAY_MODE_FULLSCREEN)) { _ ->
            openCustomize(row = 0)
            onNodeWithText("Lower Third").performClick()
            waitForIdle()

            // A fullscreen output has no band to turn, so the pane offers nothing.
            onNodeWithText("Orientation").assertDoesNotExist()
        }
    }
}
