@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
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
}
