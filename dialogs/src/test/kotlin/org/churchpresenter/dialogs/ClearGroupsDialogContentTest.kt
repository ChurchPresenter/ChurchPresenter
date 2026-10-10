@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveshow.Layer
import org.churchpresenter.settings.ClearGroup
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Clear groups dialog and the Clear layers menu. */
class ClearGroupsDialogContentTest {

    private val text = ClearGroup("clear1", "Clear text", listOf("SLIDE", "MESSAGES"))

    private class Seen {
        var groups: List<ClearGroup>? = null
        val cleared = mutableListOf<Any>()
        var dismissed = 0
        var edits = 0
    }

    private fun dialog(groups: List<ClearGroup> = listOf(text), block: ComposeUiTest.(Seen) -> Unit) =
        runComposeUiTest {
            val seen = Seen()
            setContent {
                MaterialTheme {
                    ClearGroupsDialogContent(groups, { seen.groups = it }, { seen.cleared += it }) { seen.dismissed++ }
                }
            }
            block(seen)
        }

    private fun menu(onAir: Set<Layer>, block: ComposeUiTest.(Seen) -> Unit) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                Column {
                    ClearLayersMenuItems(listOf(text), onAir, { seen.cleared += it }, { seen.cleared += it }) {
                        seen.edits++
                    }
                }
            }
        }
        block(seen)
    }

    private fun ComposeUiTest.nameField() =
        onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(CLEAR_GROUP_NAME_TAG)))

    @Test
    fun `with no groups it says so, and Close closes`() = dialog(emptyList()) { seen ->
        onNodeWithText("No clear groups yet", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        assertEquals(1, seen.dismissed)
    }

    @Test
    fun `a group's Clear button fires it`() = dialog { seen ->
        onNodeWithTag(clearGroupButtonTag("clear1")).performClick()
        assertEquals(listOf<Any>(text), seen.cleared)
    }

    @Test
    fun `a new group needs a layer, keeps them in stack order, and is named for them when unnamed`() =
        dialog(emptyList()) { seen ->
        onNodeWithTag(CLEAR_GROUP_ADD_TAG).performClick()
        onNodeWithTag(CLEAR_GROUP_SAVE_TAG).assertIsNotEnabled()
        onNodeWithTag(clearGroupLayerTag(Layer.PROPS)).performClick()
        onNodeWithTag(clearGroupLayerTag(Layer.GRAPHICS)).performClick()
        onNodeWithTag(clearGroupLayerTag(Layer.MEDIA)).performClick()
        onNodeWithTag(clearGroupLayerTag(Layer.MEDIA)).performClick()
        onNodeWithTag(CLEAR_GROUP_SAVE_TAG).assertIsEnabled().performClick()
        assertEquals(listOf(ClearGroup("clear1", "Lower third + Props", listOf("GRAPHICS", "PROPS"))), seen.groups)
    }

    @Test
    fun `an edited group keeps its place, and a named one its name`() =
        dialog(listOf(text, ClearGroup("clear2", "Other", listOf("PROPS")))) { seen ->
        onNodeWithTag(clearGroupEditTag("clear1")).performClick()
        nameField().performTextReplacement("Words")
        onNodeWithTag(clearGroupLayerTag(Layer.CAPTIONS)).performClick()
        onNodeWithTag(CLEAR_GROUP_SAVE_TAG).performClick()
        assertEquals(
            listOf(
                ClearGroup("clear1", "Words", listOf("SLIDE", "CAPTIONS", "MESSAGES")),
                ClearGroup("clear2", "Other", listOf("PROPS")),
            ),
            seen.groups,
        )
    }

    @Test
    fun `a new group is added after the others, and a saved one deleted`() = dialog { seen ->
        onNodeWithTag(CLEAR_GROUP_ADD_TAG).performClick()
        nameField().performTextInput("Props")
        onNodeWithTag(clearGroupLayerTag(Layer.PROPS)).performClick()
        onNodeWithTag(CLEAR_GROUP_SAVE_TAG).performClick()
        assertEquals(listOf(text, ClearGroup("clear2", "Props", listOf("PROPS"))), seen.groups)
        onNodeWithTag(clearGroupEditTag("clear1")).performClick()
        onNodeWithText("Delete").performClick()
        assertEquals(emptyList(), seen.groups)
    }

    @Test
    fun `the menu fires a group, clears a layer that is on air, dims one that is not, and opens the editor`() =
        menu(setOf(Layer.SLIDE)) { seen ->
        onNodeWithTag(clearGroupItemTag("clear1")).performClick()
        onNodeWithTag(clearLayerItemTag(Layer.SLIDE)).performClick()
        onNodeWithTag(clearLayerItemTag(Layer.PROPS)).assertIsNotEnabled()
        onNodeWithTag(CLEAR_GROUPS_EDIT_TAG).performClick()
        assertEquals(listOf<Any>(text, Layer.SLIDE), seen.cleared)
        assertEquals(1, seen.edits)
    }

    @Test
    fun `each new group gets an id no other has`() {
        assertEquals("clear2", newClearGroupId(listOf(text)))
        assertEquals("clear3", newClearGroupId(listOf(text, text.copy(id = "clear2"))))
        assertEquals("clear3", newClearGroupId(listOf(text.copy(id = "clear2"))), "past an id already taken")
    }
}
