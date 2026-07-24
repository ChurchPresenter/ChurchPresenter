package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.models.Question
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * The audience question shown on screen, and the QR code people scan to submit one.
 *
 * [QAPresenter] puts the current question's text on the screen; a null question must leave it blank
 * rather than stranding the previously-shown question during a transition to nothing. Separately,
 * [generateQRCodeBitmap] renders the join URL to a scannable bitmap — it must produce a square image
 * of the requested size for valid input and fail soft (null) rather than throw on input the encoder
 * rejects, so a bad URL never crashes the output window.
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

    // ── QR code generation (pure) ───────────────────────────────────────────────

    @Test
    fun `a QR code is generated at the requested square size`() {
        val bitmap = assertNotNull(
            generateQRCodeBitmap("https://example.church/qa", 240),
            "a valid URL must produce a scannable code",
        )
        assertEquals(240, bitmap.width, "a QR code must be square at the requested size")
        assertEquals(240, bitmap.height)
    }

    @Test
    fun `an unencodable input fails soft instead of crashing the output`() {
        // The zxing encoder rejects an empty string; the output window must not take the exception.
        assertNull(generateQRCodeBitmap("", 240), "an empty payload must yield null, not throw")
    }
}
