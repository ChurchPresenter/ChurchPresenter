@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.slides.tabs

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The Presentation tab's two timing tiles: how long an auto-scrolling slide holds, and how long the
 * transition between slides takes. Each applies to the running deck at once and asks the host to
 * remember it; a dismissed editor changes neither.
 */
class PresentationTimingEditorsTest {

    @Test
    fun `setting the interval applies it now and asks for it to be remembered`() =
        presentationTab { vm, reports ->
            openIntervalEditor()
            editorField().performTextReplacement("12")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(12f, vm.autoScrollInterval, "the running deck picks it up")
            assertEquals(12f, reports.settingsAfterChange?.presentationSettings?.autoScrollInterval)
        }

    @Test
    fun `setting the transition applies it now and asks for it to be remembered`() =
        presentationTab { vm, reports ->
            openTransitionEditor()
            editorField().performTextReplacement("800")
            onNodeWithText("OK").performClick()
            waitForIdle()

            assertEquals(800f, vm.transitionDuration, "the running deck picks it up")
            assertEquals(800f, reports.settingsAfterChange?.presentationSettings?.transitionDuration)
        }

    @Test
    fun `a dismissed editor changes nothing`() =
        presentationTab { _, reports ->
            openIntervalEditor()
            onNodeWithText("Cancel").performClick()
            waitForIdle()
            openTransitionEditor()
            onNodeWithText("Cancel").performClick()
            waitForIdle()

            assertEquals(0, reports.settingsChanges)
            assertNull(reports.settingsAfterChange)
        }
}
