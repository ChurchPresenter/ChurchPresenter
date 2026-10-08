package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.utils.keyDown
import org.churchpresenter.app.churchpresenter.utils.keyUp
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.KeyboardShortcutSettings
import org.churchpresenter.sharedui.models.ShortcutAction
import org.churchpresenter.sharedui.utils.LocalShortcuts
import org.churchpresenter.sharedui.utils.ShortcutMap
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Go Live key: the decision every tab takes ([handleGoLiveKey]), the root that the tabs without
 * key handling of their own wear ([goLiveKeyTarget]), and the key on the Go Live button's tooltip.
 */
@OptIn(ExperimentalTestApi::class)
class GoLiveKeyTest {

    private fun rebound(vararg chords: KeyChord) = ShortcutMap.from(
        KeyboardShortcutSettings(overrides = mapOf(ShortcutAction.GO_LIVE.name to chords.toList())),
    )

    // ── handleGoLiveKey ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `enter on the tab root goes live`() {
        var live = 0
        assertTrue(ShortcutMap.DEFAULT.handleGoLiveKey(keyDown(Key.Enter), rootFocused = true, enabled = true) { live++ })
        assertEquals(1, live)
    }

    @Test
    fun `with nothing to send the key is claimed and does nothing`() {
        var live = 0
        assertTrue(ShortcutMap.DEFAULT.handleGoLiveKey(keyDown(Key.Enter), rootFocused = true, enabled = false) { live++ })
        assertEquals(0, live)
    }

    @Test
    fun `away from the tab root the key is left to whatever has focus`() {
        var live = 0
        assertFalse(ShortcutMap.DEFAULT.handleGoLiveKey(keyDown(Key.Enter), rootFocused = false, enabled = true) { live++ })
        assertFalse(ShortcutMap.DEFAULT.handleGoLiveKey(keyUp(Key.Enter), rootFocused = true, enabled = true) { live++ })
        assertEquals(0, live)
    }

    @Test
    fun `a rebound key takes over and enter stops going live`() {
        val map = rebound(KeyChord.of(Key.F5))
        var live = 0
        assertFalse(map.handleGoLiveKey(keyDown(Key.Enter), rootFocused = true, enabled = true) { live++ })
        assertTrue(map.handleGoLiveKey(keyDown(Key.F5), rootFocused = true, enabled = true) { live++ })
        assertEquals(1, live)
    }

    // ── goLiveKeyTarget ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `the tab root takes the keyboard on opening and goes live on enter`() = runComposeUiTest {
        var live = 0
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).goLiveKeyTarget(enabled = true) { live++ })
            }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(1, live)
    }

    @Test
    fun `enter in a text field on the tab is the field's, not go live`() = runComposeUiTest {
        var live = 0
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).goLiveKeyTarget(enabled = true) { live++ }) {
                    var text by mutableStateOf("")
                    BasicTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("field"))
                }
            }
        }
        onNodeWithTag("field").requestFocus()
        onNodeWithTag("field").performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(0, live)
    }

    @Test
    fun `a tab that opens on its search box leaves the keyboard there`() = runComposeUiTest {
        var live = 0
        setContent {
            MaterialTheme {
                Box(Modifier.size(200.dp).goLiveKeyTarget(enabled = true, focusOnOpen = false) { live++ })
            }
        }
        waitForIdle()
        onRoot().performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals(0, live, "nothing took the keyboard, so the key reaches no tab root")
    }

    // ── The Go Live button ──────────────────────────────────────────────────────────────────────

    @Test
    fun `the button keeps its name while its tooltip names the key`() = runComposeUiTest {
        setContent {
            MaterialTheme { GoLiveButton(onClick = {}, tooltipText = "Go Live", showsShortcut = true) }
        }
        onNodeWithContentDescription("Go Live").assertExists("the key belongs to the tooltip, not the name")
        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        // The key's own label differs by platform (a symbol on macOS), so only its place is pinned.
        onNodeWithText("Go Live (", substring = true, useUnmergedTree = true).assertExists()
    }

    @Test
    fun `a rebound key is the one the tooltip names, and an unbound one is left out`() = runComposeUiTest {
        var map by mutableStateOf(rebound(KeyChord.of(Key.F5)))
        setContent {
            CompositionLocalProvider(LocalShortcuts provides map) {
                MaterialTheme { GoLiveButton(onClick = {}, tooltipText = "Go Live", showsShortcut = true) }
            }
        }
        onNode(hasClickAction()).performMouseInput { moveTo(center) }
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        onNodeWithText("Go Live (F5)", useUnmergedTree = true).assertExists()

        map = rebound()
        mainClock.advanceTimeBy(TOOLTIP_WAIT_MS)
        onNodeWithText("Go Live", useUnmergedTree = true).assertExists()
        onNode(hasClickAction()).performClick()
    }

    private companion object {
        const val TOOLTIP_WAIT_MS = 1_500L
    }
}
