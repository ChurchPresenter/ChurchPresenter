@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.canvas

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.onRoot
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Deleting the selected source with the keyboard.
 *
 * The canvas is the one tab where Delete removes something the operator cannot get back, so the two
 * guards around it are the point: nothing selected must do nothing, and a scene name being typed
 * must swallow the key rather than delete a source out from under the cursor. Renaming a scene to
 * "Welcome" and losing the video behind it on the backspace is the failure this prevents.
 */
class CanvasTabKeyboardTest {

    private fun sourceNamesOf(vm: SceneViewModel) = vm.sourceNames()

    @Test
    fun `Delete removes the selected source`() {
        canvasTab(seed = { addScene("Scene"); seedSources("Logo", "Lower Third") }) { vm, _, _ ->
            clickCanvasLabel("Logo")

            onRoot().performKeyInput { pressKey(Key.Delete) }
            waitForIdle()

            assertEquals(listOf("Lower Third"), sourceNamesOf(vm))
        }
    }

    @Test
    fun `Delete with nothing left to select removes nothing`() {
        // Pressing it again after the last source is gone must be a no-op rather than an error —
        // an operator clearing a scene holds the key down.
        canvasTab(seed = { addScene("Scene"); seedSources("Logo", "Lower Third") }) { vm, _, _ ->
            clickCanvasLabel("Logo")
            onRoot().performKeyInput { pressKey(Key.Delete) }
            waitForIdle()
            val left = sourceNamesOf(vm)

            onRoot().performKeyInput { pressKey(Key.Delete) }
            waitForIdle()

            assertEquals(
                left, sourceNamesOf(vm),
                "with the selection gone the key must do nothing rather than take the next source",
            )
        }
    }

    @Test
    fun `a key that is not the delete shortcut removes nothing`() {
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            clickCanvasLabel("Logo")

            onRoot().performKeyInput { pressKey(Key.A) }
            waitForIdle()

            assertEquals(listOf("Logo"), sourceNamesOf(vm))
        }
    }

    @Test
    fun `Delete does nothing while a scene name is being typed`() {
        // The rename field is the one place Backspace has to mean backspace.
        canvasTab(seed = { addScene("Scene"); seedSources("Logo") }) { vm, _, _ ->
            clickCanvasLabel("Logo")
            onNodeWithContentDescription("Rename").performClick()
            waitForIdle()

            onRoot().performKeyInput { pressKey(Key.Delete) }
            waitForIdle()

            assertEquals(
                listOf("Logo"), sourceNamesOf(vm),
                "the source must survive a Delete pressed while renaming",
            )
        }
    }
}
