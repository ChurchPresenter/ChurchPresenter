package org.churchpresenter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConditionalTooltipAreaVisibilityTest {

    private val window = 800f to 600f

    private fun visible(left: Float, top: Float, right: Float, bottom: Float) =
        isFullyVisibleInWindow(Rect(left, top, right, bottom), window.first, window.second)

    @Test
    fun `a row wholly inside the window shows its tooltip`() {
        assertTrue(visible(10f, 10f, 200f, 40f))
    }

    @Test
    fun `a row flush against every edge still counts as inside`() {
        assertTrue(visible(0f, 0f, 800f, 600f))
    }

    @Test
    fun `a row scrolled off the left is suppressed`() {
        assertFalse(visible(-1f, 10f, 200f, 40f))
    }

    @Test
    fun `a row scrolled off the top is suppressed`() {
        assertFalse(visible(10f, -1f, 200f, 40f))
    }

    @Test
    fun `a row running past the right edge is suppressed`() {
        assertFalse(visible(10f, 10f, 801f, 40f))
    }

    @Test
    fun `a row running past the bottom edge is suppressed`() {
        assertFalse(visible(10f, 10f, 200f, 601f))
    }

    @Test
    fun `a window with no size yet suppresses everything`() {
        assertFalse(isFullyVisibleInWindow(Rect(0f, 0f, 10f, 10f), 0f, 0f))
    }
}

@OptIn(ExperimentalTestApi::class, ExperimentalFoundationApi::class)
class ConditionalTooltipAreaTest {

    @Test
    fun `the wrapped content is drawn`() = runComposeUiTest {
        setContent {
            ConditionalTooltipArea(tooltip = { Text("the tooltip") }) {
                Box(modifier = Modifier.size(40.dp)) { Text("the row") }
            }
        }

        onNodeWithText("the row").assertIsDisplayed()
    }
}
