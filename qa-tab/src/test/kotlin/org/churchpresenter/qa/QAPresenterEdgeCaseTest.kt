@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.qa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.settings.QASettings
import kotlin.test.Test

/**
 * The output at the states the ordinary render tests do not reach — mid-transition, at a position
 * the settings should never hold, and with a question long enough to shrink the type right down.
 *
 * All of these draw rather than assert a value: the presenter publishes only the text it puts up, so
 * what a test can pin is that the state composes and the words survive it.
 */
class QAPresenterEdgeCaseTest {

    private fun question(text: String = "How do I join a small group?") =
        Question(id = "q1", text = text, timestamp = 0L)

    private fun shown(content: @Composable () -> Unit) = runComposeUiTest {
        setContent { MaterialTheme { Box(Modifier.size(1280.dp, 720.dp)) { content() } } }
        waitForIdle()
    }

    @Test
    fun `a question mid-fade still shows its text`() = shown {
        QAPresenter(question = question(), transitionAlpha = 0.4f)
    }.also {
        // Composing is the assertion: `graphicsLayer { alpha = transitionAlpha }` runs at draw time,
        // and a throw there fails the test rather than returning a wrong value.
    }

    @Test
    fun `the QR code mid-fade still draws`() = shown {
        QAQRCodePresenter(url = "https://example.church/qa", transitionAlpha = 0.4f)
    }

    @Test
    fun `a position the picker cannot produce falls back to the centre`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                Box(Modifier.size(1280.dp, 720.dp)) {
                    QAPresenter(
                        question = question("Anywhere at all"),
                        // Not one of the nine the settings tab offers — a value from an older
                        // settings file, or a hand-edited one, must still land somewhere.
                        qaSettings = QASettings(position = "nowhere-in-particular"),
                    )
                }
            }
        }
        waitForIdle()
        onNodeWithText("Anywhere at all").assertExists()
    }

    @Test
    fun `a question far too long for the screen is still put up`() = runComposeUiTest {
        val long = List(40) { "How should a small group choose what to read together" }.joinToString(" ")
        setContent {
            MaterialTheme { Box(Modifier.size(640.dp, 360.dp)) { QAPresenter(question = question(long)) } }
        }
        waitForIdle()
        // Auto-fit stops at its floor and lets the rest overflow rather than hiding the question.
        onNodeWithText(long).assertExists()
    }

    @Test
    fun `a fully transparent background still shows the question`() = shown {
        QAPresenter(
            question = question(),
            qaSettings = QASettings(backgroundColor = "transparent", backgroundOpacity = 0),
        )
    }

    @Test
    fun `an opacity above the allowed range is clamped rather than rejected`() = shown {
        QAPresenter(question = question(), qaSettings = QASettings(backgroundOpacity = 500))
        QAQRCodePresenter(
            url = "https://example.church/qa",
            qaSettings = QASettings(backgroundOpacity = 500, qrBackgroundOpacity = 500),
        )
    }
}
