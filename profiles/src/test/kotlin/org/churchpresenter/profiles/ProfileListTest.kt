package org.churchpresenter.profiles

import org.churchpresenter.settings.OutputLook
import org.churchpresenter.settings.SlideLook
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTextExactly
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.rightClick
import androidx.compose.ui.test.withKeyDown
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.settings.ProjectionSettings
import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.withLinksResolved
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The profile list: its order is the operator's, changed by dragging a handle, by Alt+↑/↓ on the
 * focused row, or from the row's menu -- which also renames in place, duplicates, makes a linked
 * profile and deletes. A master moves with the profiles linked to it.
 */
@OptIn(ExperimentalTestApi::class)
class ProfileListTest {

    private fun doc(): AppSettings = AppSettings(
        projectionSettings = ProjectionSettings(
            outputProfiles = listOf(
                OutputProfile(id = "main", name = "Sanctuary"),
                OutputProfile(id = "youth", name = "Youth night", parentId = "main",
                        overrides = setOf("look.slide.qa"), look = OutputLook(slide = SlideLook(qa = false))),
                OutputProfile(id = "stream", name = "Livestream"),
                OutputProfile(id = "spare", name = "Spare"),
            ),
            screenAssignments = listOf(ScreenAssignment(activeProfileId = "main")),
        ).withLinksResolved(),
    )

    private fun AppSettings.order() = projectionSettings.outputProfiles.map { it.id }

    private fun SkikoComposeUiTest.menu(id: String, item: String) {
        onNodeWithTag(profileRowTag(id)).performMouseInput { rightClick(center) }
        waitForIdle()
        // The menu is the last thing drawn; a page row can carry the same words.
        val items = onAllNodes(hasTextExactly(item))
        items[items.fetchSemanticsNodes().size - 1].performClick()
        waitForIdle()
    }

    @Test
    fun `the plus makes a new, unnamed profile and opens it`() = profilesTab(doc()) { get ->
        tap(NEW_PROFILE_TAG)
        assertEquals(5, get().projectionSettings.outputProfiles.size)
        assertEquals("", get().projectionSettings.outputProfiles.last().name)
        onNodeWithTag(PROFILE_NAME_FIELD_TAG).assertExists()
    }

