@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.appsettings

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import javax.swing.JOptionPane
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The Swing answers behind the System page's questions -- each kind of message and question put
 * the way the operator expects -- with `JOptionPane` itself stood in for, since a real dialog
 * cannot open headless. And the wizard's rail and mock panel given what the app gives them.
 */
class SwingSettingsActionUiTest {

    @BeforeTest
    fun stubDialogs() {
        mockkStatic(JOptionPane::class)
        every { JOptionPane.showMessageDialog(any(), any(), any(), any()) } answers { }
        every { JOptionPane.showConfirmDialog(any(), any(), any(), any(), any()) } returns JOptionPane.NO_OPTION
        every {
            JOptionPane.showOptionDialog(any(), any(), any(), any(), any(), any(), any(), any())
        } returns 1
    }

    @AfterTest
    fun restore() {
        unmockkAll()
    }

    @Test
    fun `an error is an error message and a question is a question`() {
        SwingSettingsActionUi.showMessage("Disk full", "Export", SettingsMessageKind.ERROR)
        verify { JOptionPane.showMessageDialog(any(), "Disk full", "Export", JOptionPane.ERROR_MESSAGE) }

        assertFalse(SwingSettingsActionUi.confirm("Clear the cache?", "Import", SettingsQuestionKind.QUESTION))
        verify {
            JOptionPane.showConfirmDialog(
                any(), "Clear the cache?", "Import", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
            )
        }
    }

    @Test
    fun `picking an option asks with every option and answers which was picked`() {
        val options = arrayOf("Keep", "Replace", "Cancel")
        assertEquals(1, SwingSettingsActionUi.pickOption("Which?", "Import", options))
        verify {
            JOptionPane.showOptionDialog(
                any(), "Which?", "Import", JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, options, "Keep",
            )
        }
    }

    @Test
    fun `the rail takes the modifier it is given and the mock panel its trailing slot`() = runComposeUiTest {
        var skipped = false
        setContent {
            Column {
                WizardRail(
                    steps = listOf(WizardRailStep("Language", "English")),
                    currentStep = 0,
                    onSelectStep = {},
                    onSkip = { skipped = true },
                    modifier = Modifier.testTag("rail"),
                )
                WizardMockPanel(title = "Preview", trailing = { Text("live") }) { Text("body") }
                WizardMockPanel(title = "Plain") { Text("plain body") }
            }
        }
        onNodeWithTag("rail").assertExists()
        onNodeWithText("live").assertExists()
        onNodeWithText("plain body").assertExists()
        assertFalse(skipped)
    }
}
