package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What goes onto the ticker's tape as the caption changes: the words a growing caption adds, a
 * rewritten caption whole, and nothing for a caption that did not change. The crawl itself runs on
 * infinite-animation frames, which a test clock does not drive, so the tape here holds still.
 */
@OptIn(ExperimentalTestApi::class)
class CaptionTickerTapeTest {

    private fun ComposeUiTest.pieces(text: String) = onAllNodesWithText(text).fetchSemanticsNodes().size

    @Test
    fun `each change adds only its new words, and an unchanged caption adds nothing`() = runComposeUiTest {
        var caption by mutableStateOf(AnnotatedString("Grace and peace"))
        setContent {
            Box(Modifier.size(800.dp, 120.dp)) {
                CaptionTicker(caption, TextStyle(fontSize = 32.sp), TextOutline(), speed = 120)
            }
        }
        waitForIdle()
        assertEquals(1, pieces("Grace and peace"))

        caption = AnnotatedString("Grace and peace to you")
        waitForIdle()
        assertEquals(1, pieces(" to you"), "only the words the caption grew by")

        caption = AnnotatedString("A new sentence")
        waitForIdle()
        assertEquals(1, pieces("A new sentence"), "a rewritten caption goes on whole")

        caption = AnnotatedString("A new sentence")
        waitForIdle()
        assertEquals(1, pieces("A new sentence"), "the same caption again adds nothing")
    }
}