    @Test
    fun `Alt arrows move the focused profile among its peers`() = profilesTab(doc()) { get ->
        onNodeWithTag(profileRowTag("stream")).performClick()
        onNodeWithTag(profileRowTag("stream")).performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.DirectionUp) }
        }
        waitForIdle()
        assertEquals(listOf("stream", "main", "youth", "spare"), get().order())
        onNodeWithTag(profileRowTag("stream")).performKeyInput {
            withKeyDown(Key.AltLeft) { pressKey(Key.DirectionDown) }
        }
        waitForIdle()
        assertEquals(listOf("main", "youth", "stream", "spare"), get().order())
        // Without Alt an arrow is left to the list.
        onNodeWithTag(profileRowTag("stream")).performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals(listOf("main", "youth", "stream", "spare"), get().order())
    }

    @Test
    fun `dragging a handle shows where the profile will land, and drops it there`() = profilesTab(doc()) { get ->
        onNodeWithTag(profileHandleTag("spare"), useUnmergedTree = true).performMouseInput {
            moveTo(center)
            press()
            moveBy(Offset(0f, -60f))
            moveBy(Offset(0f, -120f))
        }
        waitForIdle()
        assertEquals(1, countTag(PROFILE_DROP_LINE_TAG))
        onNodeWithTag(profileHandleTag("spare"), useUnmergedTree = true).performMouseInput { release() }
        waitForIdle()
        assertEquals(0, countTag(PROFILE_DROP_LINE_TAG))
        assertEquals("spare", get().order().first())
    }

    @Test
    fun `the menu renames a profile where it stands`() = profilesTab(doc()) { get ->
        menu("stream", "Rename")
        onNodeWithTag(PROFILE_RENAME_FIELD_TAG).performTextReplacement("Stream")
        onNodeWithTag(PROFILE_RENAME_FIELD_TAG).performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("Stream", get().profile("stream").name)
        assertEquals(0, countTag(PROFILE_RENAME_FIELD_TAG))
    }

    @Test
    fun `the menu duplicates, makes a linked profile, moves and deletes`() = profilesTab(doc()) { get ->
        menu("stream", "Duplicate")
        assertEquals("Livestream copy", get().projectionSettings.outputProfiles.last().name)
        menu("stream", "Create linked profile")
        val linked = get().projectionSettings.outputProfiles.first { it.parentId == "stream" }
        assertEquals("Livestream (linked)", linked.name)
        menu("spare", "Move up")
        assertTrue(get().order().indexOf("spare") < get().order().indexOf("stream"))
        menu("spare", "Move down")
        assertTrue(get().order().indexOf("spare") > get().order().indexOf("stream"))
        menu("spare", "Delete")
        onAllNodes(hasTextExactly("OK"))[0].performClick()
        waitForIdle()
        assertTrue("spare" !in get().order())
        // A follower offers no linked profile of its own.
        onNodeWithTag(profileRowTag("youth")).performMouseInput { rightClick(center) }
        waitForIdle()
        assertEquals(0, onAllNodes(hasTextExactly("Create linked profile")).fetchSemanticsNodes().size)
    }

    @Test
    fun `a follower sits under its master with its change count`() = profilesTab(doc()) { _ ->
        val inRow = hasTextExactly("1 changes") and hasAnyAncestor(hasTestTag(profileRowTag("youth")))
        assertEquals(1, onAllNodes(inRow, useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test
    fun `renaming ends on Esc, and any other key keeps the field open`() = profilesTab(doc()) { get ->
        menu("stream", "Rename")
        onNodeWithTag(PROFILE_RENAME_FIELD_TAG).performTextReplacement("Stream")
        onNodeWithTag(PROFILE_RENAME_FIELD_TAG).performKeyInput { pressKey(Key.A) }
        waitForIdle()
        assertEquals(1, countTag(PROFILE_RENAME_FIELD_TAG))
        onNodeWithTag(PROFILE_RENAME_FIELD_TAG).performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals(0, countTag(PROFILE_RENAME_FIELD_TAG))
        assertEquals("Stream", get().profile("stream").name, "every keystroke was kept")
    }

    @Test
    fun `a follower some of whose sections follow other masters says how many`() {
        val base = doc()
        val proj = base.projectionSettings
        val more = base.copy(
            projectionSettings = proj.copy(
                outputProfiles = proj.outputProfiles + listOf(
                    OutputProfile(
                        id = "kids", name = "Kids", parentId = "main",
                        sectionMasters = mapOf("bible" to "stream", "songs" to "spare", "qa" to "main"),
                    ),
                    OutputProfile(
                        id = "foyer", name = "Foyer", parentId = "main",
                        sectionMasters = mapOf("bible" to "stream"),
                    ),
                ),
            ),
        )
        profilesTab(more) { _ ->
            val twoMore = hasText("+2 masters", substring = true) and hasAnyAncestor(hasTestTag(profileRowTag("kids")))
            assertEquals(1, onAllNodes(twoMore, useUnmergedTree = true).fetchSemanticsNodes().size)
            val oneMore = hasText("+1 master", substring = true) and hasAnyAncestor(hasTestTag(profileRowTag("foyer")))
            assertEquals(1, onAllNodes(oneMore, useUnmergedTree = true).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `an unnamed profile is duplicated under the default name and deleted under its id`() {
        val base = doc()
        val proj = base.projectionSettings
        val unnamed = base.copy(
            projectionSettings = proj.copy(
                outputProfiles = proj.outputProfiles + OutputProfile(id = "blank", name = ""),
            ),
        )
        profilesTab(unnamed) { get ->
            onNodeWithTag(profileRowTag("blank")).performClick()
            waitForIdle()
            menu("blank", "Duplicate")
            assertEquals("New Profile copy", get().projectionSettings.outputProfiles.last().name)
            menu("blank", "Delete")
            assertTrue(onAllNodes(hasText("blank", substring = true)).fetchSemanticsNodes().isNotEmpty())
            onAllNodes(hasTextExactly("OK"))[0].performClick()
            waitForIdle()
            assertTrue("blank" !in get().order())
            assertEquals(0, onAllNodes(hasTestTag(profileRowTag("blank"))).fetchSemanticsNodes().size)
        }
    }

    @Test
    fun `a page the newly picked profile does not have falls back to General`() {
        val base = doc()
        val proj = base.projectionSettings
        val withStage = base.copy(
            projectionSettings = proj.copy(
                outputProfiles = proj.outputProfiles +
                    OutputProfile(id = "stage", name = "Stage", displayMode = Constants.DISPLAY_MODE_STAGE_MONITOR),
            ),
        )
        profilesTab(withStage) { _ ->
            openCustomizePane(CustomizePane.BIBLE)
            onNodeWithTag(profileRowTag("stage")).performClick()
            waitForIdle()
            assertEquals(0, onAllNodes(hasTestTag(railTag(CustomizePane.BIBLE.name))).fetchSemanticsNodes().size)
            assertTrue(onAllNodes(hasTestTag(PROFILE_NAME_FIELD_TAG)).fetchSemanticsNodes().isNotEmpty())
        }
    }
}
