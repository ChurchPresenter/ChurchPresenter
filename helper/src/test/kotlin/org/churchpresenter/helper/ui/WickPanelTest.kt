@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.helper.ui

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.UndoEntry
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.suggest.HelperSignals
import org.churchpresenter.helper.suggest.Suggestion
import org.churchpresenter.helper.suggest.suggestionsFor
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WickPanelTest {

    private val done = mutableListOf<HelperAction>()
    private var outcome: (HelperAction) -> ActionOutcome = { ActionOutcome.Done() }
    private val executor = HelperActionExecutor { done += it; outcome(it) }
    private var settings = HelperSettings(tipsEnabled = false)

    private fun ComposeUiTest.wick(
        state: HelperState,
        suggestions: List<Suggestion> = emptyList(),
        tab: Tabs? = null,
        live: Boolean = false,
    ) {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                HelperOverlay(
                    state = state,
                    inputs = HelperInputs(
                        settings = settings,
                        onSettingsChange = { settings = it },
                        suggestions = suggestions,
                        screens = emptyList(),
                        context = ResolveContext(currentTab = tab, language = "en"),
                        anythingLive = live,
                        nowMillis = { 0L },
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
        // Below the panel's fold, a click would land on whatever covers it: scroll to it first.
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    @Test
    fun `the lamp opens the panel with a greeting, and closes it`() = runComposeUiTest {
        val state = HelperState()
        wick(state)
        onNodeWithTag("helper.lamp").performClick()
        waitForIdle()
        assertTrue(state.isOpen)
        onNodeWithText("Show John 3:16").assertExists()
        onNodeWithTag("helper.lamp").performClick()
        waitForIdle()
        assertFalse(state.isOpen)
    }

    @Test
    fun `a change is asked about, done, undone and closed`() = runComposeUiTest {
        var reverted = false
        outcome = { ActionOutcome.Done(undo = UndoEntry(HelperText.Plain("blue")) { reverted = true; true }) }
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("make the song background blue for now")
        onNodeWithTag("helper.confirm").assertExists()
        press("Do it")
        assertIs<HelperAction.SetBackgroundColor>(done.single())
        press("Undo")
        assertTrue(reverted)
        press("OK")
        assertEquals(HelperReply.Idle, state.reply)
        onAllNodesWithTag("helper.asked").assertCountEquals(4)
    }

    @Test
    fun `a change can be cancelled`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("clear the screen")
        press("Cancel")
        assertTrue(done.isEmpty())
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `a question picks a scope from chips`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("make the background red")
        assertIs<HelperReply.Clarify>(state.reply)
        press("song")
        assertIs<HelperReply.Confirm>(state.reply)
    }

    @Test
    fun `the open tab decides the scope without asking`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state, tab = Tabs.BIBLE)
        type("make the background red")
        assertIs<HelperReply.Confirm>(state.reply)
    }

    @Test
    fun `what is not understood offers chips that ask for it`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("qwerty zxcvb")
        assertIs<HelperReply.Unknown>(state.reply)
        press("Show John 3:16")
        assertIs<HelperReply.Confirm>(state.reply)
    }

    @Test
    fun `a shortcut is shown and acknowledged`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("what is the shortcut for clear")
        assertIs<HelperReply.Shortcut>(state.reply)
        onNodeWithTag("helper.shortcut").assertExists()
        press("OK")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `a refusal with an offer can be taken up or declined`() = runComposeUiTest {
        outcome = { ActionOutcome.Refused(HelperText.Plain("no"), HelperAction.Take) }
        val state = HelperState().apply { isOpen = true }
        wick(state)
        state.run(HelperAction.ClearOutput, executor)
        waitForIdle()
        press("Yes")
        assertEquals(HelperReply.Confirm(HelperAction.Take), state.reply)
        press("Cancel")
        state.run(HelperAction.ClearOutput, executor)
        waitForIdle()
        press("Cancel")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `a tour steps on, and can be stopped`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("how do I show a pdf")
        assertIs<HelperReply.Touring>(state.reply)
        onNodeWithTag("helper.tourHint").assertExists()
        press("Next")
        assertEquals(1, (state.reply as HelperReply.Touring).index)
        press("Stop")
        assertEquals(HelperReply.Idle, state.reply)
        type("where is the schedule")
        press("Got it")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `pressing the ringed control moves the tour on`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("how do I show a pdf")
        state.session.pressed(state.session.activeTarget!!)
        waitForIdle()
        assertEquals(1, (state.reply as HelperReply.Touring).index)
    }

    @Test
    fun `the help table and its rows`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("help")
        onNodeWithTag("helper.commands").assertExists()
        press("“5 minute countdown”")
        assertEquals(HelperReply.Confirm(HelperAction.StartCountdown(5)), state.reply)
    }

    @Test
    fun `thanks and hello are answered`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("hello")
        assertEquals(HelperReply.Greeting, state.reply)
        type("thanks")
        assertIs<HelperReply.Message>(state.reply)
    }

    @Test
    fun `a suggestion is shown, put off, or followed`() = runComposeUiTest {
        val signals = HelperSignals(scheduleEmpty = true, songLibraryEmpty = true)
        val suggestions = suggestionsFor(signals, HelperSettings(), 0L)
        val state = HelperState().apply { isOpen = true }
        wick(state, suggestions)
        onNodeWithTag("helper.suggestion").assertExists()
        press("Not now")
        assertTrue(settings.snoozedUntil.isNotEmpty())
        press("Show me")
        assertIs<HelperReply.Touring>(state.reply)
    }

    @Test
    fun `a suggestion can be put away for good`() = runComposeUiTest {
        val suggestions = suggestionsFor(HelperSignals(primaryBibleMissing = true), HelperSettings(), 0L)
        wick(HelperState().apply { isOpen = true }, suggestions)
        press("Don't show tips like this again")
        assertTrue(settings.dismissedSuggestions.isNotEmpty())
    }

    @Test
    fun `the tip of the day steps either way`() = runComposeUiTest {
        settings = HelperSettings(tipsEnabled = true)
        val state = HelperState().apply { isOpen = true }
        wick(state)
        onNodeWithText("Tip of the day").assertExists()
        press("Next tip")
        assertEquals(1, state.tipOffset)
        press("Previous")
        assertEquals(0, state.tipOffset)
    }

    @Test
    fun `tips can be switched off`() = runComposeUiTest {
        settings = HelperSettings(tipsEnabled = true)
        wick(HelperState().apply { isOpen = true })
        press("Turn off tips")
        assertFalse(settings.tipsEnabled)
    }

    @Test
    fun `the conversation can be cleared from the menu`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        type("thanks")
        onNodeWithTag("helper.menu").performClick()
        waitForIdle()
        onNodeWithTag("helper.clear").performClick()
        waitForIdle()
        assertTrue(state.thread.entries.isEmpty())
    }

    @Test
    fun `hiding asks first, and can be cancelled`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        onNodeWithTag("helper.hide").performClick()
        waitForIdle()
        onNodeWithTag("helper.confirmHide").assertExists()
        press("Cancel")
        assertFalse(state.confirmingHide)
        onNodeWithTag("helper.hide").performClick()
        waitForIdle()
        press("Hide Wick")
        assertFalse(settings.enabled)
    }

    @Test
    fun `the close button and escape close the panel`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        onNodeWithTag("helper.close").performClick()
        waitForIdle()
        assertFalse(state.isOpen)
        state.isOpen = true
        waitForIdle()
        onNodeWithTag("helper.input").performClick()
        onNodeWithTag("helper.input").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertFalse(state.isOpen)
    }

    @Test
    fun `enter sends, and an empty box sends nothing`() = runComposeUiTest {
        val state = HelperState().apply { isOpen = true }
        wick(state)
        onNodeWithTag("helper.send").performClick()
        onNodeWithTag("helper.input").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertTrue(state.thread.entries.isEmpty())
        onNodeWithTag("helper.input").performClick()
        onNodeWithTag("helper.input").performTextInput("hello")
        onNodeWithTag("helper.input").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(HelperReply.Greeting, state.reply)
    }

    @Test
    fun `while closed, a teaser and a badge say something waits`() = runComposeUiTest {
        val suggestions = suggestionsFor(HelperSignals(scheduleEmpty = true), HelperSettings(), 0L)
        val state = HelperState()
        wick(state, suggestions)
        onNodeWithTag("helper.badge").assertExists()
        onNodeWithTag("helper.teaser").performClick()
        waitForIdle()
        assertTrue(state.isOpen)
    }

    @Test
    fun `nothing waits while something is live`() = runComposeUiTest {
        val suggestions = suggestionsFor(HelperSignals(scheduleEmpty = true), HelperSettings(), 0L)
        wick(HelperState(), suggestions, live = true)
        onAllNodesWithTag("helper.badge").assertCountEquals(0)
    }

    @Test
    fun `after the intro, the pointer opens Wick or replays the intro`() = runComposeUiTest {
        val state = HelperState().apply { introPointer = true }
        wick(state)
        press("Replay intro")
        assertTrue(state.replayIntro)
        assertFalse(state.introPointer)
        state.introPointer = true
        waitForIdle()
        onNodeWithTag("helper.introPointer").performClick()
        waitForIdle()
        assertTrue(state.isOpen)
    }

    @Test
    fun `a hidden helper draws nothing`() = runComposeUiTest {
        settings = HelperSettings(enabled = false)
        wick(HelperState())
        onAllNodesWithTag("helper.lamp").assertCountEquals(0)
    }
}
