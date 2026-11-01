@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.demo

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import org.churchpresenter.helper.HelperActionExecutor
import org.churchpresenter.helper.HelperState
import org.churchpresenter.helper.action.ActionOutcome
import org.churchpresenter.helper.action.HelperAction
import org.churchpresenter.helper.action.UndoEntry
import org.churchpresenter.helper.action.scopeName
import org.churchpresenter.helper.action.withBackgroundColor
import org.churchpresenter.helper.display.HelperScreen
import org.churchpresenter.helper.display.screenLabel
import org.churchpresenter.helper.helperText
import org.churchpresenter.helper.intent.ResolveContext
import org.churchpresenter.helper.intent.RuleIntentResolver
import org.churchpresenter.helper.ui.GuideSpotlightHost
import org.churchpresenter.helper.ui.HelperInputs
import org.churchpresenter.helper.ui.HelperOverlay
import org.churchpresenter.helper.ui.WickIntro
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.guide.LocalGuideSession
import org.churchpresenter.sharedui.guide.WickPlace
import org.churchpresenter.sharedui.models.Tabs
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.helper_done_assign
import org.churchpresenter.strings.generated.resources.helper_done_bg
import org.churchpresenter.strings.generated.resources.helper_undo_label_bg
import java.io.File
import kotlin.test.Test

/**
 * Wick, the helper: started from the Help menu, its intro, the lamp in the corner, a question answered
 * with the spotlight, a request carried out after asking, display setup, and holding still while
 * something is live. The real overlay, intro and spotlight around the real main window, as the app's
 * own window puts them; [film] plays their animations.
 */
class WickDemoTour {

    @Test
    fun wick() = recordTour(
        "wick",
        tab = "Wick",
        liveLabel = "Wick holds still",
        steps = 10,
        heading = "Meet " to ", the Helper",
        // The Bible tab raises a "Keyboard focus lost" banner whenever Wick's box takes the keys; Media has none.
        startTab = START_TAB,
        // Wick started and its intro due, as Help → Show Helper leaves them.
        settings = { it.copy(helper = it.helper.copy(enabled = true, startedByUser = true, introSeen = false)) },
        appHost = { settings, change, content -> WickHost(settings, change, content) },
    ) { wickTour() }

