package org.churchpresenter.qa.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.qa.Question
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The question on an output far smaller than full HD -- a dev window, a preview tile: drawn as the
 * full-HD picture scaled down, so its card's fixed padding cannot squeeze the text out of it.
 */
@OptIn(ExperimentalTestApi::class)
class QAPresenterSmallOutputTest {

    private val text = "How can I join a small group during the week?"

    @Test
    fun `on a small window the question is still drawn, inside the window`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(320.dp, 180.dp)) {
                QAPresenter(question = Question(id = "q", text = text, timestamp = 0L))
            }
        }
        val bounds = onNodeWithText(text).getBoundsInRoot()
        assertTrue(bounds.right > bounds.left && bounds.bottom > bounds.top, "the question was squeezed out: $bounds")
        assertTrue(bounds.left >= 0.dp && bounds.right <= 320.dp && bounds.bottom <= 180.dp, "inside: $bounds")
    }
}
