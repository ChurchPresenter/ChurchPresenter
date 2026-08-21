@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.churchpresenter.app.churchpresenter.utils.Constants
import org.churchpresenter.app.churchpresenter.models.presentation.AnimationType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
            assertTrue(showsContainingText("AUTO-SCROLL INTERVAL:5 s"), "the hold time: ${renderedText()}")
            assertTrue(showsContainingText("TRANSITION DURATION:500 ms"), "the transition: ${renderedText()}")
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
    fun `an interval longer than the maximum is clamped rather than refused`() =
        picturesTab { vm, _ ->
            // Half a minute is the cap; a typo of 300 should not park the slideshow for five
            // minutes, and rejecting it outright would leave the operator with no feedback.
            openIntervalEditor()
            editorField().performTextReplacement("300")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(30f, vm.autoScrollInterval)
        }

    @Test
    fun `an interval below the minimum is clamped too`() = picturesTab { vm, _ ->
        openIntervalEditor()
        editorField().performTextReplacement("0")
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(1f, vm.autoScrollInterval, "one second is as fast as it goes")
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
}
