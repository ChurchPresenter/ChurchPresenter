@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.helper.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperReply
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.HelperText
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.display.DisplayStep
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.resolve
import org.churchpresenter.settings.HelperSettings
import org.churchpresenter.sharedui.guide.GuideSession
import org.churchpresenter.sharedui.guide.GuideTargets
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.guideTarget
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class WickScreensTest {

    private val laptop = HelperScreen(0, isPrimary = true, x = 0, y = 0, width = 1440, height = 900)
    private val projector =
        HelperScreen(1, isPrimary = false, x = 1440, y = 0, width = 1920, height = 1080, isAudience = true)
    private val done = mutableListOf<HelperAction>()
    private val executor = HelperActionExecutor { done += it; ActionOutcome.Done() }

    private fun ComposeUiTest.setup(state: HelperState, screens: List<HelperScreen>) {
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                HelperOverlay(
                    state = state,
                    inputs = HelperInputs(
                        settings = HelperSettings(tipsEnabled = false),
                        onSettingsChange = {},
                        suggestions = emptyList(),
                        screens = screens,
                        context = ResolveContext(language = "en"),
                        anythingLive = false,
                    ),
                    executor = executor,
                    resolver = RuleIntentResolver(),
                    animate = false,
                )
            }
        }
        waitForIdle()
    }

    private fun ComposeUiTest.press(label: String) {
        val node = onAllNodesWithText(label).onLast()
        runCatching { node.performScrollTo() }
        node.performClick()
        waitForIdle()
    }

    private fun displaySetup() = HelperState().apply {
        isOpen = true
        run(HelperAction.StartDisplaySetup, HelperActionExecutor { ActionOutcome.DisplaySetup })
    }

    @Test
    fun `display setup picks, confirms, tests and finishes`() = runComposeUiTest {
        val state = displaySetup()
        setup(state, listOf(laptop, projector))
        assertEquals(DisplayStep.PICK, state.displayFlow.step)
        onNodeWithText("The audience screen now").assertExists()
        onNodeWithTag("helper.screen.1").performClick()
        waitForIdle()
        assertEquals(DisplayStep.CONFIRM, state.displayFlow.step)
        press("Back")
        assertEquals(DisplayStep.PICK, state.displayFlow.step)
        onNodeWithTag("helper.screen.1").performClick()
        waitForIdle()
        press("Do it")
        assertEquals(DisplayStep.TEST, state.displayFlow.step)
        press("Show numbers again")
        assertTrue(done.count { it == HelperAction.IdentifyScreens } >= 2)
        press("No")
        assertEquals(DisplayStep.PICK, state.displayFlow.step)
        onNodeWithTag("helper.screen.1").performClick()
        waitForIdle()
        press("Do it")
        press("Yes, I see it")
        assertEquals(DisplayStep.DONE, state.displayFlow.step)
        press("OK")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `display setup with one screen points at the settings`() = runComposeUiTest {
        val state = displaySetup()
        setup(state, listOf(laptop))
        assertEquals(DisplayStep.DETECT, state.displayFlow.step)
        press("Open Projection settings")
        assertTrue(done.any { it is HelperAction.OpenSettings })
    }

    @Test
    fun `display setup can be cancelled while picking`() = runComposeUiTest {
        val state = displaySetup()
        assertIs<HelperReply.DisplaySetup>(state.reply)
        setup(state, listOf(laptop, projector.copy(isAudience = false, name = "Projector")))
        onNodeWithText("The audience screen now").assertDoesNotExist()
        press("Cancel")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `display setup with one screen can be cancelled`() = runComposeUiTest {
        val state = displaySetup()
        setup(state, listOf(laptop))
        press("Cancel")
        assertEquals(HelperReply.Idle, state.reply)
    }

    @Test
    fun `the intro steps through and back, then finishes or is skipped`() = runComposeUiTest {
        var finished = 0
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                Box(Modifier.size(700.dp)) { WickIntro(onDone = { finished++ }, animate = false) }
            }
        }
        onNodeWithTag("helper.intro").assertExists()
        onNodeWithTag("helper.intro.next").performClick()
        waitForIdle()
        onNodeWithText("How do I add a song?").performClick()
        waitForIdle()
        onNodeWithText("Can I use my phone?").performClick()
        waitForIdle()
        onNodeWithText("Back").performClick()
        waitForIdle()
        onNodeWithText("Hi, I'm Wick").assertExists()
        onNodeWithTag("helper.intro.next").performClick()
        onNodeWithTag("helper.intro.next").performClick()
        waitForIdle()
        onNodeWithText("I stay out of the way").assertExists()
        onNodeWithTag("helper.intro.next").performClick()
        waitForIdle()
        assertEquals(1, finished)
        onNodeWithTag("helper.intro.skip").performClick()
        waitForIdle()
        assertEquals(2, finished)
    }

    @Test
    fun `the animated lamp, launcher, intro and spotlight run`() = runComposeUiTest {
        val session = GuideSession().apply { activeTarget = GuideTargets.TAKE }
        mainClock.autoAdvance = false
        setContent {
            ChurchPresenterTheme(themeMode = ThemeMode.DARK) {
                CompositionLocalProvider(LocalGuideSession provides session) {
                    GuideSpotlightWindow {
                        Box(Modifier.size(900.dp)) {
                            Text("Take", Modifier.guideTarget(GuideTargets.TAKE))
                            LampMascot(size = 60.dp, mood = LampMood.THINKING)
                            Launcher(open = true, waiting = 0, animate = true, mood = LampMood.IDLE, onClick = {})
                            WickIntro(onDone = {}, animate = true)
                        }
                    }
                }
            }
        }
        repeat(6) { mainClock.advanceTimeBy(700) }
        onNodeWithTag("helper.lamp").assertExists()
        onNodeWithTag("helper.intro").assertExists()
    }

    @Test
    fun `joined text reads as one line`() = runComposeUiTest {
        var line = ""
        setContent {
            val parts = listOf(HelperText.Plain("John 3:16"), HelperText.Plain("a countdown"))
            line = HelperText.Joined(parts).resolve()
        }
        waitForIdle()
        assertEquals("John 3:16, a countdown", line)
    }
}
