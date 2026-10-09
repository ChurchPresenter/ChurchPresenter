@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.core.models.presentation.AnimationType
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.showsContainingText

class PresentationTabPlaybackSettingsTest {

    // ── Loop button ───────────────────────────────────────────────────────────────

    @Test
    fun `the loop button starts on and toggles off`() = presentationTab { vm, _ ->
        assertEquals(true, vm.isLooping, "looping is on by default")

        onNodeWithContentDescription("Loop On").performClick()
        waitForIdle()

        assertEquals(false, vm.isLooping)
    }

    @Test
    fun `toggling loop persists the choice`() = presentationTab { vm, reports ->
        onNodeWithContentDescription("Loop On").performClick()
        waitForIdle()

        assertEquals(false, reports.settingsAfterChange?.presentationSettings?.isLooping)

        onNodeWithContentDescription("Loop Off").performClick()
        waitForIdle()

        assertEquals(true, vm.isLooping, "clicking again turns it back on")
        assertEquals(true, reports.settingsAfterChange?.presentationSettings?.isLooping)
    }

    // ── Animation type: every option besides Slide Left ─────────────────────────

    @Test
    fun `selecting Fade applies it now and asks for it to be remembered`() = presentationTab { vm, reports ->
        onNodeWithText("ANIMATION TYPE", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Fade").performClick()
        waitForIdle()

        assertEquals(AnimationType.FADE, vm.animationType)
        assertEquals(Constants.ANIMATION_FADE, reports.settingsAfterChange?.presentationSettings?.animationType)
    }

    @Test
    fun `selecting Slide Right applies it now and asks for it to be remembered`() = presentationTab { vm, reports ->
        onNodeWithText("ANIMATION TYPE", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Slide Right").performClick()
        waitForIdle()

        assertEquals(AnimationType.SLIDE_RIGHT, vm.animationType)
        assertEquals(Constants.ANIMATION_SLIDE_RIGHT, reports.settingsAfterChange?.presentationSettings?.animationType)
    }

    @Test
    fun `selecting None applies it now and asks for it to be remembered`() = presentationTab { vm, reports ->
        onNodeWithText("ANIMATION TYPE", substring = true).performClick()
        waitForIdle()
        onNodeWithText("None").performClick()
        waitForIdle()

        assertEquals(AnimationType.NONE, vm.animationType)
        assertEquals(Constants.ANIMATION_NONE, reports.settingsAfterChange?.presentationSettings?.animationType)
    }

    // ── Animation type: the label reflects the settings value on render ─────────

    @Test
    fun `Fade renders as the current animation label`() = presentationTab(
        settings =
            { it.copy(presentationSettings = it.presentationSettings.copy(animationType = Constants.ANIMATION_FADE)) },
    ) { _, _ ->
        onNodeWithText("Fade").assertExists()
    }

    @Test
    fun `Slide Left renders as the current animation label`() = presentationTab(
        settings =
            { it.copy(
                presentationSettings = it.presentationSettings.copy(animationType = Constants.ANIMATION_SLIDE_LEFT),
            ) },
    ) { _, _ ->
        onNodeWithText("Slide Left").assertExists()
    }

    @Test
    fun `Slide Right renders as the current animation label`() = presentationTab(
        settings =
            { it.copy(
                presentationSettings = it.presentationSettings.copy(animationType = Constants.ANIMATION_SLIDE_RIGHT),
            ) },
    ) { _, _ ->
        onNodeWithText("Slide Right").assertExists()
    }

    @Test
    fun `None renders as the current animation label`() = presentationTab(
        settings =
            { it.copy(presentationSettings = it.presentationSettings.copy(animationType = Constants.ANIMATION_NONE)) },
    ) { _, _ ->
        onNodeWithText("None").assertExists()
    }

    @Test
    fun `selecting Crossfade after another type applies it now and asks for it to be remembered`() = presentationTab(
        settings =
            { it.copy(presentationSettings = it.presentationSettings.copy(animationType = Constants.ANIMATION_FADE)) },
    ) { vm, reports ->
        onNodeWithText("ANIMATION TYPE", substring = true).performClick()
        waitForIdle()
        onNodeWithText("Crossfade").performClick()
        waitForIdle()

        assertEquals(AnimationType.CROSSFADE, vm.animationType)
        assertEquals(Constants.ANIMATION_CROSSFADE, reports.settingsAfterChange?.presentationSettings?.animationType)
    }

    // ── Auto-scroll interval dialog ───────────────────────────────────────────────

    @Test
    fun `canceling the interval dialog leaves the interval unchanged`() = presentationTab { vm, reports ->
        onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("12")
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval, "the shipped default must survive a cancel")
        assertNull(reports.settingsAfterChange)
    }

    @Test
    fun `a non-numeric interval cannot be confirmed`() = presentationTab { vm, _ ->
        onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("abc")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval, "a value that doesn't parse must not overwrite the interval")
        assertTrue(showsContainingText("Enter a number from 1 to 30 s."), "the dialog says why")

        onNodeWithText("Cancel").performClick()
        waitForIdle()
        assertFalse(showsContainingText("Enter a number from"), "Cancel still closes it")
    }

    @Test
    fun `an interval above the maximum is refused with a message`() = presentationTab { vm, _ ->
        onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("999")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval, "a mistyped value never reaches the slideshow")
        assertTrue(showsContainingText("Enter a number from 1 to 30 s."))

        onNodeWithContentDescription("Decrease").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()
        assertEquals(30f, vm.autoScrollInterval, "one step brings it back to the maximum")
    }

    @Test
    fun `an interval below the minimum is refused with a message`() = presentationTab { vm, _ ->
        onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("0")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval)
        assertTrue(showsContainingText("Enter a number from 1 to 30 s."))
    }

    @Test
    fun `an interval preset sets it in one click`() = presentationTab { vm, reports ->
        onNodeWithText("AUTO-SCROLL INTERVAL", substring = true).performClick()
        waitForIdle()
        onNodeWithText("10 s").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(10f, vm.autoScrollInterval)
        assertEquals(10f, reports.settingsAfterChange?.presentationSettings?.autoScrollInterval)
    }

    // ── Transition duration dialog ────────────────────────────────────────────────

    @Test
    fun `canceling the transition dialog leaves the duration unchanged`() = presentationTab { vm, reports ->
        onNodeWithText("TRANSITION DURATION", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("250")
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(500f, vm.transitionDuration)
        assertNull(reports.settingsAfterChange)
    }

    @Test
    fun `a transition duration above the maximum is refused with a message`() = presentationTab { vm, _ ->
        onNodeWithText("TRANSITION DURATION", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("9999")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(500f, vm.transitionDuration)
        assertTrue(showsContainingText("Enter a number from 100 to 2000 ms."))
    }

    @Test
    fun `a transition duration below the minimum is refused with a message`() = presentationTab { vm, _ ->
        onNodeWithText("TRANSITION DURATION", substring = true).performClick()
        waitForIdle()
        onAllNodes(hasSetTextAction())[0].performTextReplacement("1")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(500f, vm.transitionDuration)
        assertTrue(showsContainingText("Enter a number from 100 to 2000 ms."))
    }

    @Test
    fun `plus steps the transition by fifty milliseconds`() = presentationTab { vm, reports ->
        onNodeWithText("TRANSITION DURATION", substring = true).performClick()
        waitForIdle()
        onNodeWithContentDescription("Increase").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(550f, vm.transitionDuration)
        assertEquals(550f, reports.settingsAfterChange?.presentationSettings?.transitionDuration)
    }
}
