@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import kotlin.test.assertTrue
import org.churchpresenter.core.models.text.TextBackdrop
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import kotlin.test.Test

/**
 * [BottomAlignedText] is shared between `STTPresenter` and `SubtitleOverlay`, both of which always
 * pass a positive `maxLines`, so its `maxLines <= 0` early-return branch (a plain unclipped
 * [OutlinedText]) is otherwise never reached even though STT's own max-lines field accepts 0.
 * Covers both branches directly rather than through either caller.
 */
class BottomAlignedTextTest {

    @Test
    fun `maxLines 0 renders through the unclipped OutlinedText branch`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("root").size(200.dp, 100.dp)) {
                    BottomAlignedText(
                        text = AnnotatedString("Zero lines"),
                        style = TextStyle.Default,
                        maxLines = 0,
                    )
                }
            }
        }
        onNodeWithText("Zero lines").assertExists()
    }

    @Test
    fun `a positive maxLines renders through the measured Layout branch`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("root").size(200.dp, 100.dp)) {
                    BottomAlignedText(
                        text = AnnotatedString("Line one\nLine two\nLine three"),
                        style = TextStyle.Default,
                        maxLines = 2,
                    )
                }
            }
        }
        onNodeWithTag("root").assertExists()
    }

    /** A plate that reaches well outside the text, which is what needs room inside the clip. */
    private val plate = TextBackdrop(
        border = true,
        borderColor = "#FFD54F",
        borderWidth = 6,
        borderPadding = 18,
    )

    @Test
    fun `a full clip of lines keeps its first line when a backdrop is on`() = runComposeUiTest {
        // The clip is measured from an invisible reference of exactly `maxLines` lines. Give the
        // content room for the plate without giving the reference the same, and a full two lines
        // measure taller than the box they are clipped to — which this layout reads as overflow and
        // corrects by shifting the text up, taking the first line off the top. Two lines in, one
        // line visible, the moment a backdrop is switched on.
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("root").size(400.dp, 200.dp)) {
                    BottomAlignedText(
                        text = AnnotatedString("Line one\nLine two"),
                        style = TextStyle.Default,
                        maxLines = 2,
                        backdrop = plate,
                    )
                }
            }
        }
        // Asserted on **where it was placed**, not on whether it exists: text shifted off the top is
        // clipped by the parent and is still perfectly present in the semantics tree, so
        // `assertExists` passes against this bug. The shift is what has to be caught, and a shift is
        // a negative offset.
        // `positionInRoot`, not `getBoundsInRoot`: the latter clamps to the root, so the negative
        // offset this is looking for is rounded away to zero before the assertion ever sees it.
        val top = onNodeWithText("Line one", substring = true)
            .fetchSemanticsNode().positionInRoot.y
        assertTrue(top >= 0f, "the first line was shifted off the top of the clip: top=$top")
    }

    @Test
    fun `the unclipped branch takes a backdrop too`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("root").size(400.dp, 200.dp)) {
                    BottomAlignedText(
                        text = AnnotatedString("Zero lines"),
                        style = TextStyle.Default,
                        maxLines = 0,
                        backdrop = plate,
                    )
                }
            }
        }
        onNodeWithText("Zero lines").assertExists()
    }

    @Test
    fun `a scaled caption scales its plate with its type`() = runComposeUiTest {
        // `scaleFactor` reaches the painter as well as the outline: the caller has already scaled
        // the font size it passes, and a backdrop's measurements are in those same units, so a
        // plate drawn at 1x against half-size text comes out twice as heavy as it was set.
        setContent {
            MaterialTheme {
                Box(Modifier.testTag("root").size(400.dp, 200.dp)) {
                    BottomAlignedText(
                        text = AnnotatedString("Scaled"),
                        style = TextStyle.Default,
                        maxLines = 2,
                        backdrop = plate,
                        scaleFactor = 0.5f,
                    )
                }
            }
        }
        onNodeWithText("Scaled").assertExists()
    }
}
