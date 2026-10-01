package org.churchpresenter.sharedui.composables

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A segment that rises under the pointer rises where it is drawn, never where it is hit.
 *
 * Done as a `graphicsLayer` translation, the rise moved the hit area with the picture, so a pointer
 * resting within the step of the bottom edge left the segment as it rose and was under it again as
 * it fell — every frame, for ever. A Compose test waiting for idle then never returned: #699's
 * Profiles hang, where the pane scrolled a segmented control under a pointer parked by a click.
 * Asserted here on the cause, so a regression fails on its first frame rather than hanging the fork.
 * The theme's own controls carry the same test in `:theme`.
 */
@OptIn(ExperimentalTestApi::class)
class HoverLiftSettlesTest {

    /** Parks the pointer half a pixel above [node]'s bottom edge and checks it stays where it is hit. */
    private fun ComposeUiTest.assertHitAreaStaysPut(node: () -> SemanticsNodeInteraction) {
        val rest = node().fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        try {
            node().performMouseInput { moveTo(Offset(width / 2f, height - HALF_PIXEL)) }
            repeat(FRAMES) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(
                    rest, node().fetchSemanticsNode().boundsInRoot,
                    "hovering moved the hit area on frame $frame",
                )
            }
        } finally {
            mainClock.autoAdvance = true
        }
    }

    private fun ComposeUiTest.segments() = setContent {
        MaterialTheme {
            SegmentedButton(
                items = listOf(
                    SegmentedButtonItem("a", "A", testTag = CHOSEN),
                    SegmentedButtonItem("b", "B", testTag = UNCHOSEN),
                ),
                selectedValue = "a",
                onValueChange = {},
            )
        }
    }

    private fun ComposeUiTest.alignment() = setContent {
        MaterialTheme {
            HorizontalAlignmentButtons(
                selectedAlignment = LEFT,
                onAlignmentChange = {},
                leftValue = LEFT,
                centerValue = "center",
                rightValue = "right",
            )
        }
    }

    @Test
    fun `an unchosen segment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        segments()
        assertHitAreaStaysPut { onNodeWithTag(UNCHOSEN) }
    }

    @Test
    fun `the chosen segment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        segments()
        assertHitAreaStaysPut { onNodeWithTag(CHOSEN) }
    }

    @Test
    fun `an unchosen alignment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        alignment()
        // Declared right, centre, left, and left is the chosen one.
        assertHitAreaStaysPut { onAllNodes(isSelectable())[0] }
    }

    @Test
    fun `the chosen alignment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        alignment()
        assertHitAreaStaysPut { onAllNodes(isSelectable())[2] }
    }

    private companion object {
        const val CHOSEN = "chosen"
        const val UNCHOSEN = "unchosen"
        const val LEFT = "left"
        const val HALF_PIXEL = 0.5f
        const val FRAMES = 6
    }
}
