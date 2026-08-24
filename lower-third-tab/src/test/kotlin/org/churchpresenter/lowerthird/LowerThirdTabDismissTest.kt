@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.lowerthird

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import org.churchpresenter.atem.AtemMediaSlot
import org.churchpresenter.atem.AtemState
import kotlin.test.Test
import kotlin.test.assertTrue
import org.churchpresenter.ui.renderedText
import org.churchpresenter.ui.showsContainingText

/**
 * Getting back out of things — the upload dialog, the slot menu — and the tooltips that explain the
 * icon-only buttons.
 *
 * Every action on this tab is an unlabelled icon, so the tooltip *is* the label; and the dialog is
 * modal over a live service, so being able to close it without uploading matters more here than the
 * uploading does.
 *
 * See `LowerThirdTabTestSupport.kt` for the harness.
 */
class LowerThirdTabDismissTest {

    private fun state() = AtemState(
        fps = 25.0,
        videoMode = "1080p25",
        stillSlots = listOf(
            AtemMediaSlot(index = 0, name = "Welcome", isUsed = true),
            AtemMediaSlot(index = 1, name = "Second", isUsed = false),
        ),
        clipSlots = listOf(AtemMediaSlot(index = 0, name = "Clip A", isUsed = true)),
        clipMaxFrames = listOf(600),
    )

    // Not tested: the dialog's own `onDismissRequest`. Compose Desktop's `AlertDialog` does not
    // route Escape to it under `runComposeUiTest`, and there is no click-outside to send either —
    // the dialog fills the test window. Cancel, which is what an operator actually presses, is
    // covered by `LowerThirdAtemDialogTest`.

    @Test
    fun `the slot menu closes again without choosing anything`() =
        lowerThirdTab(atemReachable = true, queryAtemState = { _, _ -> state() }) { _ ->
            openAtemDialog()
            onAllNodes(hasText("Slot 1", substring = true))[0].performClick()
            waitForIdle()
            assertTrue(showsContainingText("Slot 2 – Second"), renderedText().toString())

            escapeTopmost()

            // The dialog is still up — only the menu went — so the operator has not lost their place.
            assertTrue(showsContainingText("Upload mode"), renderedText().toString())
        }

    @Test
    fun `hovering an action shows the tooltip that names it`() = lowerThirdTab { _ ->
        selectPreset("Welcome")

        // Every action here is an icon with no caption, so the tooltip carries the only words that
        // say what the button does. It is drawn on hover and nowhere else.
        hover(LowerThirdLabel.GO_LIVE)

        waitUntil("the tooltip to appear", 5_000) {
            renderedText().count { it == LowerThirdLabel.GO_LIVE } > 0
        }
    }

    /**
     * Presses Escape on the topmost compose root.
     *
     * A dialog and a dropdown each open a root of their own, so `onRoot()` is ambiguous the moment
     * one is up — and the key has to reach the thing being dismissed, which is always the last one
     * opened.
     */
    private fun ComposeUiTest.escapeTopmost() {
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes(atLeastOneRootRequired = false)
        onAllNodes(isRoot())[roots.lastIndex].performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
    }

    /** Moves the pointer onto the button [label] names and leaves it there. */
    private fun ComposeUiTest.hover(label: String) {
        ltButton(label).performMouseInput { moveTo(center) }
        waitForIdle()
    }
}