    private fun DemoTour.wickTour() {
        say("intro")
        intro("A little helper in the corner of the app.")
        segment("show-helper")
        say("start")
        caption("Help, then Show Helper", "In the menu at the top of the screen")
        introShown.value = true
        pause(0.3)
        say("hello")
        val hello = find(hasText("Hi, I'm Wick"))
        noteWhile("Hi, I'm Wick", "A helper bot, not an AI chatbot", hello) { play(LAMP_FILM_S) }
        say("next-1")
        step("Next", "Click it", find(hasText("Next")), numbered = true)
        say("ask")
        step("How do I add a song?", "Click a question", find(hasText("How do I add a song?")), numbered = true)
        say("reply")
        val reply = hasText("I'll open the Songs tab", substring = true)
        wickNote("Wick says what it will do", "Before it does anything", reply)
        say("next-2")
        step("Next", "Click it", find(hasText("Next")))
        say("quiet")
        wickNote("I stay out of the way", "Quiet while something is live", hasText("I stay out of the way"))
        say("finish")
        step("Let's go", "Click it", find(hasText("Let's go")), numbered = true)
        segment("lamp")
        say("lamp")
        noteWhile("Wick's lamp", "In the bottom right corner", lamp()) { play(LAMP_FILM_S) }
        say("open")
        step("The lamp", "Click it to open Wick", lamp(), numbered = true)
        say("greeting")
        wickNote("Tip of the day", "For the tab you're on", hasText("Tip of the day"))
        segment("ask")
        // The spotlight's callout draws a Wick that never stops moving, so the clock is held while it is
        // up — stepped by filming alone — and the spotlight taken down before the clock is let go.
        holdClock {
            say("type")
            type("Ask Wick", "Where is the schedule?", input(), "where is the schedule", enter = true)
            film(ANSWER_S)
            say("spotlight")
            // The ring is on the schedule itself, down the window's left; the camera goes to it there.
            val schedule = find(hasText("Schedule")) {
                it.right < dp(SCHEDULE_RIGHT_DP) && it.top < dp(SCHEDULE_TOP_DP)
            }
            noteWhile("The spotlight", "On the schedule, with what it's for", schedule) { play(SPOTLIGHT_FILM_S) }
        }
        segment("request")
        say("request")
        type("Ask Wick", "Make the song background blue", input(), "make the song background blue", enter = true)
        say("confirm")
        step("Do it", "Wick asks first", find(hasText("Do it")), numbered = true)
        say("done")
        wickNote("Done, with Undo", "In case you change your mind", hasText("background is now", substring = true))
        segment("display")
        say("display-type")
        type("Ask Wick", "Set up my screens", input(), "set up my screens", enter = true)
        say("display")
        step("The congregation's screen", "Click it", find(hasText("Projector", substring = true)), numbered = true)
        say("display-confirm")
        step("Do it", "Wick checks first", findAll(hasText("Do it")).last(), numbered = true)
        say("test")
        step("Yes, I see it", "The big number is there", find(hasText("Yes, I see it")), numbered = true)
        say("all-set")
        wickNote("All set", "Live now shows on that screen", hasText("All set", substring = true))
        segment("quiet")
        say("close")
        step("The lamp", "Click it to close Wick", find(hasContentDescription(CLOSE_LABEL, substring = true)))
        say("bible-tab")
        step("Bible", "Click the tab", tab("Bible"), numbered = true)
        say("pick-verse")
        val verse = find(hasText("In the beginning", substring = true)) { it.left > dp(VERSES_LEFT_DP) }
        step("A verse", "Click it", verse)
        say("quiet-live")
        goLive(sub = "Wick holds still")
        liveNow.value = true
        say("still")
        noteWhile("Holding still", "Nothing pops up mid-service", lamp()) { play(STILL_FILM_S) }
        segment("end")
        say("outro")
        outro()
    }

    /**
     * A note on Wick's own words, or, if they are not on screen, a caption — with every text in the
     * window's lower right logged to `missed.txt`, to see what Wick says instead.
     */
    private fun DemoTour.wickNote(heading: String, sub: String, matcher: SemanticsMatcher) {
        val target = runCatching { find(matcher) }.getOrNull()
        if (target != null) {
            note(heading, sub, target)
            return
        }
        val shown = ui.onAllNodes(hasText("", substring = true)).fetchSemanticsNodes(atLeastOneRootRequired = false)
            .filter { it.boundsInWindow.left > dp(INPUT_LEFT_DP) }
            .mapNotNull { it.config.getOrNull(SemanticsProperties.Text)?.joinToString("") }
        val line = "$heading — Wick shows: ${shown.joinToString(" | ")}\n"
        File(System.getenv("CP_DEMO_OUT"), "missed.txt").appendText(line)
        caption(heading, sub)
    }

    /** The lamp while Wick is closed: the button that opens it, never the panel's own icons. */
    private fun DemoTour.lamp(): Target = find(hasContentDescription(OPEN_LABEL, substring = true))

    /** Wick's text box: the one low in the window's right corner, never the Bible tab's search. */
    private fun DemoTour.input(): Target =
        find(hasSetTextAction()) { it.left > dp(INPUT_LEFT_DP) && it.top > dp(INPUT_TOP_DP) }

    /** Runs [body] with the test clock stepped only by filming; the spotlight is down before it is let go. */
    private fun DemoTour.holdClock(body: () -> Unit) {
        ui.mainClock.autoAdvance = false
        try {
            body()
        } finally {
            state.session.activeTarget = null
            ui.mainClock.advanceTimeByFrame()
            ui.mainClock.autoAdvance = true
            ui.waitForIdle()
        }
    }

