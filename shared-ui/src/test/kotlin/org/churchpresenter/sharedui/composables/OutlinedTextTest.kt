package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import org.churchpresenter.core.models.text.TextOutline
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class OutlinedTextTest {

    private val on = TextOutline(enabled = true, color = "#FF0000", width = 4)

    @Test
    fun `with no outline the text is drawn once`() = runComposeUiTest {
        setContent {
            OutlinedText("In the beginning", TextOutline(), 1f, Color.White, 24.sp, TextStyle.Default)
        }
        assertEquals(1, onAllNodesWithText("In the beginning").fetchSemanticsNodes().size)
    }

    @Test
    fun `an outline draws the text a second time underneath`() = runComposeUiTest {
        setContent { OutlinedText("In the beginning", on, 1f, Color.White, 24.sp, TextStyle.Default) }
        assertEquals(2, onAllNodesWithText("In the beginning").fetchSemanticsNodes().size)
    }

    @Test
    fun `an outline switched on at zero width is no outline`() = runComposeUiTest {
        setContent {
            OutlinedText("Grace", on.copy(width = 0), 1f, Color.White, 24.sp, TextStyle.Default)
        }
        assertEquals(1, onAllNodesWithText("Grace").fetchSemanticsNodes().size)
    }

    @Test
    fun `the layout callback fires for the fill alone`() = runComposeUiTest {
        var layouts = 0
        setContent {
            OutlinedText(
                AnnotatedString("Peace"), on, 2f, Color.White, 24.sp, TextStyle.Default,
                onTextLayout = { layouts++ },
            )
        }
        waitForIdle()
        assertEquals(1, layouts)
    }

    @Test
    fun `text kept to its own width is narrower than text filling the row`() = runComposeUiTest {
        setContent {
            Column(Modifier.testTag("col")) {
                OutlinedText(
                    "Hymn 12", on, 1f, Color.White, 18.sp, TextStyle.Default,
                    modifier = Modifier.testTag("pinned"), fillWidth = false,
                )
                OutlinedText(
                    "Hymn 12", TextOutline(), 1f, Color.White, 18.sp, TextStyle.Default,
                    modifier = Modifier.testTag("filled"),
                )
            }
        }
        val pinned = onNodeWithTag("pinned").fetchSemanticsNode().size.width
        val filled = onNodeWithTag("filled").fetchSemanticsNode().size.width
        assertTrue(pinned < filled, "pinned $pinned should be narrower than filled $filled")
    }

    @Test
    fun `every styling argument is accepted with and without an outline`() = runComposeUiTest {
        setContent {
            Column {
                listOf(TextOutline(), on).forEach { outline ->
                    OutlinedText(
                        text = "Amen",
                        outline = outline,
                        scaleFactor = 1.5f,
                        color = Color.Yellow,
                        fontSize = 20.sp,
                        style = TextStyle(fontSize = 20.sp),
                        modifier = Modifier,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        softWrap = false,
                        fillWidth = false,
                        lineHeight = 24.sp,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        onTextLayout = {},
                    )
                }
            }
        }
        assertEquals(3, onAllNodesWithText("Amen").fetchSemanticsNodes().size)
    }
}
