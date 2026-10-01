@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanningCenterConnectDialogContentTest {

    private fun connectDialog(
        isConnecting: Boolean = false,
        connectionError: String? = null,
        block: ComposeUiTest.(dismissCount: () -> Int, connectCount: () -> Int) -> Unit,
    ) = runComposeUiTest {
        var dismissCount = 0
        var connectCount = 0
        setContent {
            MaterialTheme {
                PlanningCenterConnectDialogContent(
                    isConnecting = isConnecting,
                    connectionError = connectionError,
                    onDismiss = { dismissCount++ },
                    onConnectClick = { connectCount++ },
                )
            }
        }
        block({ dismissCount }, { connectCount })
    }

    @Test
    fun `the description is shown and there is no error text by default`() = connectDialog { _, _ ->
        onNodeWithText("Import a Planning Center Services plan", substring = true).assertIsDisplayed()
    }

    @Test
    fun `clicking Connect invokes the callback`() = connectDialog { _, connectCount ->
        onNodeWithText("Connect to Planning Center").performClick()
        assertEquals(1, connectCount())
    }

    @Test
    fun `clicking Cancel dismisses the dialog`() = connectDialog { dismissCount, _ ->
        onNodeWithText("Cancel").performClick()
        assertEquals(1, dismissCount())
    }

    @Test
    fun `while connecting the button shows a spinner and its own label, and is disabled`() =
        connectDialog(isConnecting = true) { _, _ ->
            onNodeWithText("Connect to Planning Center").assertDoesNotExist()
            onNodeWithText("Connecting…").assertIsDisplayed().assertIsNotEnabled()
        }

    @Test
    fun `a connection error is shown to the operator`() =
        connectDialog(connectionError = "Network error — check your connection") { _, _ ->
            onNodeWithText("Network error — check your connection", substring = true).assertIsDisplayed()
        }
}
