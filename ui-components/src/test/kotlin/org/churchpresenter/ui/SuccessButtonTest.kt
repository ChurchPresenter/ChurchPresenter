package org.churchpresenter.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The filled, high-emphasis button used for the primary action in the app's dialogs (e.g.
 * "Save", "Go Live") — a [Button][androidx.compose.material3.Button] styled with the theme's
 * inverse surface colors.
 */
@OptIn(ExperimentalTestApi::class)
class SuccessButtonTest {

    @Test
    fun `the button shows its text`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SuccessButton(text = "Save", onClick = { })
            }
        }
        onNodeWithText("Save").assertExists("the button must show the caller's label")
    }

    @Test
    fun `clicking the button invokes onClick`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                SuccessButton(text = "Save", onClick = { clicked = true })
            }
        }
        onNode(hasClickAction()).performClick()
        assertTrue(clicked, "clicking the button must invoke the caller's onClick")
    }

    @Test
    fun `an enabled button (the default) can be clicked`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SuccessButton(text = "Save", onClick = { })
            }
        }
        onNode(hasClickAction()).assertIsEnabled()
    }

    @Test
    fun `a disabled button reports itself disabled and ignores clicks`() = runComposeUiTest {
        var clicked = false
        setContent {
            MaterialTheme {
                SuccessButton(text = "Save", onClick = { clicked = true }, enabled = false)
            }
        }
        val button = onNode(hasClickAction())
        button.assertIsNotEnabled()
        button.performClick()
        assertFalse(clicked, "a disabled button must not invoke onClick")
    }

    @Test
    fun `the modifier passed by the caller reaches the button`() = runComposeUiTest {
        setContent {
            MaterialTheme {
                SuccessButton(text = "Save", onClick = { }, modifier = Modifier.testTag("save-button"))
            }
        }
        onNodeWithTag("save-button").assertExists("the caller's modifier must be applied to the button")
    }
}
