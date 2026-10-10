@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.core.models.presentation.AnimationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.churchpresenter.sharedui.testing.renderedText
import org.churchpresenter.sharedui.testing.showsContainingText

/**
 * The slideshow settings on the Pictures tab: how long each image is held, how long the transition
 * takes, and which transition it is.
 *
 * Each of these lives in two places at once — the view model drives the slideshow now, and the
 * settings are persisted by the host for next time — so every test checks both, because a control
 * that updates only one of them either fails to take effect or silently forgets itself on restart.
 *
 * See `PicturesTabTestSupport.kt` for the harness.
 */
class PicturesTabSettingsTest {

    @Test
    fun `the current interval and transition are shown before anything is changed`() =
        picturesTab { _, _ ->
            // The shipped defaults: hold for 5s, transition over 500ms.
            // Caption and value are merged into one node, so these are substring checks.
            assertTrue(showsContainingText("AUTO-SCROLL INTERVAL5 s"), "the hold time: ${renderedText()}")
            assertTrue(showsContainingText("TRANSITION DURATION500 ms"), "the transition: ${renderedText()}")
            assertTrue(showsContainingText("ANIMATION TYPE:Crossfade"), "the transition style")
        }

    @Test
    fun `setting the interval applies it now and asks for it to be remembered`() =
        picturesTab { vm, reports ->
            openIntervalEditor()
            editorField().performTextReplacement("12")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(12f, vm.autoScrollInterval, "the running slideshow picks it up")
            assertEquals(
                12f,
                reports.settingsAfterChange?.pictureSettings?.autoScrollInterval,
                "and the host is asked to store it",
            )
        }

    @Test
    fun `cancelling the interval editor changes nothing`() = picturesTab { vm, reports ->
        openIntervalEditor()
        editorField().performTextReplacement("12")
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval, "the slideshow is untouched")
        assertEquals(0, reports.settingsChanges, "and nothing was persisted")
    }

    @Test
    fun `an interval longer than the maximum is refused with a message`() =
        picturesTab { vm, _ ->
            openIntervalEditor()
            editorField().performTextReplacement("300")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(5f, vm.autoScrollInterval, "a typo never parks the slideshow")
            assertTrue(showsContainingText("Enter a number from 1 to 30 s."), renderedText().toString())
        }

    @Test
    fun `an interval below the minimum is refused too`() = picturesTab { vm, _ ->
        openIntervalEditor()
        editorField().performTextReplacement("0")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval)
        assertTrue(showsContainingText("Enter a number from 1 to 30 s."))
    }

    @Test
    fun `a transition preset sets it in one click`() = picturesTab { vm, reports ->
        openTransitionEditor()
        onNodeWithText("750 ms").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(750f, vm.transitionDuration)
        assertEquals(750f, reports.settingsAfterChange?.pictureSettings?.transitionDuration)
    }

    @Test
    fun `minus steps the interval down by a second`() = picturesTab { vm, _ ->
        openIntervalEditor()
        onNodeWithContentDescription("Decrease").performClick()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(4f, vm.autoScrollInterval)
    }

    @Test
    fun `text that is not a number leaves the interval alone`() = picturesTab { vm, reports ->
        openIntervalEditor()
        editorField().performTextReplacement("soon")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(5f, vm.autoScrollInterval, "the slideshow keeps running as it was")
        assertNull(reports.settingsAfterChange, "and nothing is persisted")
    }

    @Test
    fun `setting the transition duration applies it and asks for it to be remembered`() =
        picturesTab { vm, reports ->
            openTransitionEditor()
            editorField().performTextReplacement("900")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(900f, vm.transitionDuration)
            assertEquals(
                900f,
                reports.settingsAfterChange?.pictureSettings?.transitionDuration,
            )
        }

    @Test
    fun `cancelling the transition editor changes nothing`() = picturesTab { vm, reports ->
        openTransitionEditor()
        editorField().performTextReplacement("900")
        onNodeWithText("Cancel").performClick()
        waitForIdle()

        assertEquals(500f, vm.transitionDuration, "the slideshow is untouched")
        assertEquals(0, reports.settingsChanges, "and nothing was persisted")
    }

    @Test
    fun `choosing an animation applies it and asks for it to be remembered`() =
        picturesTab { vm, reports ->
            openAnimationDropdown()
            onNodeWithText("Slide Left").performClick()
            waitForIdle()

            assertEquals(AnimationType.SLIDE_LEFT, vm.animationType)
            assertEquals(
                Constants.ANIMATION_SLIDE_LEFT,
                reports.settingsAfterChange?.pictureSettings?.animationType,
                "stored as the constant, not as the translated label",
            )
        }

    @Test
    fun `turning the animation off is a choice like any other`() = picturesTab { vm, reports ->
        // "None" has to round-trip as a real value rather than as an absent one, or the setting
        // reads as "never configured" and the default crossfade comes back.
        openAnimationDropdown()
        onNodeWithText("None").performClick()
        waitForIdle()

        assertEquals(AnimationType.NONE, vm.animationType)
        assertEquals(
            Constants.ANIMATION_NONE,
            reports.settingsAfterChange?.pictureSettings?.animationType,
        )
    }

    @Test
    fun `choosing Fade applies it and asks for it to be remembered`() = picturesTab { vm, reports ->
        openAnimationDropdown()
        onNodeWithText("Fade").performClick()
        waitForIdle()

        assertEquals(AnimationType.FADE, vm.animationType)
        assertEquals(Constants.ANIMATION_FADE, reports.settingsAfterChange?.pictureSettings?.animationType)
    }

    @Test
    fun `choosing Slide Right applies it and asks for it to be remembered`() = picturesTab { vm, reports ->
        openAnimationDropdown()
        onNodeWithText("Slide Right").performClick()
        waitForIdle()

        assertEquals(AnimationType.SLIDE_RIGHT, vm.animationType)
        assertEquals(Constants.ANIMATION_SLIDE_RIGHT, reports.settingsAfterChange?.pictureSettings?.animationType)
    }
}
