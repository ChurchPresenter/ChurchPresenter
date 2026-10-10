@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class KonamiEasterEggContentTest {

    private fun dialog(block: ComposeUiTest.(dismissed: () -> Int) -> Unit) {
        var dismissed = 0
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    KonamiEasterEggDialogContent(onDismiss = { dismissed++ })
                }
            }
            block { dismissed }
        }
    }

    @Test
    fun `the celebration text is shown`() = dialog {
        onNodeWithText("Cheat Code Activated").assertExists()
        onNodeWithText("+30 Blessings Unlocked").assertExists()
        onNodeWithText("You know the code. Now go present some worship.").assertExists()
    }

    @Test
    fun `clicking the button dismisses the dialog`() = dialog { dismissed ->
        onNodeWithText("Let's Go").performClick()
        assertEquals(1, dismissed())
    }
}
