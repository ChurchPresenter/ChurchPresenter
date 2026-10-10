@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.settings.PropCorner
import org.churchpresenter.settings.PropDefinition
import org.churchpresenter.settings.PropKind
import kotlin.test.Test
import kotlin.test.assertEquals

/** The Props dialog: switching props on and off, and adding, editing and deleting them. */
class PropsDialogContentTest {

    private val logo = PropDefinition("prop1", "Logo", PropKind.IMAGE, imagePath = "/logo.png")

    private class Seen {
        var props: List<PropDefinition>? = null
        val switched = mutableListOf<Pair<String, Boolean>>()
        var dismissed = 0
    }

    private fun dialog(
        props: List<PropDefinition> = listOf(logo),
        onAir: Set<String> = emptySet(),
        picked: String? = "/chosen.png",
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                PropsDialogContent(
                    props = props,
                    onPropsChange = { seen.props = it },
                    onAir = onAir,
                    onSwitch = { id, on -> seen.switched += id to on },
                    onChoosePicture = { picked },
                    onDismiss = { seen.dismissed++ },
                )
            }
        }
        block(seen)
    }

    private fun ComposeUiTest.field(tag: String) = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun `with no props it says so, and Close closes`() = dialog(props = emptyList()) { seen ->
        onNodeWithText("No props yet", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        assertEquals(1, seen.dismissed)
    }

    @Test
    fun `a prop's switch puts it up and takes it down`() = dialog(onAir = setOf("prop1")) { seen ->
        onNodeWithTag(propSwitchTag("prop1")).performClick()
        assertEquals(listOf("prop1" to false), seen.switched)
    }

    @Test
    fun `a new badge is added with its words, corner and height`() = dialog(props = emptyList()) { seen ->
        onNodeWithTag(PROP_ADD_TAG).performClick()
        field(PROP_NAME_TAG).performTextInput("Live")
        onNodeWithTag(propKindTag(PropKind.BADGE)).performClick()
        onNodeWithTag(propCornerTag(PropCorner.TOP_LEFT)).performClick()
        field(PROP_TEXT_TAG).performTextInput("LIVE")
        field(PROP_SIZE_TAG).performTextReplacement("1x2")
        onNodeWithTag(PROP_SAVE_TAG).performClick()
        assertEquals(
            listOf(PropDefinition("prop1", "Live", PropKind.BADGE, PropCorner.TOP_LEFT, 12, text = "LIVE")),
            seen.props,
        )
    }

    @Test
    fun `a countdown keeps its time, and a prop with no name is named for its kind`() =
        dialog(props = emptyList()) { seen ->
        onNodeWithTag(PROP_ADD_TAG).performClick()
        onNodeWithTag(propKindTag(PropKind.COUNTDOWN)).performClick()
        field(PROP_COUNTDOWN_TAG).performTextInput("10:30:00")
        onNodeWithTag(PROP_SAVE_TAG).performClick()
        assertEquals("countdown", seen.props?.single()?.name)
        assertEquals("10:30", seen.props?.single()?.countdownTo)
    }

    @Test
    fun `a picture is chosen for a prop being edited`() = dialog { seen ->
        onNodeWithTag(propEditTag("prop1")).performClick()
        onNodeWithText("Choose…").performClick()
        waitForIdle()
        onNodeWithTag(PROP_SAVE_TAG).performClick()
        assertEquals(listOf(logo.copy(imagePath = "/chosen.png")), seen.props)
    }

    @Test
    fun `a picture's path can be typed instead of chosen`() = dialog { seen ->
        onNodeWithTag(propEditTag("prop1")).performClick()
        field(PROP_PICTURE_TAG).performTextReplacement("/typed.png")
        onNodeWithTag(PROP_SAVE_TAG).performClick()
        assertEquals(listOf(logo.copy(imagePath = "/typed.png")), seen.props)
    }

    @Test
    fun `deleting a prop that is up takes it down first`() = dialog(onAir = setOf("prop1")) { seen ->
        onNodeWithTag(propEditTag("prop1")).performClick()
        onNodeWithText("Delete").performClick()
        assertEquals(listOf("prop1" to false), seen.switched)
        assertEquals(emptyList(), seen.props)
    }

    @Test
    fun `each new prop gets an id no other has`() {
        assertEquals("prop2", newPropId(listOf(logo)))
        assertEquals("prop3", newPropId(listOf(logo, logo.copy(id = "prop2"))))
        assertEquals("prop3", newPropId(listOf(logo.copy(id = "prop2"))), "past an id already taken")
    }
}
