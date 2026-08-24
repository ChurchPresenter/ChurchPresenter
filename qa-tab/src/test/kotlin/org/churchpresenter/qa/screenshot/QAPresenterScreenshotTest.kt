@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.github.takahirom.roborazzi.captureRoboImage
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.core.models.qa.QuestionStatus
import org.churchpresenter.qa.QAPresenter
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.ui.screenshot.SCREENSHOT_ROOT
import kotlin.test.Test

/**
 * The audience question as the congregation sees it, full screen.
 *
 * **One image per state, not two.** The rest of the screenshot suite stacks a light and a dark
 * render of each state, because those surfaces follow the operator's theme. This one does not: the
 * audience screen is drawn from [QASettings] and looks the same whichever theme the operator has
 * chosen. Stacking would write the same picture twice.
 *
 * Rendered at 1920x1080, which is what this surface is drawn onto in practice — and it matters here,
 * because auto-fit sizes the text against the space it is given.
 */
class QAPresenterScreenshotTest {

    /** A 1080p output. */
    private val screen = Modifier.size(1920.dp, 1080.dp)

    private fun shoot(name: String, content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(screen) { content() } } }
        waitForIdle()
        capture(name)
    }

    private fun ComposeUiTest.capture(name: String) {
        onRoot().captureRoboImage("$SCREENSHOT_ROOT/$SECTION/$name.png")
    }

    @Test
    fun `a question`() = shoot("qa_question") { QAPresenter(question = question()) }

    @Test
    fun `a styled question`() = shoot("qa_question_styled") {
        QAPresenter(
            question = question(),
            qaSettings = QASettings(
                textColor = "#FFD54F",
                backgroundColor = "#1B2A5B",
                fontSize = 72,
                bold = true,
                position = Constants.CENTER,
            ),
        )
    }

    @Test
    fun `a long question`() = shoot("qa_question_long") {
        QAPresenter(question = question(LONG_QUESTION))
    }

    private fun question(text: String = "How do I join a small group?") =
        Question(id = "q1", text = text, timestamp = 0L, status = QuestionStatus.APPROVED)

    private companion object {
        const val SECTION = "qaPresenter"

        const val LONG_QUESTION =
            "How should a small group decide what to study together, and how often should the " +
                "group change what it is reading?"
    }
}
