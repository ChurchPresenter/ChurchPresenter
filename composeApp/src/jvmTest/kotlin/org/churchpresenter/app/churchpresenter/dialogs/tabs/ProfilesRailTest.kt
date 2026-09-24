@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The list down the left of the Profiles tab: creating, duplicating, renaming, deleting and
 * selecting a profile, and the empty state before there is one.
 *
 * New ground rather than a port -- the Projection tab had no such list. What it replaces is a set
 * of per-display settings, so the invariant worth holding onto is that a profile which is still
 * assigned to an output cannot be deleted out from under it.
 */
class ProfilesRailTest {

    private fun docWith(vararg profiles: OutputProfile, assignments: List<ScreenAssignment> = emptyList()) =
        AppSettings(
            projectionSettings = ProjectionSettings(
                outputProfiles = profiles.toList(),
                screenAssignments = assignments,
            ),
        )

    private fun two() = docWith(
        OutputProfile(id = "main", name = "Main"),
        OutputProfile(id = "foyer", name = "Foyer"),
    )

    private fun AppSettings.names() = projectionSettings.outputProfiles.map { it.name }

    /**
     * Opens [name]'s row and presses its Delete. A row's Duplicate and Delete show under the pointer
     * and on the selected row, so selecting it first is what puts the button there.
     */
    private fun SkikoComposeUiTest.deleteProfile(name: String) {
        onAllNodesWithText(name)[0].performClick()
        waitForIdle()
        onNode(hasContentDescription("Delete")).performClick()
        waitForIdle()
    }

    @Test
    fun `an empty document says what to do about it`() {
        profilesTab(docWith()) { _ ->
            onNodeWithText("Create a profile to start styling it").assertExists()
        }
    }

    @Test
    fun `New Profile adds an unnamed one`() {
        profilesTab(two()) { get ->
            onNodeWithText("New Profile").performClick()
            waitForIdle()

            val profiles = get().projectionSettings.outputProfiles
            assertEquals(3, profiles.size)
            // Created unnamed: the operator names it in the header, which opens on the new
            // profile, rather than being handed a "Profile 3" to rename.
            assertEquals("", profiles[2].name)
        }
    }

    @Test
    fun `Duplicate copies the profile's styling under a new name`() {
        val doc = docWith(
            OutputProfile(id = "main", name = "Main", displayMode = Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL),
        )
        profilesTab(doc) { get ->
            onAllNodes(hasContentDescription("Duplicate"))[0].performClick()
            waitForIdle()

            val profiles = get().projectionSettings.outputProfiles
            assertEquals(2, profiles.size)
            assertEquals(
                Constants.DISPLAY_MODE_LOWER_THIRD_HORIZONTAL,
                profiles[1].displayMode,
                "a duplicate is the same profile under another name",
            )
            assertTrue(profiles[1].name != profiles[0].name, "and not the same name")
            assertTrue(profiles[1].id != profiles[0].id, "nor the same id")
        }
    }

    @Test
    fun `renaming in the header renames the profile on the rail`() {
        profilesTab(two()) { get ->
            onNode(hasText("Main") and hasSetTextAction()).performTextReplacement("Stage")
            waitForIdle()

            assertEquals(listOf("Stage", "Foyer"), get().names())
        }
    }

    @Test
    fun `clicking a profile on the rail opens it`() {
        profilesTab(two()) { _ ->
            onAllNodesWithText("Foyer")[0].performClick()
            waitForIdle()

            // Selected, so its name is in the header field too.
            onAllNodesWithText("Foyer").assertCountEquals(2)
        }
    }

    @Test
    fun `Delete asks first, and removes the profile when confirmed`() {
        profilesTab(two()) { get ->
            deleteProfile("Foyer")
            onNodeWithText("Delete \"Foyer\"? This can't be undone.").assertExists()

            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(listOf("Main"), get().names())
        }
    }

    @Test
    fun `Delete can be backed out of`() {
        profilesTab(two()) { get ->
            deleteProfile("Foyer")
            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertEquals(listOf("Main", "Foyer"), get().names(), "nothing was deleted")
        }
    }

    @Test
    fun `a profile an output is still using cannot be deleted`() {
        val doc = docWith(
            OutputProfile(id = "main", name = "Main"),
            OutputProfile(id = "foyer", name = "Foyer"),
            assignments = listOf(ScreenAssignment(activeProfileId = "foyer")),
        )
        profilesTab(doc) { get ->
            deleteProfile("Foyer")

            // Refused rather than merely warned about: the dialog names the outputs still on it
            // and offers no confirm button at all, so the only way out is to back out.
            onNodeWithText("Can't delete", substring = true).assertExists()
            onNodeWithText("OK").assertDoesNotExist()

            onNodeWithText("Cancel").performClick()
            waitForIdle()
            assertEquals(listOf("Main", "Foyer"), get().names(), "it is still there")
        }
    }

    @Test
    fun `the list names the outputs each profile drives`() {
        val doc = docWith(
            OutputProfile(id = "main", name = "Main"),
            assignments = listOf(
                ScreenAssignment(activeProfileId = "main"),
                ScreenAssignment(activeProfileId = "main"),
            ),
        )
        profilesTab(doc) { _ ->
            onAllNodesWithText("Used by Screen 1, Screen 2")[0].assertExists()
        }
    }

    /**
     * The empty state's own button, which names the profile it makes.
     *
     * Distinct from "New Profile", which leaves the name blank for the operator to fill in: this is
     * the one-click way out of an empty document, so the profile it makes arrives usable.
     */
    @Test
    fun `Create Default Profile makes a named one`() {
        profilesTab(docWith()) { get ->
            onNode(hasContentDescription("Create Default Profile")).performClick()
            waitForIdle()

            val profiles = get().projectionSettings.outputProfiles
            assertEquals(1, profiles.size)
            assertEquals("New Profile", profiles[0].name, "it arrives named rather than blank")
        }
    }
}
