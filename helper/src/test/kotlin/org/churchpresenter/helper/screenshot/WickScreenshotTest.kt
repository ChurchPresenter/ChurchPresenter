@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.helper.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.ContentScope
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.Persistence
import org.churchpresenter.helper.intent.KnownProfile
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.Resolution
import org.churchpresenter.helper.report.ChatSendResult
import org.churchpresenter.helper.report.ChatSender
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.ui.CommandsTable
import org.churchpresenter.helper.ui.HelperInputs
import org.churchpresenter.helper.ui.HelperOverlay
import org.churchpresenter.helper.ui.IntroPointer
import org.churchpresenter.helper.ui.LampMascot
import org.churchpresenter.helper.ui.LampMood
import org.churchpresenter.helper.ui.Launcher
import org.churchpresenter.helper.ui.WickIntro
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

class WickScreenshotTest {

    private val executor = HelperActionExecutor { ActionOutcome.Done() }

    private val sendChat: ChatSender = { _, _ -> ChatSendResult.SENT }

    @Composable
    private fun panel(state: HelperState) {
        HelperOverlay(
            state = state,
            inputs = HelperInputs(
                // Tips off: today's tip is shuffled, and a screenshot must be the same every run.
                settings = HelperSettings(tipsEnabled = false),
                onSettingsChange = {},
                suggestions = emptyList(),
                screens = emptyList(),
                context = ResolveContext(language = "en", profiles = listOf(KnownProfile("p", "Livestream"))),
                anythingLive = false,
                onSendChat = sendChat,
            ),
            executor = executor,
            resolver = RuleIntentResolver(),
            animate = false,
        )
    }

    @Test
    fun `the lamp at rest`() = captureComponent(SECTION, "lamp_rest") {
        LampMascot(size = 96.dp, animate = false)
    }

    @Test
    fun `the lamp happy`() = captureComponent(SECTION, "lamp_happy") {
        LampMascot(size = 96.dp, mood = LampMood.HAPPY, animate = false)
    }

    @Test
    fun `the launcher closed`() = captureComponent(SECTION, "launcher_closed") {
        Launcher(open = false, waiting = 0, animate = false, mood = LampMood.IDLE, onClick = {})
    }

    @Test
    fun `the launcher with something waiting`() = captureComponent(SECTION, "launcher_waiting") {
        Launcher(open = false, waiting = 1, animate = false, mood = LampMood.IDLE, onClick = {})
    }

    @Test
    fun `the panel greeting`() = captureComponent(SECTION, "panel_greeting") {
        panel(HelperState().apply { isOpen = true })
    }

    @Test
    fun `the panel asking before a change`() = captureComponent(SECTION, "panel_confirm") {
        panel(
            HelperState().apply {
                isOpen = true
                thread.said("make the song background blue")
                request(
                    HelperAction.SetBackgroundColor(ContentScope.SONG, "#1565C0", "blue", Persistence.SAVED),
                    executor,
                )
            },
        )
    }

    @Test
    fun `the panel listing every command`() = captureComponent(SECTION, "panel_commands") {
        panel(
            HelperState().apply {
                isOpen = true
                thread.said("help")
                run(HelperAction.ShowCommands, executor)
            },
        )
    }

    /** A short chat that went wrong: a page to parents, a profile by name, then a request Wick did not follow. */
    private fun chat() = HelperState().apply {
        isOpen = true
        thread.said("tell the parents of Sam to come to the nursery")
        request(HelperAction.ShowAnnouncement("Parents of Sam, please come to the nursery."), executor)
        answer("Cancel")
        thread.said("make the Livestream lyrics bigger, order 98765")
        onResolved(Resolution.Unknown, executor)
    }

    @Test
    fun `the panel not sure, offering to send the chat`() = captureComponent(SECTION, "panel_not_sure") {
        panel(chat())
    }

    @Test
    fun `the panel's menu`() = captureComponent(
        SECTION,
        "panel_menu",
        rootIndex = 1,
        drive = {
            onNodeWithTag("helper.menu").performClick()
            waitForIdle()
        },
    ) { panel(chat()) }

    @Test
    fun `the send-this-chat preview, masked`() = captureComponent(SECTION, "panel_share_chat") {
        panel(chat().apply { sharingChat = true })
    }

    @Test
    fun `the send-this-chat card once sent`() = captureComponent(
        SECTION,
        "panel_share_chat_sent",
        drive = {
            onNodeWithText("Send").performClick()
            waitForIdle()
        },
    ) { panel(chat().apply { sharingChat = true }) }

    @Test
    fun `the whole commands table`() = captureComponent(SECTION, "commands_table") {
        Box(Modifier.width(352.dp)) { CommandsTable(ask = { _, _ -> }) }
    }

    @Test
    fun `the pointer after the intro`() = captureComponent(SECTION, "intro_pointer") {
        IntroPointer(onOpen = {}, onReplay = {})
    }

    private fun intro(name: String, drive: ComposeUiTest.() -> Unit = {}) =
        captureComponent(SECTION, name, drive = drive) {
            Box(Modifier.size(width = 620.dp, height = 640.dp)) { WickIntro(onDone = {}, animate = false) }
        }

    @Test
    fun `the intro, first step`() = intro("intro_1_hello")

    @Test
    fun `the intro, second step with a question picked`() = intro("intro_2_ask") {
        onNodeWithTag("helper.intro.next").performClick()
        waitForIdle()
        onNodeWithText("How do I add a song?").performClick()
        waitForIdle()
    }

    @Test
    fun `the intro, last step`() = intro("intro_3_quiet") {
        repeat(2) {
            onNodeWithTag("helper.intro.next").performClick()
            waitForIdle()
        }
    }

    private companion object {
        const val SECTION = "wick"
    }
}
