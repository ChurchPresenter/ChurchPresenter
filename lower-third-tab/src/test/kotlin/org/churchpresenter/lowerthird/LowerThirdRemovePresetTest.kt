@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.performClick
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * Removing a preset — which deletes the file off disk, so the confirm in front of it is the only
 * thing between a mis-click and a lost design.
 *
 * The confirm itself is a modal Swing dialog and stays uncovered; `confirmRemoval` is a parameter so
 * that everything *behind* it — the delete, dropping the selection when it was the one showing, and
 * refreshing the list — is under test. See `LowerThirdTabTestSupport.kt`.
 */
class LowerThirdRemovePresetTest {

    @Test
    fun `confirming the removal deletes the file and drops it from the list`() {
        val folder = lottieFolder("Welcome", "Second")
        try {
            lowerThirdTab(folder = folder, confirmRemoval = true) { reports ->
                removeButtonFor("Second").performClick()
                waitForIdle()

                assertEquals(1, reports.removalsAsked, "removing always asks first")
                waitUntil("the preset to leave the list", 5_000) { !showsContainingText("Second") }
                assertTrue(!File(folder, "Second.json").exists(), "and the file is gone from disk")
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `declining the removal leaves the preset alone`() {
        val folder = lottieFolder("Welcome", "Second")
        try {
            lowerThirdTab(folder = folder, confirmRemoval = false) { reports ->
                removeButtonFor("Second").performClick()
                waitForIdle()

                assertEquals(1, reports.removalsAsked)
                assertTrue(File(folder, "Second.json").exists(), "saying no must not delete anything")
                assertTrue(showsContainingText("Second"), renderedText().toString())
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test
    fun `removing the preset that is showing clears the preview with it`() {
        val folder = lottieFolder("Welcome", "Second")
        try {
            lowerThirdTab(folder = folder, confirmRemoval = true) { _ ->
                selectPreset("Second")
                assertTrue(!showsContainingText(LowerThirdLabel.SELECT_PRESET), "a preset is up")

                removeButtonFor("Second").performClick()
                waitForIdle()

                // Leaving the preview pointed at a file that no longer exists is how the tab used to
                // end up drawing a warning triangle nobody could clear.
                waitUntil("the preview to fall back to its prompt", 5_000) {
                    showsContainingText(LowerThirdLabel.SELECT_PRESET)
                }
            }
        } finally {
            folder.deleteRecursively()
        }
    }

    /**
     * The remove button on the row for [name].
     *
     * Every row carries one and they all share a name, so the row is found first — by its own text —
     * and its button matched by position within the list.
     */
    private fun androidx.compose.ui.test.ComposeUiTest.removeButtonFor(name: String) =
        onAllNodesWithContentDescription(LowerThirdLabel.REMOVE)[
            renderedText().filter { it == "Welcome" || it == "Second" }.indexOf(name).coerceAtLeast(0)
        ]
}
