@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.data.Language
import org.churchpresenter.theme.ThemeMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The first-run wizard's navigation: which step follows which, and what each one offers to press.
 *
 * This is the very first thing a new user sees, and it is the only place the app asks for a language
 * and a theme before anything else exists — so a wizard that cannot be advanced, cannot be skipped,
 * or drops its final button strands someone on their first launch.
 *
 * `SetupWizardDialog` opens a `Window`, which cannot be composed headless, so the window's body was
 * lifted into `SetupWizardContent` — an extraction, no logic moved or changed — and that is what
 * these drive. The `Window` call itself, the eight-step *content* copy, and the wizard's placement
 * on screen are what remain uncovered.
 *
 * The step body is an `AnimatedContent`, so during a transition both the outgoing and the incoming
 * step are briefly on screen. Every assertion here is therefore made after the transition has
 * settled, and reads the step counter — which is outside the animation and always singular — rather
 * than counting step content.
 */
class SetupWizardContentTest {

    private object Label {
        const val TITLE = "Getting Started"
        const val SKIP = "Skip"
        const val BACK = "Back"
        const val NEXT = "Next"
        const val DONE = "Get Started"
        const val LAST_STEP = 8

        fun step(n: Int) = "Step $n of $LAST_STEP"
    }

    /** What the wizard reported back, so a test can assert on the choice rather than on a stub. */
    private class Choices {
        var language: Language? = null
        var theme: ThemeMode? = null
        var dismissed = 0
        var openedSettings = 0
        var openedConverter = 0
    }

