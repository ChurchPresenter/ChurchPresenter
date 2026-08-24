package org.churchpresenter.qa

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.settings.QASettings
import org.churchpresenter.core.models.qa.Question
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test

/**
 * The audience question shown on screen, and the QR code people scan to submit one.
 *
 * [QAPresenter] puts the current question's text on the screen; a null question must leave it blank
 * rather than stranding the previously-shown question during a transition to nothing. The QR encoder itself
 * moved to `:ui-components` and is tested there, by `QrCodeTest`.
 */
@OptIn(ExperimentalTestApi::class)
class QAPresenterRenderTest {

    private fun question(text: String) =
        Question(id = "q1", text = text, timestamp = 0L)

    @Test
    fun `the current question is put on screen`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAPresenter(question = question("How do I start reading the Bible?"))
            }
        }
        onNodeWithText("How do I start reading the Bible?", substring = true)
            .assertExists("the congregation must see the question being answered")
    }

    @Test
    fun `no question leaves the screen blank`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) { QAPresenter(question = null) }
        }
        onNodeWithText("How do I start reading the Bible?", substring = true)
            .assertDoesNotExist()
    }

    private fun renderQuestion(settings: QASettings, outputRole: String = Constants.OUTPUT_ROLE_NORMAL) =
        runComposeUiTest {
            setContent {
                Box(Modifier.size(1920.dp, 1080.dp)) {
                    QAPresenter(
                        question = question("How do I start reading the Bible?"),
                        qaSettings = settings,
                        outputRole = outputRole,
                    )
                }
            }
            onNodeWithText("How do I start reading the Bible?", substring = true).assertExists()
        }

    @Test
    fun `key output role renders the question`() {
        renderQuestion(QASettings(), outputRole = Constants.OUTPUT_ROLE_KEY)
    }

    @Test
    fun `every parameter can be overridden explicitly`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAPresenter(
                    modifier = Modifier,
                    question = question("How do I start reading the Bible?"),
                    qaSettings = QASettings(),
                    outputRole = Constants.OUTPUT_ROLE_NORMAL,
                    transitionAlpha = 0.5f,
                )
            }
        }
        onNodeWithText("How do I start reading the Bible?", substring = true).assertExists()
    }

    @Test
    fun `every horizontal alignment renders the question`() {
        listOf(Constants.LEFT, Constants.RIGHT, Constants.CENTER).forEach { alignment ->
            renderQuestion(QASettings(horizontalAlignment = alignment))
        }
    }

    @Test
    fun `every position renders the question`() {
        listOf(
            Constants.TOP_LEFT, Constants.TOP_CENTER, Constants.TOP_RIGHT,
            Constants.CENTER_LEFT, Constants.CENTER, Constants.CENTER_RIGHT,
            Constants.BOTTOM_LEFT, Constants.BOTTOM_CENTER, Constants.BOTTOM_RIGHT,
        ).forEach { position -> renderQuestion(QASettings(position = position)) }
    }

    @Test
    fun `bold italic underline and shadow do not break the render`() {
        renderQuestion(QASettings(bold = true, italic = true, underline = true, shadow = true))
    }

    @Test
    fun `a literal transparent background color renders the question`() {
        renderQuestion(QASettings(backgroundColor = "transparent"))
    }

    // ── QAQRCodePresenter ────────────────────────────────────────────────────────

    @Test
    fun `the QR image is shown for a normal output role`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(url = "https://example.church/qa")
            }
        }
        onNodeWithContentDescription("QR Code").assertExists()
    }

    @Test
    fun `every QAQRCodePresenter parameter can be overridden explicitly`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(
                    modifier = Modifier,
                    url = "https://example.church/qa",
                    qaSettings = QASettings(),
                    outputRole = Constants.OUTPUT_ROLE_NORMAL,
                    transitionAlpha = 0.5f,
                )
            }
        }
        onNodeWithContentDescription("QR Code").assertExists()
    }

    @Test
    fun `key output role shows a plain box instead of the QR image`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(url = "https://example.church/qa", outputRole = Constants.OUTPUT_ROLE_KEY)
            }
        }
        onNodeWithContentDescription("QR Code").assertDoesNotExist()
    }

    @Test
    fun `the default QR message is shown when none is configured`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(url = "https://example.church/qa")
            }
        }
        onNodeWithText("Scan to ask a question", substring = true).assertExists()
    }

    @Test
    fun `a custom QR message replaces the default`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(
                    url = "https://example.church/qa",
                    qaSettings = QASettings(qrCodeMessage = "Text CHURCH to 12345"),
                )
            }
        }
        onNodeWithText("Text CHURCH to 12345", substring = true).assertExists()
        onNodeWithText("Scan to ask a question", substring = true).assertDoesNotExist()
    }

    @Test
    fun `an unencodable url shows the message without the QR image`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(url = "")
            }
        }
        onNodeWithContentDescription("QR Code").assertDoesNotExist()
        onNodeWithText("Scan to ask a question", substring = true).assertExists()
    }

    @Test
    fun `a literal transparent background color renders the QR presenter`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(1920.dp, 1080.dp)) {
                QAQRCodePresenter(
                    url = "https://example.church/qa",
                    qaSettings = QASettings(backgroundColor = "transparent"),
                )
            }
        }
        onNodeWithContentDescription("QR Code").assertExists()
    }
}
