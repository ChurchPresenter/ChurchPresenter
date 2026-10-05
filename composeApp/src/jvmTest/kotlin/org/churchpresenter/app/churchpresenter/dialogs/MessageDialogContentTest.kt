@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.MessageTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The Message dialog: picking a saved message, filling its tokens, saving, and going live. */
class MessageDialogContentTest {

    private val nursery = MessageTemplate("message1", "Nursery", "Parent of child #{number}", 120)

    private class Seen {
        var templates: List<MessageTemplate>? = null
        var live: Cue.Message? = null
        var cleared = 0
        var dismissed = 0
    }

    private fun dialog(
        templates: List<MessageTemplate> = listOf(nursery),
        onAir: Cue.Message? = null,
        block: ComposeUiTest.(Seen) -> Unit,
    ) = runComposeUiTest {
        val seen = Seen()
        setContent {
            MaterialTheme {
                MessageDialogContent(
                    templates = templates,
                    onTemplatesChange = { seen.templates = it },
                    onAir = onAir,
                    onGoLive = { seen.live = it },
                    onClear = { seen.cleared++ },
                    onDismiss = { seen.dismissed++ },
                )
            }
        }
        block(seen)
    }

    private fun ComposeUiTest.field(tag: String) = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

    @Test
    fun `a saved message is picked, its token filled in, and goes live as shown`() = dialog { seen ->
        onNodeWithTag(messageTemplateTag("message1")).performClick()
        field(messageTokenTag("number")).performTextInput("42")
        waitForIdle()
        onNodeWithText("Parent of child #42").assertExists()
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals(Cue.Message("Parent of child #42", "Nursery", 120), seen.live)
    }

    @Test
    fun `a typed message goes live until cleared, and Go Live waits for text`() = dialog(templates = emptyList()) { seen ->
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertNull(seen.live, "nothing to put up yet")
        field(MESSAGE_TEXT_TAG).performTextInput("Car lights on")
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals(Cue.Message("Car lights on"), seen.live)
    }

    @Test
    fun `saving adds a message, and saving a picked one updates it`() = dialog(templates = emptyList()) { seen ->
        field(MESSAGE_TEXT_TAG).performTextInput("Welcome to all our guests today")
        field(MESSAGE_DURATION_TAG).performTextInput("3x0")
        onNodeWithText("Save message").performClick()
        val saved = assertSingle(seen.templates)
        assertEquals(MessageTemplate("message1", "Welcome to all our…", "Welcome to all our guests today", 30), saved)
    }

    @Test
    fun `a picked message is updated or deleted`() = dialog { seen ->
        onNodeWithTag(messageTemplateTag("message1")).performClick()
        field(MESSAGE_TEXT_TAG).performTextReplacement("Child {number} to the nursery")
        onNodeWithText("Save message").performClick()
        assertEquals(listOf(nursery.copy(name = "Child {number} to the…", text = "Child {number} to the nursery")), seen.templates)
        onNodeWithText("Delete").performClick()
        assertEquals(emptyList(), seen.templates)
    }

    @Test
    fun `the message on air is shown and cleared from here, and Close closes`() =
        dialog(onAir = Cue.Message("Nursery #4")) { seen ->
            onNodeWithText("On air: Nursery #4").assertExists()
            onNodeWithContentDescription("Clear message").performClick()
            onNodeWithText("Close").performClick()
            assertEquals(1, seen.cleared)
            assertEquals(1, seen.dismissed)
        }

    @Test
    fun `a saved message is named by its first words and given a free id`() {
        assertEquals("One two", templateName("  One   two "))
        assertEquals("a b c d…", templateName("a b c d e"))
        assertEquals("message2", newTemplateId(listOf(nursery)))
        assertEquals("message3", newTemplateId(listOf(nursery, nursery.copy(id = "message2"))))
        assertTrue(newTemplateId(emptyList()).isNotBlank())
    }

    private fun <T> assertSingle(list: List<T>?): T {
        assertEquals(1, list?.size, "$list")
        return list!!.single()
    }
}
