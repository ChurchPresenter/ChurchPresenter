@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The Presentation tab with no deck loaded — the empty state, and the playback settings that sit
 * beside it.
 *
 * This is what the tab looks like every time the app starts, and no test had ever rendered it. The
 * settings tiles are the substance here: auto-scroll interval, transition duration and animation type
 * each live in two places at once — the view model drives playback now, the host persists the value for
 * next Sunday — so each test checks both. A control that updates only one either fails to take effect
 * or silently forgets itself on restart.
 *
 * **What the empty state does not offer** is worth knowing too, and is asserted: the blank/clear/remote
 * controls appear only once a deck is loaded. That is why this suite does not test them — not an
 * oversight. Covering them needs a rasterized deck, as does the slide grid and `SlideThumbnail`. See
 * `PresentationTabTestSupport.kt`.
 */
class PresentationTabTest {

    // ── The empty state ─────────────────────────────────────────────────────────

    @Test
    fun `with no deck loaded the tab says so and offers to pick one`() = presentationTab { vm, _ ->
        assertTrue(vm.slideFiles.isEmpty(), "the fixture starts with no deck")
        onNodeWithText(PresentationLabel.NO_FILE).assertExists("the tab must say nothing is loaded")
        onNodeWithText(PresentationLabel.SELECT_FILE).assertExists("and offer a way to load one")
    }

    @Test
    fun `the empty state names the formats that can be opened`() = presentationTab { _, _ ->
        // An operator with a .odp or a Google Slides export needs to find out here, not after a
        // failed load mid-service.
        assertTrue(showsContainingText("PowerPoint"), renderedText().toString())
        assertTrue(showsContainingText("Keynote"), renderedText().toString())
        assertTrue(showsContainingText(".pdf"), renderedText().toString())
    }

    @Test
    fun `the keyboard hint is shown so the shortcuts are discoverable`() = presentationTab { _, _ ->
        // Nobody finds "B blanks the screen" without being told, and it is the one shortcut worth
        // knowing mid-service.
        assertTrue(
            showsContainingText("blank screen"),
            "the arrow-key hint must be on screen: ${renderedText()}",
        )
    }

    // ── The playback settings ───────────────────────────────────────────────────

    @Test
    fun `the shipped defaults are shown before anything is changed`() = presentationTab { _, _ ->
        // Caption and value are merged into one node, so these are substring checks.
        assertTrue(showsContainingText("AUTO-SCROLL INTERVAL:5 s"), renderedText().toString())
        assertTrue(showsContainingText("TRANSITION DURATION:500 ms"), renderedText().toString())
        assertTrue(showsContainingText("ANIMATION TYPE:Crossfade"), renderedText().toString())
    }

    @Test
    fun `setting the auto-scroll interval applies it now and asks for it to be remembered`() =
        presentationTab { vm, reports ->
            onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
            waitForIdle()
            onAllNodes(hasSetTextAction())[0].performTextReplacement("12")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(12f, vm.autoScrollInterval, "the running slideshow picks it up")
            assertEquals(
                12f,
                reports.settingsAfterChange?.presentationSettings?.autoScrollInterval,
                "and the host is asked to store it",
            )
        }

    @Test
    fun `setting the transition duration applies it now and asks for it to be remembered`() =
        presentationTab { vm, reports ->
            onNodeWithText("TRANSITION DURATION", substring = true).performClick()
            waitForIdle()
            onAllNodes(hasSetTextAction())[0].performTextReplacement("250")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(250f, vm.transitionDuration)
            assertEquals(250f, reports.settingsAfterChange?.presentationSettings?.transitionDuration)
        }

    @Test
    fun `changing the animation type applies it now and asks for it to be remembered`() =
        presentationTab { vm, reports ->
            onNodeWithText("ANIMATION TYPE", substring = true).performClick()
            waitForIdle()
            onNodeWithText("Slide Left").performClick()
            waitForIdle()

            // Asserted through the view model and the settings, never by reading the label back: a
            // DropdownMenu shows whatever was clicked whether or not anything was stored. Note the
            // two sides use different types — the view model holds the enum, settings the string it
            // is persisted as — so this also pins that they stay in step.
            assertEquals(AnimationType.SLIDE_LEFT, vm.animationType)
            assertEquals(
                Constants.ANIMATION_SLIDE_LEFT,
                reports.settingsAfterChange?.presentationSettings?.animationType,
            )
        }

    // ── The live-output controls, with nothing to act on ────────────────────────

    @Test
    fun `clear is offered but disabled while no deck is loaded`() = presentationTab { vm, _ ->
        assertTrue(vm.slideFiles.isEmpty())

        // Present rather than hidden, so the toolbar does not reshuffle the moment a deck loads —
        // but disabled, because clearing nothing would just be a click that appears to do nothing.
        assertTrue(hasPresentationButton(PresentationLabel.CLEAR), renderedText().toString())
        presentationButton(PresentationLabel.CLEAR).assertIsNotEnabled()
    }

    @Test
    fun `the blank control is offered only when there is an output to blank`() {
        // It lives behind `presenterManager != null`: with no presenter there is no output, and a
        // blank button that blanks nothing would be worse than no button.
        presentationTab(presenterManager = null) { _, _ ->
            assertTrue(!hasPresentationButton(PresentationLabel.BLANK_OUTPUT))
        }
        presentationTab(presenterManager = PresenterManager()) { _, _ ->
            assertTrue(hasPresentationButton(PresentationLabel.BLANK_OUTPUT))
        }
    }

    @Test
    fun `the blank control is disabled until a deck is loaded`() =
        presentationTab(presenterManager = PresenterManager()) { _, _ ->
            presentationButton(PresentationLabel.BLANK_OUTPUT).assertIsNotEnabled()
        }

    @Test
    fun `blanking reads as unblank once the output is already blanked`() =
        presentationTab(presenterManager = PresenterManager(), presentationFrozen = true) { _, _ ->
            // One button with two meanings — showing "Blank Output" while already blanked would have
            // the operator click it and see nothing change.
            assertTrue(hasPresentationButton(PresentationLabel.UNBLANK_OUTPUT))
            assertTrue(!hasPresentationButton(PresentationLabel.BLANK_OUTPUT))
        }
}
