package org.churchpresenter.theme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * A control that rises under the pointer rises where it is drawn, never where it is hit.
 *
 * Done as a `graphicsLayer` translation, the rise moved the hit area with the picture, so a pointer
 * resting within the step of the bottom edge left the control as it rose and was under it again as
 * it fell: hover on, hover off, every frame, for ever — and a Compose test waiting for idle waited
 * for ever with it. Asserted here on the cause rather than the hang, so a regression fails on its
 * first frame instead of stalling the suite.
 */
@OptIn(ExperimentalTestApi::class)
class HoverLiftSettlesTest {

    /** Parks the pointer half a pixel above the bottom edge of [tag] and checks it stays where it is hit. */
    private fun ComposeUiTest.assertHitAreaStaysPut(tag: String) {
        val rest = onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        mainClock.autoAdvance = false
        try {
            onNodeWithTag(tag).performMouseInput { moveTo(Offset(width / 2f, height - HALF_PIXEL)) }
            repeat(FRAMES) { frame ->
                mainClock.advanceTimeByFrame()
                assertEquals(
                    rest, onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot,
                    "hovering moved where $tag is hit, on frame $frame",
                )
            }
        } finally {
            mainClock.autoAdvance = true
        }
    }

    @Test
    fun `a raised button rises where it is drawn, not where it is hit`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                RaisedButton(onClick = {}, modifier = Modifier.testTag(BUTTON)) { Text("Go") }
            }
        }
        assertHitAreaStaysPut(BUTTON)
    }

    @Test
    fun `an unchosen segment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        segments()
        assertHitAreaStaysPut(UNCHOSEN)
    }

    @Test
    fun `the chosen segment rises where it is drawn, not where it is hit`() = runComposeUiTest {
        segments()
        assertHitAreaStaysPut(CHOSEN)
    }

    private fun ComposeUiTest.segments() = setContent {
        MaterialTheme {
            Row {
                SegmentTrack {
                    SegmentTrackItem(selected = true, onClick = {}, modifier = Modifier.testTag(CHOSEN)) {
                        Text("One")
                    }
                    SegmentTrackItem(selected = false, onClick = {}, modifier = Modifier.testTag(UNCHOSEN)) {
                        Text("Two")
                    }
                }
            }
        }
    }

    private companion object {
        const val BUTTON = "button"
        const val CHOSEN = "chosen"
        const val UNCHOSEN = "unchosen"
        const val HALF_PIXEL = 0.5f
        const val FRAMES = 6
    }
}
