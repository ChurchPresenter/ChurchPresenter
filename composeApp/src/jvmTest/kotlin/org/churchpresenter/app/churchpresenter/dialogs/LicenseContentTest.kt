@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.window.FrameWindowScope
import javax.swing.SwingUtilities
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Stands in for the real [FrameWindowScope] a `Window` provides, without opening one. */
private val fakeFrameWindowScope = object : FrameWindowScope {
    override val window: ComposeWindow get() = error("real window not available in tests")
}

class LicenseContentTest {

    /** Polls until [condition] is true, settling the Swing/Compose queues between checks — the EULA
     * text read runs off the virtual clock, so real wall-clock polling is needed (mirrors
     * `LocalLibraryContentTest.awaitUntil`). */
    private fun ComposeUiTest.awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            SwingUtilities.invokeAndWait { }
            waitForIdle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("timed out after ${timeoutMs}ms waiting for condition")
    }

    private class Result {
        var accepted = 0
        var declined = 0
    }

    private fun dialog(licenseText: String = "Terms and conditions.", block: ComposeUiTest.(Result) -> Unit) {
        val result = Result()
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    LicenseDialogContent(
                        licenseText = licenseText,
                        onAccept = { result.accepted++ },
                        onDecline = { result.declined++ },
                    )
                }
            }
            block(result)
        }
    }

    @Test
    fun `the license text is shown`() = dialog(licenseText = "You must agree to these terms.") {
        onNodeWithText("You must agree to these terms.").assertExists()
    }

    @Test
    fun `clicking I Accept accepts and does not decline`() = dialog { result ->
        onNodeWithText("I Accept").performClick()
        assertEquals(1, result.accepted)
        assertEquals(0, result.declined)
    }

    @Test
    fun `clicking Decline and Exit declines and does not accept`() = dialog { result ->
        onNodeWithText("Decline & Exit").performClick()
        assertEquals(0, result.accepted)
        assertEquals(1, result.declined)
    }

    @Test
    fun `the title and prompt are shown`() = dialog {
        onNodeWithText("End User License Agreement (EULA)").assertExists()
        onNodeWithText("Please read and accept the End User License Agreement to use Church Presenter.").assertExists()
    }

    @Test
    fun `LicenseDialog renders nothing when not visible`() = runComposeUiTest {
        setContent {
            LicenseDialog(isVisible = false, onAccept = {}, onDecline = {})
        }
        onNodeWithText("I Accept").assertDoesNotExist()
    }

    @Test
    fun `loadEulaText reads the real bundled EULA`() = runBlocking {
        val text = loadEulaText()

        assertTrue(text.isNotBlank())
        assertTrue(text.contains("License", ignoreCase = true))
    }

    @Test
    fun `LicenseDialog when visible loads the real EULA text into its content`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                LicenseDialog(
                    isVisible = true,
                    onAccept = {},
                    onDecline = {},
                    windowHost = { _, _, _, _, content -> fakeFrameWindowScope.content() },
                )
            }
        }

        awaitUntil {
            onAllNodesWithText("End User License Agreement (EULA)").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText("CHURCH PRESENTER", substring = true).assertExists()
    }

    @Test
    fun `LicenseDialog when visible passes its title and close request through the window host`() = runComposeUiTest {
        var hostedTitle: String? = null
        var closeRequested = 0

        setContent {
            MaterialTheme {
                LicenseDialog(
                    isVisible = true,
                    onAccept = {},
                    onDecline = { closeRequested++ },
                    windowHost = { title, _, _, onCloseRequest, content ->
                        hostedTitle = title
                        onCloseRequest()
                        fakeFrameWindowScope.content()
                    },
                )
            }
        }
        waitForIdle()

        assertEquals("End User License Agreement (EULA)", hostedTitle)
        assertEquals(1, closeRequested)
    }

    @Test
    fun `LicenseDialog when visible wires I Accept and Decline through to its own callbacks`() = runComposeUiTest {
        var accepted = 0
        var declined = 0

        setContent {
            MaterialTheme {
                LicenseDialog(
                    isVisible = true,
                    onAccept = { accepted++ },
                    onDecline = { declined++ },
                    windowHost = { _, _, _, _, content -> fakeFrameWindowScope.content() },
                )
            }
        }

        onNodeWithText("I Accept").performClick()
        onNodeWithText("Decline & Exit").performClick()

        assertEquals(1, accepted)
        assertEquals(1, declined)
    }
}
