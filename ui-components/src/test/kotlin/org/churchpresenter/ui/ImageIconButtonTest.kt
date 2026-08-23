package org.churchpresenter.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The rippleless icon button used wherever the content already has its own custom background
 * shape (the default desktop ripple would render as a grey disk on top of it).
 */
@OptIn(ExperimentalTestApi::class)
class ImageIconButtonTest {

    @Test
    fun `the content slot is rendered inside the button`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ImageIconButton(onClick = { }) { Text("Icon") }
            }
        }
        onNodeWithText("Icon").assertExists("the content slot must be composed")
    }

    @Test
    fun `clicking the button invokes onClick`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ImageIconButton(onClick = { clicked = true }) { Text("Icon") }
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "clicking the button must invoke the caller's onClick")
    }

    @Test
    fun `an enabled button (the default) can be clicked`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ImageIconButton(onClick = { }) { Text("Icon") }
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled button reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                ImageIconButton(onClick = { clicked = true }, enabled = false) { Text("Icon") }
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked, "a disabled button must not invoke onClick")
    }

    @Test
    fun `size controls the rendered button's size`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ImageIconButton(onClick = { }, modifier = Modifier.testTag("btn"), size = 60.dp) { Text("Icon") }
            }
        }
        val nodeSize = onNodeWithTag("btn").fetchSemanticsNode().size
        assertEquals(60, nodeSize.width, "the button must be laid out at the requested size")
        assertEquals(60, nodeSize.height, "the button must be laid out at the requested size")
    }

    @Test
    fun `a size set on the caller's modifier does not override the size parameter`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                ImageIconButton(
                    onClick = { },
                    modifier = Modifier.testTag("btn").size(80.dp),
                    size = 40.dp,
                ) { Text("Icon") }
            }
        }
        val nodeSize = onNodeWithTag("btn").fetchSemanticsNode().size
        assertEquals(
            40, nodeSize.width,
            "the size parameter is applied before the caller's modifier, so it constrains the final size " +
                "regardless of a conflicting size() in that modifier",
        )
    }
}
