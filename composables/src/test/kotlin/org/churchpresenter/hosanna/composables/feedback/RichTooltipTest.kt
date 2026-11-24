@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class RichTooltipTest {

    private var actions = 0

    private fun ComposeUiTest.start(action: String? = "Learn more") {
        mainClock.autoAdvance = false
        setContent {
            HosannaSurface {
                Box(Modifier.padding(start = 160.dp)) {
                    RichTooltip(
                        "Companion port", "Companion connects here.",
                        actionText = action, onAction = { actions++ },
                    ) {
                        IconButton(testIcon(), "Help", {})
                    }
                }
            }
        }
        wait(100)
    }

    private fun ComposeUiTest.wait(ms: Long) {
        mainClock.advanceTimeBy(ms)
        waitForIdle()
    }

    private fun ComposeUiTest.help() = onNodeWithContentDescription("Help")

    private fun ComposeUiTest.away() = help().performMouseInput { moveTo(Offset(-500f, -500f)) }

    @Test
    fun `hover opens the card after a longer pause`() = runComposeUiTest {
        start()
        help().performMouseInput { moveTo(center) }
        wait(400)
        onNodeWithText("Companion port").assertDoesNotExist()
        wait(200)
        onNodeWithText("Companion port").assertExists()
        onNodeWithText("Companion connects here.").assertExists()
    }

    @Test
    fun `leaving closes it after a grace period`() = runComposeUiTest {
        start()
        help().performMouseInput { moveTo(center) }
        wait(600)
        away()
        wait(200)
        onNodeWithText("Companion port").assertExists()
        wait(300)
        onNodeWithText("Companion port").assertDoesNotExist()
    }

    @Test
    fun `the pointer can travel onto the card and press its action`() = runComposeUiTest {
        start()
        help().performMouseInput { moveTo(center) }
        wait(600)
        away()
        onNodeWithText("Learn more").performMouseInput { moveTo(center) }
        wait(600)
        onNodeWithText("Companion port").assertExists()
        onNodeWithText("Learn more").performMouseInput { click(center) }
        wait(50)
        assertEquals(1, actions)
    }

    @Test
    fun `a click opens it at once and a second click closes it`() = runComposeUiTest {
        start()
        help().performMouseInput { click(center) }
        wait(50)
        onNodeWithText("Companion port").assertExists()
        help().performMouseInput { click(center) }
        wait(200)
        onNodeWithText("Companion port").assertDoesNotExist()
    }

    @Test
    fun `Esc closes it`() = runComposeUiTest {
        start()
        help().performMouseInput { click(center) }
        wait(50)
        help().requestFocus()
        help().performKeyInput { pressKey(Key.Escape) }
        wait(200)
        onNodeWithText("Companion port").assertDoesNotExist()
    }

    @Test
    fun `without action text there is no button`() = runComposeUiTest {
        start(action = null)
        help().performMouseInput { click(center) }
        wait(50)
        onNodeWithText("Companion port").assertExists()
        onNodeWithText("Learn more").assertDoesNotExist()
    }
}
