@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.hosanna.composables.feedback

import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.testing.HosannaSurface
import org.churchpresenter.hosanna.composables.testing.testIcon
import kotlin.test.Test

class TooltipTest {

    @Composable
    private fun Toolbar(enabled: Boolean = true) {
        HosannaSurface {
            Row {
                Tooltip("Settings", shortcut = "Ctrl ,") { IconButton(testIcon(), "Settings", {}) }
                Tooltip("Open", enabled = enabled) { IconButton(testIcon(), "Open", {}) }
                Tooltip("Connect a Stream Deck first") { IconButton(testIcon(), "Deck", {}, enabled = false) }
            }
        }
    }

    private fun ComposeUiTest.start(enabled: Boolean = true) {
        mainClock.autoAdvance = false
        setContent { Toolbar(enabled) }
        mainClock.advanceTimeBy(100)
    }

    private fun ComposeUiTest.hover(name: String) =
        onNodeWithContentDescription(name).performMouseInput { moveTo(center) }

    private fun ComposeUiTest.leave() =
        onNodeWithContentDescription("Settings").performMouseInput { moveTo(Offset(-200f, -200f)) }

    private fun ComposeUiTest.wait(ms: Long) {
        mainClock.advanceTimeBy(ms)
        waitForIdle()
    }

    @Test
    fun `hover shows the name and shortcut after a pause`() = runComposeUiTest {
        start()
        hover("Settings")
        wait(300)
        onNodeWithText("Settings").assertDoesNotExist()
        wait(200)
        onNodeWithText("Settings").assertExists()
        onNodeWithText("Ctrl ,").assertExists()
    }

    @Test
    fun `leaving hides it shortly after`() = runComposeUiTest {
        start()
        hover("Settings")
        wait(500)
        leave()
        wait(40)
        onNodeWithText("Settings").assertExists()
        wait(300)
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `a click hides it at once and it stays hidden on that control`() = runComposeUiTest {
        start()
        hover("Settings")
        wait(500)
        onNodeWithContentDescription("Settings").performMouseInput { press(); release() }
        wait(200)
        onNodeWithText("Settings").assertDoesNotExist()
        wait(1000)
        onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `keyboard focus shows it right away and Esc hides it`() = runComposeUiTest {
        start()
        onNodeWithContentDescription("Open").requestFocus()
        wait(50)
        onNodeWithText("Open").assertExists()
        onNodeWithContentDescription("Open").performKeyInput { pressKey(Key.Escape) }
        wait(200)
        onNodeWithText("Open").assertDoesNotExist()
    }

    @Test
    fun `moving along a toolbar shows the next one without the pause`() = runComposeUiTest {
        start()
        hover("Settings")
        wait(500)
        hover("Open")
        wait(150)
        onNodeWithText("Open").assertExists()
    }

    @Test
    fun `a tooltip removed while open lets the next one wait again`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var shown by mutableStateOf(true)
        setContent {
            HosannaSurface {
                Row {
                    Tooltip("Open") { IconButton(testIcon(), "Open", {}) }
                    if (shown) Tooltip("Settings") { IconButton(testIcon(), "Settings", {}) }
                }
            }
        }
        wait(100)
        hover("Settings")
        wait(500)
        onNodeWithText("Settings").assertExists()
        shown = false
        wait(1000)
        hover("Open")
        wait(200)
        onNodeWithText("Open").assertDoesNotExist()
        wait(300)
        onNodeWithText("Open").assertExists()
    }

    @Test
    fun `a disabled control still explains itself`() = runComposeUiTest {
        start()
        hover("Deck")
        wait(500)
        onNodeWithText("Connect a Stream Deck first").assertExists()
    }

    @Test
    fun `a turned off tooltip never shows`() = runComposeUiTest {
        start(enabled = false)
        hover("Open")
        wait(1000)
        onNodeWithText("Open").assertDoesNotExist()
    }
}