    @OptIn(ExperimentalTestApi::class)
    private fun wizard(
        theme: ThemeMode = ThemeMode.SYSTEM,
        language: Language = Language.ENGLISH,
        summary: SetupSummary = SetupSummary(bibleTranslations = 0, songBooks = 0, songs = 0),
        block: ComposeUiTest.(Choices) -> Unit,
    ) {
        val choices = Choices()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    SetupWizardContent(
                        theme = theme,
                        selectedLanguage = language,
                        onLanguageSelected = { choices.language = it },
                        onThemeSelected = { choices.theme = it },
                        onOpenSettings = { choices.openedSettings++ },
                        onOpenConverter = { choices.openedConverter++ },
                        onDismiss = { choices.dismissed++ },
                        // A stand-in for the disk scan, so the summary step is deterministic and
                        // does not depend on what happens to be in the fork's home directory.
                        loadSummary = { summary },
                    )
                }
            }
            block(choices)
        }
    }

    /** Presses Next and lets the slide transition finish, so only one step is on screen after. */
    private fun ComposeUiTest.next() {
        onNodeWithText(Label.NEXT).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.back() {
        onNodeWithText(Label.BACK).performClick()
        waitForIdle()
    }

    // ── Where the wizard opens ──────────────────────────────────────────────────

    @Test
    fun `the wizard opens on the first of eight steps`() = wizard { _ ->
        onNodeWithText(Label.TITLE).assertIsDisplayed()
        onNodeWithText(Label.step(1)).assertIsDisplayed()
    }

    @Test
    fun `the first step offers no way back`() = wizard { _ ->
        onAllNodesWithTextCount(Label.BACK).let {
            assertEquals(0, it, "there is nothing behind the first step to go back to")
        }
    }

    // ── Moving through ──────────────────────────────────────────────────────────

    @Test
    fun `Next advances one step at a time`() = wizard { _ ->
        next()
        onNodeWithText(Label.step(2)).assertIsDisplayed()
        next()
        onNodeWithText(Label.step(3)).assertIsDisplayed()
    }

    @Test
    fun `Back returns to the step before`() = wizard { _ ->
        next()
        next()
        onNodeWithText(Label.step(3)).assertIsDisplayed()
        back()
        onNodeWithText(Label.step(2)).assertIsDisplayed()
    }

    @Test
    fun `every step from the second on offers Back`() = wizard { _ ->
        repeat(Label.LAST_STEP - 1) { i ->
            next()
            assertEquals(
                1,
                onAllNodesWithTextCount(Label.BACK),
                "step ${i + 2} must offer a way back",
            )
        }
    }

    // ── The last step ───────────────────────────────────────────────────────────

    @Test
    fun `the wizard runs to an eighth and final step`() = wizard { _ ->
        repeat(Label.LAST_STEP - 1) { next() }
        onNodeWithText(Label.step(Label.LAST_STEP)).assertIsDisplayed()
    }

    @Test
    fun `the final step swaps Next for the finish button`() = wizard { _ ->
        repeat(Label.LAST_STEP - 1) { next() }
        assertEquals(0, onAllNodesWithTextCount(Label.NEXT), "there is nowhere left to advance to")
        onNodeWithText(Label.DONE).assertIsDisplayed()
    }

    @Test
    fun `Skip stays offered on the last step`() = wizard { choices ->
        // It moved into the rail, which is drawn for every step including this one. The old wizard
        // dropped it here because Skip shared the footer with Next; there is no longer a reason to,
        // and someone who reaches the summary and wants out should not have to press the button
        // labelled as though it were a commitment.
        repeat(Label.LAST_STEP - 1) { next() }
        onNodeWithText(Label.SKIP).performClick()
        waitForIdle()
        assertEquals(1, choices.dismissed, "Skip must still close the wizard from the last step")
    }

    @Test
    fun `finishing on the last step closes the wizard`() = wizard { choices ->
        repeat(Label.LAST_STEP - 1) { next() }
        onNodeWithText(Label.DONE).performClick()
        waitForIdle()
        assertEquals(1, choices.dismissed, "pressing the finish button must close the wizard")
    }

    // ── Skipping ────────────────────────────────────────────────────────────────

    @Test
    fun `Skip closes the wizard from the very first step`() = wizard { choices ->
        onNodeWithText(Label.SKIP).performClick()
        waitForIdle()
        assertEquals(1, choices.dismissed, "Skip must let someone out without walking the wizard")
    }

    @Test
    fun `Skip is still offered partway through`() = wizard { choices ->
        next()
        next()
        onNodeWithText(Label.SKIP).performClick()
        waitForIdle()
        assertEquals(1, choices.dismissed)
    }

    // ── The two steps that ask for something ────────────────────────────────────

    @Test
    fun `choosing a language on the first step reports that language`() = wizard { choices ->
        assertNull(choices.language, "nothing is chosen until it is pressed")
        onNodeWithText(Language.GERMAN.nativeName).performClick()
        waitForIdle()
        assertEquals(Language.GERMAN, choices.language, "the pill pressed must be the language reported")
    }

    @Test
    fun `every language the app supports is offered on the first step`() = wizard { _ ->
        Language.entries.forEach { language ->
            assertTrue(
                onAllNodesWithTextCount(language.nativeName) >= 1,
                "${language.nativeName} must be offered in its own script",
            )
        }
    }

    @Test
    fun `choosing a theme on the second step reports that theme`() = wizard { choices ->
        next()
        assertNull(choices.theme)
        onNodeWithText("Dark Theme").performClick()
        waitForIdle()
        assertEquals(ThemeMode.DARK, choices.theme, "the theme pressed must be the theme reported")
    }

    @Test
    fun `the rail and the step body scroll independently`() {
        // Two regions, not one: the rail holds eight rows and the body holds a step that can be
        // longer than the window. They have to scroll separately — when the whole window scrolled
        // as a unit, reaching the bottom of a long step took the rail off screen with it.
        //
        // The count is what the old single-column wizard got wrong in the other direction: it had
        // one scrollable, attached to the chip row itself, and no scrollbar, so four languages sat
        // below the fold with nothing to say they were there.
        wizard { _ ->
            assertEquals(2, onAllNodesCount(hasScrollAction()), "the language step: rail and body")
            next()
            assertEquals(2, onAllNodesCount(hasScrollAction()), "and the same on the theme step")
        }
    }

    @Test
    fun `walking the whole wizard never reports a language or theme by itself`() = wizard { choices ->
        repeat(Label.LAST_STEP - 1) { next() }
        assertNull(choices.language, "merely passing the language step must not choose one")
        assertNull(choices.theme, "merely passing the theme step must not choose one")
        assertEquals(0, choices.dismissed, "walking to the end must not close the wizard on its own")
    }

    // ── The rail ────────────────────────────────────────────────────────────────

    @Test
    fun `the rail lists every step by name`() = wizard { _ ->
        listOf("Language", "Appearance", "Welcome", "Bible", "Song books", "Projection", "Media", "All set")
            .forEach { name ->
                assertTrue(onAllNodesWithTextCount(name) >= 1, "the rail must name the $name step")
            }
    }

    @Test
    fun `pressing a rail row jumps straight to that step`() = wizard { _ ->
        // The whole point of the rail over Back/Next: revisiting the language six steps later was
        // six presses of Back, and the wizard never showed which step held what.
        railStep(6)
        onNodeWithText(Label.step(6)).assertIsDisplayed()
    }

    @Test
    fun `the rail can go backwards as well as forwards`() = wizard { _ ->
        railStep(8)
        onNodeWithText(Label.step(8)).assertIsDisplayed()
        railStep(1)
        onNodeWithText(Label.step(1)).assertIsDisplayed()
    }

    @Test
    fun `the rail shows the language and theme already chosen`() =
        wizard(theme = ThemeMode.OCEAN, language = Language.GERMAN) { _ ->
            assertTrue(onAllNodesWithTextCount(Language.GERMAN.nativeName) >= 1, "the rail names the language")
            assertTrue(onAllNodesWithTextCount("Ocean Theme") >= 1, "the rail names the theme")
        }

    // ── Searching the language list ─────────────────────────────────────────────

    @Test
    fun `typing in the search box narrows the language list`() = wizard { _ ->
        onNode(hasSetTextAction()).performTextInput("Deutsch")
        waitForIdle()
        // The search field itself now reads "Deutsch", so the chip is one of at least two matches.
        assertTrue(onAllNodesWithTextCount(Language.GERMAN.nativeName) >= 1, "the match stays")
        assertEquals(0, onAllNodesWithTextCount(Language.THAI.nativeName), "everything else goes")
    }

    @Test
    fun `the search matches the English name as well as the native one`() = wizard { _ ->
        // Someone who cannot yet read the interface may still know their language's English name,
        // and the enum carries it — GERMAN for Deutsch.
        onNode(hasSetTextAction()).performTextInput("german")
        waitForIdle()
        assertTrue(onAllNodesWithTextCount(Language.GERMAN.nativeName) >= 1)
    }

    @Test
    fun `a search matching nothing says so rather than showing an empty area`() = wizard { _ ->
        onNode(hasSetTextAction()).performTextInput("zzzzz")
        waitForIdle()
        assertTrue(
            onAllNodesCount(hasText("No languages match", substring = true)) >= 1,
            "an empty result must explain itself",
        )
    }

    // ── The theme step ──────────────────────────────────────────────────────────

    @Test
    fun `every theme the app ships is offered, under a light or dark heading`() = wizard { _ ->
        next()
        ThemeMode.entries.forEach { mode ->
            assertTrue(onAllNodesWithTextCount(themeLabelFor(mode)) >= 1, "$mode must be offered")
        }
        listOf("Light", "Dark", "Follows your system").forEach { heading ->
            assertTrue(onAllNodesWithTextCount(heading) >= 1, "the $heading section must be headed")
        }
    }

    @Test
    fun `the three newest themes are selectable, not merely drawn`() = wizard { choices ->
        next()
        listOf(ThemeMode.SLATE, ThemeMode.SAND, ThemeMode.PLUM).forEach { mode ->
            onNodeWithText(themeLabelFor(mode)).performClick()
            waitForIdle()
            assertEquals(mode, choices.theme, "$mode must report itself when pressed")
        }
    }

    // ── The song step's way out of an unsupported format ─────────────────────────

    @Test
    fun `the song step offers the converter`() = wizard { choices ->
        railStep(5)
        assertEquals(0, choices.openedConverter)
        onNodeWithText("Open Converter").performClick()
        waitForIdle()
        assertEquals(1, choices.openedConverter, "the button must open the converter")
    }

    // ── The summary ─────────────────────────────────────────────────────────────

    @Test
    fun `the last step reports what was actually found on disk`() =
        wizard(summary = SetupSummary(bibleTranslations = 3, songBooks = 6, songs = 8262)) { _ ->
            railStep(8)
            assertTrue(onAllNodesWithTextCount("3 installed") >= 1, "the translation count is shown")
            assertTrue(onAllNodesWithTextCount("6 books · 8262 songs") >= 1, "the song tally is shown")
        }

    @Test
    fun `a summary of nothing is still reported honestly`() =
        wizard(summary = SetupSummary(bibleTranslations = 0, songBooks = 0, songs = 0)) { _ ->
            // The step this replaced congratulated a user with an empty library exactly as loudly
            // as one with a full one, which is how a mistyped folder path gets past a setup wizard.
            railStep(8)
            assertTrue(onAllNodesWithTextCount("0 installed") >= 1, "an empty setup must say zero")
        }

    /** The label the picker draws for a theme, kept in one place so a rename lands here too. */
    private fun themeLabelFor(mode: ThemeMode): String = when (mode) {
        ThemeMode.SYSTEM -> "System Theme"
        ThemeMode.LIGHT -> "Light Theme"
        ThemeMode.DARK -> "Dark Theme"
        ThemeMode.WARM -> "Warm Theme"
        ThemeMode.OCEAN -> "Ocean Theme"
        ThemeMode.ROSE -> "Rose Theme"
        ThemeMode.MIDNIGHT -> "Midnight Theme"
        ThemeMode.FOREST -> "Forest Theme"
        ThemeMode.MOCHA -> "Mocha Theme"
        ThemeMode.STUDIO -> "Studio Theme"
        ThemeMode.SLATE -> "Slate Theme"
        ThemeMode.SAND -> "Sand Theme"
        ThemeMode.PLUM -> "Plum Theme"
    }

    /** Presses the rail's Nth step (1-based) by tag, so a label appearing twice cannot confuse it. */
    private fun ComposeUiTest.railStep(number: Int) {
        onNodeWithTag(setupRailTag(number - 1)).performClick()
        waitForIdle()
    }

    private fun ComposeUiTest.onAllNodesCount(matcher: SemanticsMatcher): Int =
        onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).size
}
