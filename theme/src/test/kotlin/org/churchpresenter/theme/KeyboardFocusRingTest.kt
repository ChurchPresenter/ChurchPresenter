package org.churchpresenter.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.assertIsFocused
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * The keyboard focus ring: drawn just outside a control that Tab moved focus to, in the theme's
 * primary, and never after a mouse click -- which would leave a ring on every key pressed.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardFocusRingTest {

    private val ringColor = Color(0xFF3366FF)

    private fun ComposeUiTest.key() = setContent {
        MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = ringColor)) {
            Box(Modifier.background(Color.White).padding(10.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .keyboardFocusRing(AppShape(6.dp))
                        .clickable {}
                        .testTag("key"),
                )
            }
        }
    }

    /** The pixel 3dp outside the key's left edge, halfway down -- where the ring is drawn. */
    private fun ComposeUiTest.justOutside(): Color {
        waitForIdle()
        val map = onRoot().captureToImage().toPixelMap()
        val density = density.density
        return map[((10 - 3) * density).toInt(), ((10 + 20) * density).toInt()]
    }

    @Test
    fun `focus from the keyboard draws the ring outside the control`() = runComposeUiTest {
        key()
        assertEquals(Color.White, justOutside(), "no ring before anything has focus")
        onRoot().performKeyInput { pressKey(Key.Tab) }
        assertEquals(ringColor, justOutside())
    }

    @Test
    fun `a mouse click leaves no ring`() = runComposeUiTest {
        key()
        onNodeWithTag("key").performClick()
        assertNotEquals(ringColor, justOutside())
    }

    /** In the app's own theme: the window shows rings only once Tab has been used, not before. */
    private fun ComposeUiTest.themedKey(focusOnOpen: FocusRequester) = setContent {
        ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
            Box(Modifier.background(Color.White).padding(10.dp)) {
                Box(
                    Modifier
                        .size(40.dp)
                        .focusRequester(focusOnOpen)
                        .keyboardFocusRing(AppShape(6.dp))
                        .clickable {}
                        .testTag("key"),
                )
            }
        }
        LaunchedEffect(Unit) { focusOnOpen.requestFocus() }
    }

    private fun ComposeUiTest.ringShowing(): Boolean {
        waitForIdle()
        val map = onRoot().captureToImage().toPixelMap()
        val pixel = map[((10 - 3) * density.density).toInt(), ((10 + 20) * density.density).toInt()]
        return pixel != Color.White
    }

    @Test
    fun `a control focused when the window opens shows no ring until Tab is used`() = runComposeUiTest {
        val requester = FocusRequester()
        themedKey(requester)
        onNodeWithTag("key").assertIsFocused()
        assertEquals(false, ringShowing(), "focused on opening, but nobody has used Tab")

        onRoot().performKeyInput { pressKey(Key.Tab) }
        runOnIdle { requester.requestFocus() }
        assertEquals(true, ringShowing(), "once Tab is used, keyboard focus shows")

        onNodeWithTag("key").performClick()
        assertEquals(false, ringShowing(), "a pointer press puts the rings away again")
    }
}
