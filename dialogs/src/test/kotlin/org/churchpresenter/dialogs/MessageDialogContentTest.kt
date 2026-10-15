@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.liveshow.Cue
import org.churchpresenter.settings.MessageTemplate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText

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
    fun `a typed message goes live until cleared, and Go Live waits for text`() =
        dialog(templates = emptyList()) { seen ->
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertNull(seen.live, "nothing to put up yet")
        field(MESSAGE_TEXT_TAG).performTextInput("Car lights on")
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals(Cue.Message("Car lights on"), seen.live)
    }

    @Test
    fun `saving adds a message, and saving a picked one updates it`() = dialog(templates = emptyList()) { seen ->
        field(MESSAGE_TEXT_TAG).performTextInput("Welcome to all our guests today")
        onNodeWithText("Custom").performClick()
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
        val updated = nursery.copy(name = "Child {number} to the…", text = "Child {number} to the nursery")
        assertEquals(listOf(updated), seen.templates)
        onNodeWithText("Delete").performClick()
        assertEquals(emptyList(), seen.templates)
    }

    @Test
    fun `the message on air is shown and cleared from here, and Close closes`() =
        dialog(onAir = Cue.Message("Nursery #4")) { seen ->
            onNodeWithText("On air: Nursery #4").assertExists()
            onNodeWithContentDescription("Clear message").performScrollTo().performClick()
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
        assertEquals("message3", newTemplateId(listOf(nursery.copy(id = "message2"))), "past an id already taken")
        assertTrue(newTemplateId(emptyList()).isNotBlank())
    }

    @Test
    fun `a usual length is one click, and Until cleared takes it back off`() = dialog(templates = emptyList()) { seen ->
        field(MESSAGE_TEXT_TAG).performTextInput("Car lights on")
        onNodeWithText("10 s").performClick()
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals(Cue.Message("Car lights on", null, 10), seen.live)
        onNodeWithText("1 min").performClick()
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals(60, seen.live?.durationSeconds)
        onAllNodesWithText("Until cleared")[0].performClick()
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertNull(seen.live?.durationSeconds)
    }

    @Test
    fun `a saved length none of the usual ones opens on Custom`() = dialog { _ ->
        onNodeWithTag(MESSAGE_DURATION_TAG).assertDoesNotExist()
        onNodeWithTag(messageTemplateTag("message1")).performClick()
        waitForIdle()
        onNodeWithTag(MESSAGE_DURATION_TAG).assertExists()
        onNodeWithText("30 s").performClick()
        waitForIdle()
        onNodeWithTag(MESSAGE_DURATION_TAG).assertDoesNotExist()
    }

    @Test
    fun `Insert blank adds a blank to fill in, spaced from the words before it`() =
        dialog(templates = emptyList()) { seen ->
            field(MESSAGE_TEXT_TAG).performTextInput("Child")
            onNodeWithTag(messageBlankTag("{number}")).performClick()
            field(messageTokenTag("number")).performTextInput("7")
            onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
            assertEquals("Child 7", seen.live?.text)
        }

    @Test
    fun `a blank on an empty message is the whole message`() = dialog(templates = emptyList()) { seen ->
        onNodeWithTag(messageBlankTag("{room}")).performClick()
        field(messageTokenTag("room")).performTextInput("Room 3")
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).performClick()
        assertEquals("Room 3", seen.live?.text)
    }

    @Test
    fun `the preview shows the message and how long it stays up`() = dialog(templates = emptyList()) { _ ->
        onNodeWithText("Your message appears here").assertExists()
        onAllNodesWithText("Until cleared").assertCountEquals(2)
        field(MESSAGE_TEXT_TAG).performTextInput("Welcome")
        onNodeWithText("30 s").performClick()
        waitForIdle()
        onNodeWithText("Your message appears here").assertDoesNotExist()
        onAllNodesWithText("Welcome").assertCountEquals(2)
        onAllNodesWithText("30 s").assertCountEquals(2)
    }

    @Test
    fun `Go Live is off until there is something to show`() = dialog(templates = emptyList()) { _ ->
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).assertIsNotEnabled()
        field(MESSAGE_TEXT_TAG).performTextInput("Hello")
        onNodeWithTag(MESSAGE_GO_LIVE_TAG).assertIsEnabled()
    }

    @Test
    fun `the header names the tool and its close closes`() = dialog { seen ->
        onNodeWithText("Shows a line of text over whatever is live.").assertExists()
        onNodeWithContentDescription("Close").performClick()
        assertEquals(1, seen.dismissed)
    }

    private fun <T> assertSingle(list: List<T>?): T {
        assertEquals(1, list?.size, "$list")
        return list!!.single()
    }
}
