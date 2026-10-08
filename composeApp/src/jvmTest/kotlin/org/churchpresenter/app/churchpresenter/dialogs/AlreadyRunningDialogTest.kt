package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.waitUntilAtLeastOneExists
import kotlin.test.Test
import kotlin.test.assertEquals

/** The body of the dialog a second launch shows: it says what happened, and OK closes it. */
@OptIn(ExperimentalTestApi::class)
class AlreadyRunningDialogTest {

    @Test
    fun `it says the app is already running, and ok closes it`() = runComposeUiTest {
        var dismissed = 0
        setContent { MaterialTheme { AlreadyRunningContent(onDismiss = { dismissed++ }) } }
        waitUntilAtLeastOneExists(hasText("OK"), timeoutMillis = 5_000L)

        onNodeWithText("ChurchPresenter is already running").assertExists()
        onNodeWithText("Switch to the ChurchPresenter window", substring = true).assertExists()
        onNodeWithText("OK").performClick()
        waitForIdle()

        assertEquals(1, dismissed)
    }
}
