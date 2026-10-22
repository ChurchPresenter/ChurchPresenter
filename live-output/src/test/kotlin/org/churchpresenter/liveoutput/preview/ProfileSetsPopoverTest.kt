@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.liveoutput.preview

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProfileSet
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The profile sets menu: every control edits the [ProjectionSettings] it is handed and nothing else. */
class ProfileSetsPopoverTest {

    private val sunday = ProfileSet("set1", "Sunday", mapOf("screen:0" to "fs", "screen:1" to "vert"))
    private val youth = ProfileSet("set2", "", mapOf("screen:0" to "ltd", "screen:1" to "ltd"))

    private fun base() = ProjectionSettings(
        outputProfiles = listOf(OutputProfile(id = "fs"), OutputProfile(id = "ltd"), OutputProfile(id = "vert")),
        screenAssignments = listOf("fs", "vert").map { ScreenAssignment(activeProfileId = it) },
        profileSets = listOf(sunday, youth),
    )

    /** Composes the menu open over [start]; [block] drives it and reads the latest settings. */
    private fun menu(start: ProjectionSettings, block: ComposeUiTest.(() -> ProjectionSettings) -> Unit) =
        runComposeUiTest {
            var current by mutableStateOf(start)
            setContent {
                MaterialTheme {
                    ProfileSetsPopover(expanded = true, onDismiss = {}, proj = current, onChange = { current = it })
                }
            }
            waitForIdle()
            block { current }
        }

    private fun ProjectionSettings.used() = screenAssignments.map { it.activeProfileId }

    @Test
    fun `a click on a set puts it on the outputs, and the tick follows`() = menu(base()) { now ->
        onNodeWithContentDescription("In use now").assertExists()
        onNodeWithTag(profileSetTag("set2")).performClick()
        waitForIdle()
        assertEquals(listOf("ltd", "ltd"), now().used())
        onNodeWithTag(profileSetTag("set1")).performClick()
        waitForIdle()
        assertEquals(listOf("fs", "vert"), now().used())
    }

    @Test
    fun `a set with no name is called by its place`() = menu(base()) {
        onNodeWithText("Set 2").assertExists()
        onAllNodesWithText("2 outputs").assertCountEquals(2)
    }

    @Test
    fun `save adds the outputs as they are, its name ready to type`() = menu(base()) { now ->
        onNodeWithTag(TAG_SAVE_PROFILE_SET).performClick()
        waitForIdle()
        val added = now().profileSets.last()
        assertEquals(mapOf("screen:0" to "fs", "screen:1" to "vert"), added.profiles)
        onNodeWithTag(profileSetNameTag(added.id)).assertIsFocused().performTextReplacement("Conference")
        waitForIdle()
        assertEquals("Conference", now().profileSets.last().name)
        onNodeWithTag(profileSetNameTag(added.id)).performImeAction()
        waitForIdle()
        onNodeWithTag(profileSetNameTag(added.id)).assertDoesNotExist()
        onNodeWithText("Conference").assertExists()
    }

    @Test
    fun `the pencil renames a set in place`() = menu(base()) { now ->
        onAllNodesWithContentDescription("Rename set")[0].performClick()
        waitForIdle()
        onNodeWithTag(profileSetNameTag("set1")).performTextReplacement("Sunday AM")
        waitForIdle()
        assertEquals("Sunday AM", now().profileSets.first().name)
        assertEquals(listOf("fs", "vert"), now().used(), "renaming does not apply it")
    }

    @Test
    fun `delete takes the set out and leaves the outputs alone`() = menu(base()) { now ->
        onAllNodesWithContentDescription("Delete set")[0].performClick()
        waitForIdle()
        assertEquals(listOf("set2"), now().profileSets.map { it.id })
        assertEquals(listOf("fs", "vert"), now().used())
    }

    @Test
    fun `with no sets it says what one is`() = menu(base().copy(profileSets = emptyList())) { now ->
        onNodeWithText("Save which profile every output uses now", substring = true).assertExists()
        onNodeWithTag(TAG_SAVE_PROFILE_SET).performClick()
        waitForIdle()
        assertTrue(now().profileSets.isNotEmpty())
    }
}
