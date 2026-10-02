package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextBackdrop
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BackdropTextOptionsTest {

    private val bordered = TextBackdrop(border = true, borderWidth = 4, borderPadding = 10)

    @Test
    fun `plain backdrop text takes every styling argument`() = runComposeUiTest {
        var layouts = 0
        setContent {
            BackdropText(
                text = "Grace", backdrop = bordered, modifier = Modifier, style = TextStyle(fontSize = 18.sp),
                color = Color.White, textAlign = TextAlign.Center, lineHeight = 22.sp, maxLines = 2,
                overflow = TextOverflow.Ellipsis, softWrap = false, onTextLayout = { layouts++ },
            )
        }
        waitForIdle()
        onNodeWithText("Grace").assertExists()
        assertTrue(layouts > 0)
    }

    @Test
    fun `annotated backdrop text takes every styling argument`() = runComposeUiTest {
        var layouts = 0
        setContent {
            BackdropText(
                text = AnnotatedString("Peace"), backdrop = bordered, modifier = Modifier,
                style = TextStyle(fontSize = 18.sp), color = Color.White, textAlign = TextAlign.End,
                lineHeight = 22.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = true,
                onTextLayout = { layouts++ },
            )
        }
        waitForIdle()
        onNodeWithText("Peace").assertExists()
        assertTrue(layouts > 0)
    }

    @Test
    fun `text with no backdrop is given no extra room`() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("bare").backdropRoom(TextBackdrop())) { BackdropText("Hope", TextBackdrop()) }
        }
        val box = onNodeWithTag("bare").fetchSemanticsNode().size
        val text = onNodeWithText("Hope").fetchSemanticsNode().size
        assertEquals(text, box)
    }

    @Test
    fun `a bordered backdrop is given room on every side`() = runComposeUiTest {
        setContent {
            Box(Modifier.testTag("padded").backdropRoom(bordered, scale = 2f)) { BackdropText("Hope", bordered) }
        }
        val box = onNodeWithTag("padded").fetchSemanticsNode().size
        val text = onNodeWithText("Hope").fetchSemanticsNode().size
        assertTrue(box.width > text.width && box.height > text.height, "box $box should exceed text $text")
    }
}
