package org.churchpresenter.qa.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runDesktopComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.settings.QASettings
import org.churchpresenter.settings.QA_QR_CODE_BOX
import org.churchpresenter.settings.QA_QR_MESSAGE_BOX
import org.churchpresenter.settings.QA_QUESTION_BOX
import org.churchpresenter.settings.TextBox
import org.churchpresenter.settings.textBoxKey
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The Q&A output's text boxes: a boxed question, QR code or QR message is drawn inside its box and
 * leaves the card it used to share; whatever is left unboxed keeps the card.
 *
 * Asserted as containment between drawn bounds rather than pixel values, which differ across the
 * platforms' font metrics.
 */
@OptIn(ExperimentalTestApi::class)
class QAPresenterBoxTest {

    /** The bottom-right quarter of the output, in percent. */
    private val corner =
        TextBox(enabled = true, xPercent = 50f, yPercent = 75f, widthPercent = 50f, heightPercent = 25f)
    private val cornerRect = Rect(WIDTH * 0.5f, HEIGHT * 0.75f, WIDTH.toFloat(), HEIGHT.toFloat())

    /** The top-left quarter of the output, in percent. */
    private val topLeft =
        TextBox(enabled = true, xPercent = 0f, yPercent = 0f, widthPercent = 25f, heightPercent = 25f)
    private val topLeftRect = Rect(0f, 0f, WIDTH * 0.25f, HEIGHT * 0.25f)

    private fun question(text: String) = Question(id = "q", text = text, timestamp = 0L)

    private fun render(content: @Composable () -> Unit, check: ComposeUiTest.() -> Unit) =
        runDesktopComposeUiTest(width = WIDTH, height = HEIGHT) {
            setContent { MaterialTheme { Box(Modifier.size(WIDTH.dp, HEIGHT.dp)) { content() } } }
            check()
        }

    private fun ComposeUiTest.textBounds(text: String): Rect =
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().first().boundsInRoot

    private fun ComposeUiTest.qrBounds(): Rect =
        onAllNodesWithContentDescription("QR Code").fetchSemanticsNodes().first().boundsInRoot

    private fun Rect.inside(outer: Rect) =
        left >= outer.left - 1 && top >= outer.top - 1 && right <= outer.right + 1 && bottom <= outer.bottom + 1

    @Test
    fun `a boxed question is drawn in its box`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QUESTION_BOX, lowerThird = false) to corner))
        render({ QAPresenter(question = question("Where do I sign up?"), qaSettings = qa) }) {
            val drawn = textBounds("Where do I sign up")
            assertTrue(drawn.inside(cornerRect), "question $drawn in $cornerRect")
        }
    }

    @Test
    fun `a boxed question on the key output is still drawn in its box`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QUESTION_BOX, lowerThird = false) to corner))
        render({
            QAPresenter(
                question = question("Where do I sign up?"),
                qaSettings = qa,
                outputRole = Constants.OUTPUT_ROLE_KEY,
            )
        }) {
            assertTrue(textBounds("Where do I sign up").inside(cornerRect))
        }
    }

    @Test
    fun `a box with no question draws nothing`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QUESTION_BOX, lowerThird = false) to corner))
        render({ QAPresenter(question = null, qaSettings = qa) }) {
            onNodeWithText("Where do I sign up", substring = true).assertDoesNotExist()
        }
    }

    @Test
    fun `a boxed QR message is drawn in its box, not under the centred code`() {
        val qa = QASettings(
            qrCodeMessage = "Scan to ask a question",
            textBoxes = mapOf(textBoxKey(QA_QR_MESSAGE_BOX, lowerThird = false) to corner),
        )
        render({ QAQRCodePresenter(url = "https://example.org/qa", qaSettings = qa) }) {
            val drawn = textBounds("Scan to ask")
            assertTrue(drawn.inside(cornerRect), "message $drawn in $cornerRect")
            onNodeWithContentDescription("QR Code").assertExists()
        }
    }

    @Test
    fun `a boxed QR code is drawn in its box while the message keeps the card`() {
        val qa = QASettings(
            qrCodeMessage = "Scan to ask a question",
            textBoxes = mapOf(textBoxKey(QA_QR_CODE_BOX, lowerThird = false) to topLeft),
        )
        render({ QAQRCodePresenter(url = "https://example.org/qa", qaSettings = qa) }) {
            val code = qrBounds()
            assertTrue(code.inside(topLeftRect), "code $code in $topLeftRect")
            val message = textBounds("Scan to ask")
            assertTrue(!message.inside(topLeftRect), "the unboxed message stays in the centred card")
        }
    }

    @Test
    fun `boxing both the code and the message leaves no card behind`() {
        val qa = QASettings(
            qrCodeMessage = "Scan to ask a question",
            textBoxes = mapOf(
                textBoxKey(QA_QR_CODE_BOX, lowerThird = false) to topLeft,
                textBoxKey(QA_QR_MESSAGE_BOX, lowerThird = false) to corner,
            ),
        )
        render({ QAQRCodePresenter(url = "https://example.org/qa", qaSettings = qa) }) {
            assertTrue(qrBounds().inside(topLeftRect))
            assertTrue(textBounds("Scan to ask").inside(cornerRect))
            onAllNodesWithText("Scan to ask", substring = true).fetchSemanticsNodes().let {
                assertTrue(it.size == 1, "the message is drawn once, in its box")
            }
        }
    }

    @Test
    fun `a boxed QR code on the key output is a white square, not the code`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QR_CODE_BOX, lowerThird = false) to topLeft))
        render({
            QAQRCodePresenter(url = "https://example.org/qa", qaSettings = qa, outputRole = Constants.OUTPUT_ROLE_KEY)
        }) {
            onNodeWithContentDescription("QR Code").assertDoesNotExist()
        }
    }

    @Test
    fun `a boxed QR code with an unencodable url falls back to the card's message`() {
        val qa = QASettings(textBoxes = mapOf(textBoxKey(QA_QR_CODE_BOX, lowerThird = false) to topLeft))
        render({ QAQRCodePresenter(url = "", qaSettings = qa) }) {
            onNodeWithContentDescription("QR Code").assertDoesNotExist()
            onNodeWithText("Scan to ask a question", substring = true).assertExists()
        }
    }

    private companion object {
        const val WIDTH = 1920
        const val HEIGHT = 1080
    }
}