    /** [seconds] of Wick moving: its lamp animates only while filmed, so waits never spin on it. */
    private fun DemoTour.play(seconds: Double) {
        animating.value = true
        try {
            film(seconds)
        } finally {
            animating.value = false
        }
    }
}

/** The main window as the app's own window holds it: Wick's spotlight around it, the lamp in the corner. */
@Composable
private fun WickHost(
    settings: AppSettings,
    change: ((AppSettings) -> AppSettings) -> Unit,
    content: @Composable () -> Unit,
) {
    val latest by rememberUpdatedState(settings)
    val executor = remember { demoExecutor({ latest }, change) }
    val resolver = remember { RuleIntentResolver() }
    CompositionLocalProvider(LocalGuideSession provides state.session) {
        GuideSpotlightHost(Modifier.fillMaxSize(), wick = false) {
            content()
            if (!introShown.value) {
                HelperOverlay(
                    state = state,
                    inputs = HelperInputs(
                        settings = settings.helper,
                        onSettingsChange = { helper -> change { it.copy(helper = helper) } },
                        suggestions = emptyList(),
                        screens = SCREENS,
                        context = ResolveContext(currentTab = Tabs.MEDIA, language = "en"),
                        anythingLive = liveNow.value,
                        place = WickPlace.Main(Tabs.MEDIA),
                    ),
                    executor = executor,
                    resolver = resolver,
                    modifier = Modifier.align(Alignment.BottomEnd),
                    animate = animating.value,
                )
            } else {
                WickIntro(onDone = { introShown.value = false }, animate = animating.value)
            }
        }
    }
}

/**
 * What the app's executor does for the requests this tour makes: the same settings change for a
 * background, display setup, and the screen it picks. Pointing at things the helper does itself.
 */
private fun demoExecutor(
    settings: () -> AppSettings,
    change: ((AppSettings) -> AppSettings) -> Unit,
) = HelperActionExecutor { action ->
    when (action) {
        is HelperAction.SetBackgroundColor -> {
            val before = settings()
            change { withBackgroundColor(it, action.scope, action.hex) }
            ActionOutcome.Done(
                message = helperText(Res.string.helper_done_bg, scopeName(action.scope), action.colorName),
                undo = UndoEntry(helperText(Res.string.helper_undo_label_bg, scopeName(action.scope))) {
                    change { before }
                    true
                },
            )
        }
        HelperAction.StartDisplaySetup -> ActionOutcome.DisplaySetup
        is HelperAction.AssignAudienceScreen ->
            ActionOutcome.Done(message = helperText(Res.string.helper_done_assign, action.screen.screenLabel()))
        else -> ActionOutcome.Done()
    }
}

private val state = HelperState()
private val introShown = mutableStateOf(false)
private val liveNow = mutableStateOf(false)

/** True while [DemoTour.film] plays the lamp: at rest otherwise, so waiting for the window never spins on it. */
private val animating = mutableStateOf(false)

/** This computer's screen and the projector beside it, for display setup to choose between. */
private val SCREENS = listOf(
    HelperScreen(index = 0, isPrimary = true, x = 0, y = 0, width = 1920, height = 1080, name = "Built-in Display"),
    HelperScreen(index = 1, isPrimary = false, x = 1920, y = 0, width = 1920, height = 1080, name = "Projector"),
)

private const val LAMP_FILM_S = 3.0
private const val START_TAB = "Media"
private const val VERSES_LEFT_DP = 600f
private const val OPEN_LABEL = "Open Wick"
private const val CLOSE_LABEL = "Close the helper"
private const val SCHEDULE_RIGHT_DP = 380f
private const val SCHEDULE_TOP_DP = 120f
private const val ANSWER_S = 0.8
private const val INPUT_LEFT_DP = 1000f
private const val INPUT_TOP_DP = 500f
private const val SPOTLIGHT_FILM_S = 2.5
private const val STILL_FILM_S = 2.0
