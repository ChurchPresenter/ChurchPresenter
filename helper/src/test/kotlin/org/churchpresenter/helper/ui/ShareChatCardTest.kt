@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.helper.ui

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CompletableDeferred
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.intent.KnownProfile
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.report.ChatSendResult
import org.churchpresenter.helper.report.ChatSender
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ShareChatCardTest {

    private val executor = HelperActionExecutor { ActionOutcome.Done() }
    private val sent = mutableListOf<Pair<String, String>>()
    private var result = ChatSendResult.SENT

    private fun ComposeUiTest.wick(state: HelperState, sender: ChatSender? = { message, email ->
        sent += message to email
        result
    }) {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                HelperOverlay(
                    state = state,
                    inputs = HelperInputs(
                        settings = HelperSettings(tipsEnabled = false),
                        onSettingsChange = {},
                        suggestions = emptyList(),
                        screens = emptyList(),
                        context = ResolveContext(language = "en", profiles = listOf(KnownProfile("p", "Livestream"))),
                        anythingLive = false,
                        nowMillis = { 0L },
                        onSendChat = sender,
                    ),
                    executor = executor,
                    resolver = RuleIntentResolver(),
                    animate = false,
                )
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.type(text: String) {
        onNodeWithTag("helper.input").performTextInput(text)
        onNodeWithTag("helper.send").performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onAllNodesWithText(label).onLast()
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.openFromMenu() {
        onNodeWithTag("helper.menu").performClick()
        waitForIdle()
        onNodeWithTag("helper.shareChatMenu").performClick()
        waitForIdle()
    }

    @Test
    fun `the chat is previewed masked, and sent with the note and email only on Send`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("tell the parents of Sam to come to the nursery")
        press("Cancel")
        type("make the Livestream look like order 98765")
        openFromMenu()
        val preview = onNodeWithTag("helper.shareChat.preview")
        preview.assertTextContains("You: [announcement]", substring = true)
        preview.assertTextContains("[profile]", substring = true)
        preview.assertTextContains("[number]", substring = true)
        assertTrue(sent.isEmpty(), "nothing leaves before Send")

        onNodeWithTag("helper.shareChat.note").performTextInput("It did not understand me")
        onNodeWithTag("helper.shareChat.email").performTextInput(" me@example.org ")
        press("Send")
        val (message, email) = sent.single()
        assertTrue(message.startsWith("Note: It did not understand me\n\nYou: [announcement]\n"), message)
        assertFalse("Sam" in message || "Livestream" in message || "98765" in message, message)
        assertEquals("me@example.org", email)
        onNodeWithText("Sent, thank you!").assertExists()
        press("OK")
        assertFalse(state.sharingChat)
    }

    @Test
    fun `the link under I'm not sure opens the card, and Cancel sends nothing`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("qwertyuiop asdf")
        onNodeWithTag("helper.shareChatLink").performClick()
        waitForIdle()
        assertTrue(state.sharingChat)
        onNodeWithTag("helper.shareChatLink").assertDoesNotExist()
        onNodeWithTag("helper.shareChat.preview").assertTextContains("You: qwertyuiop asdf", substring = true)
        press("Cancel")
        assertFalse(state.sharingChat)
        assertTrue(sent.isEmpty())
    }

    @Test
    fun `a refusal or a failure says so and leaves the card to try again`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("qwertyuiop asdf")
        openFromMenu()
        result = ChatSendResult.RATE_LIMITED
        press("Send")
        onNodeWithText("Too many sent just now, try again later.").assertExists()
        result = ChatSendResult.FAILED
        press("Send")
        onNodeWithText("Couldn't send. Check the internet and try again.").assertExists()
        assertEquals(2, sent.size)
        assertTrue(state.sharingChat)
    }

    @Test
    fun `while sending, the fields hold still and a second press sends nothing more`() = runComposeUiTest {
        val gate = CompletableDeferred<ChatSendResult>()
        var calls = 0
        val state = HelperState().apply { isOpen = true }
        wick(state) { _, _ ->
            calls++
            gate.await()
        }
        type("qwertyuiop asdf")
        openFromMenu()
        press("Send")
        onNodeWithText("Sending…").assertExists()
        onNodeWithTag("helper.shareChat.email").assertIsNotEnabled()
        press("Send")
        press("Cancel")
        assertEquals(1, calls)
        assertTrue(state.sharingChat)
        gate.complete(ChatSendResult.SENT)
        waitUntil { onAllNodesWithText("Sent, thank you!").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun `without a sender there is no way to send a chat`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state, sender = null)
        type("qwertyuiop asdf")
        onNodeWithTag("helper.shareChatLink").assertDoesNotExist()
        onNodeWithTag("helper.menu").performClick()
        waitForIdle()
        onNodeWithTag("helper.shareChatMenu").assertDoesNotExist()
    }
}
