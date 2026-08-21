@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onNodeWithText
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
    }

    @OptIn(ExperimentalTestApi::class)
    private fun wizard(
        theme: ThemeMode = ThemeMode.SYSTEM,
        language: Language = Language.ENGLISH,
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
                        onDismiss = { choices.dismissed++ },
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
    fun `the final step swaps Next for the finish button and drops Skip`() = wizard { _ ->
        repeat(Label.LAST_STEP - 1) { next() }
        assertEquals(0, onAllNodesWithTextCount(Label.NEXT), "there is nowhere left to advance to")
        assertEquals(0, onAllNodesWithTextCount(Label.SKIP), "nothing left to skip on the last step")
        onNodeWithText(Label.DONE).assertIsDisplayed()
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
    fun `both picker steps can be scrolled, so no choice is unreachable`() {
        // The window is a fixed 700x620 and not resizable. Ten theme pills wrap to more rows than
        // that leaves room for, so without a scroll the lower ones cannot be reached — which is
        // exactly what happened before: the language step scrolled and the theme step did not.
        wizard { _ ->
            assertEquals(1, onAllNodesCount(hasScrollAction()), "the language step scrolls")
            next()
            assertEquals(1, onAllNodesCount(hasScrollAction()), "and so must the theme step")
        }
    }

    @Test
    fun `walking the whole wizard never reports a language or theme by itself`() = wizard { choices ->
        repeat(Label.LAST_STEP - 1) { next() }
        assertNull(choices.language, "merely passing the language step must not choose one")
        assertNull(choices.theme, "merely passing the theme step must not choose one")
        assertEquals(0, choices.dismissed, "walking to the end must not close the wizard on its own")
    }

    private fun ComposeUiTest.onAllNodesCount(matcher: SemanticsMatcher): Int =
        onAllNodes(matcher).fetchSemanticsNodes(atLeastOneRootRequired = false).size

    private fun ComposeUiTest.onAllNodesWithTextCount(text: String): Int =
        onAllNodes(androidx.compose.ui.test.hasText(text)).fetchSemanticsNodes(atLeastOneRootRequired = false).size
}
