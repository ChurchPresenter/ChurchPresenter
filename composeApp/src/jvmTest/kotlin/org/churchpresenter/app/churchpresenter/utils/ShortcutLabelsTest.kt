@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.core.models.shortcuts.KeyChord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How a binding is written out: the spelled-out form, the keycaps the shortcuts dialog draws, and
 * the text the search box matches against.
 *
 * `isMac` is read once from `os.name` into a file-level `val`, and faking `os.name` breaks skiko for
 * the rest of the JVM, so the platform-dependent forms are exercised through `label(useSymbols=)` —
 * which takes the choice as a parameter for exactly this reason — rather than by swapping the OS.
 */
class ShortcutLabelsTest {

    /** Reads a `@Composable` String function back out of a composition. */
    private fun composed(block: @Composable () -> String): String {
        var result = ""
        runComposeUiTest {
            setContent { MaterialTheme { result = block() } }
            waitForIdle()
        }
        return result
    }

    private fun chord(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = KeyChord.of(key, ctrl = ctrl, shift = shift, alt = alt, meta = meta)

    private fun map(vararg entries: Pair<ShortcutAction, List<KeyChord>>) = ShortcutMap(entries.toMap())

    // ── The spelled-out form ──────────────────────────────────────────────────

    @Test
    fun `a bare key is just its name`() {
        assertEquals("Space", composed { chord(Key.Spacebar).label(useSymbols = false) })
    }

    @Test
    fun `modifiers are spelled out in a fixed order`() {
        val all = chord(Key.N, ctrl = true, shift = true, alt = true, meta = true)
        assertEquals("Ctrl+Meta+Alt+Shift+N", composed { all.label(useSymbols = false) })
    }

    @Test
    fun `only the modifiers held are written`() {
        assertEquals("Ctrl+Shift+Z", composed { chord(Key.Z, ctrl = true, shift = true).label(useSymbols = false) })
    }

    // ── The symbol form ───────────────────────────────────────────────────────

    @Test
    fun `the symbol form has no separator and its own fixed order`() {
        val all = chord(Key.N, ctrl = true, shift = true, alt = true, meta = true)
        assertEquals("⌃⌥⇧⌘N", composed { all.label(useSymbols = true) })
    }

    @Test
    fun `a bare key is the same in both forms`() {
        val bare = chord(Key.Escape)
        assertEquals(
            composed { bare.label(useSymbols = false) },
            composed { bare.label(useSymbols = true) },
        )
    }

    // ── Key names ─────────────────────────────────────────────────────────────

    @Test
    fun `the named keys are written by name, not by code`() {
        val expected = mapOf(
            Key.Spacebar to "Space",
            Key.Escape to "Esc",
            Key.Enter to "Enter",
            Key.NumPadEnter to "Enter",
            Key.Tab to "Tab",
            Key.Backspace to "Backspace",
            Key.Delete to "Delete",
            Key.Insert to "Insert",
            Key.MoveHome to "Home",
            Key.MoveEnd to "End",
            Key.PageUp to "PgUp",
            Key.PageDown to "PgDn",
        )
        for ((key, name) in expected) {
            assertEquals(name, composed { chord(key).label(useSymbols = false) }, "$key")
        }
    }

    @Test
    fun `the arrows are drawn as arrows`() {
        val expected = mapOf(
            Key.DirectionUp to "↑",
            Key.DirectionDown to "↓",
            Key.DirectionLeft to "←",
            Key.DirectionRight to "→",
        )
        for ((key, glyph) in expected) {
            assertEquals(glyph, composed { chord(key).label(useSymbols = false) }, "$key")
        }
    }

    @Test
    fun `punctuation keys are drawn as the character they type`() {
        val expected = mapOf(
            Key.Period to ".",
            Key.Comma to ",",
            Key.Semicolon to ";",
            Key.Apostrophe to "'",
            Key.Slash to "/",
            Key.Backslash to "\\",
            Key.LeftBracket to "[",
            Key.RightBracket to "]",
            Key.Minus to "-",
            Key.Equals to "=",
            Key.Grave to "`",
        )
        for ((key, glyph) in expected) {
            assertEquals(glyph, composed { chord(key).label(useSymbols = false) }, "$key")
        }
    }

    @Test
    fun `an unnamed key falls back to a readable name rather than a blank`() {
        val label = composed { chord(Key.F12).label(useSymbols = false) }
        assertTrue(label.isNotBlank(), "an unlisted key must still be nameable")
        assertTrue(label.contains("F12"), "and should name the key it is: $label")
    }

    // ── The keycaps ───────────────────────────────────────────────────────────

    @Test
    fun `a bare key is one cap`() {
        var caps = emptyList<String>()
        runComposeUiTest {
            setContent { MaterialTheme { caps = chord(Key.Escape).keyCaps() } }
            waitForIdle()
        }
        assertEquals(listOf("Esc"), caps)
    }

    @Test
    fun `every modifier held adds a cap of its own, with the key last`() {
        var caps = emptyList<String>()
        runComposeUiTest {
            setContent {
                MaterialTheme { caps = chord(Key.N, ctrl = true, shift = true, alt = true, meta = true).keyCaps() }
            }
            waitForIdle()
        }
        assertEquals(5, caps.size, "four modifiers and the key: $caps")
        assertEquals("N", caps.last())
    }

    @Test
    fun `the caps spell the same chord the label does`() {
        var caps = emptyList<String>()
        var label = ""
        val z = chord(Key.Z, ctrl = true, shift = true)
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    caps = z.keyCaps()
                    label = z.label()
                }
            }
            waitForIdle()
        }
        assertEquals(3, caps.size, "two modifiers and the key: $caps")
        assertEquals(label, caps.joinToString(if (label.contains("+")) "+" else ""))
    }

    // ── Search text ───────────────────────────────────────────────────────────

    @Test
    fun `search text carries both renderings`() {
        val text = composed { chord(Key.N, ctrl = true).searchText() }
        assertTrue(text.contains("ctrl+n"), "the spelled-out form: $text")
        assertTrue(text.contains("⌃n"), "and the symbol form: $text")
    }

    @Test
    fun `search text names every modifier the way people type it`() {
        val text = composed { chord(Key.N, ctrl = true, meta = true, alt = true, shift = true).searchText() }
        for (word in listOf("ctrl", "control", "meta", "cmd", "command", "alt", "option", "shift")) {
            assertTrue(text.contains(word), "\"$word\" must be searchable: $text")
        }
    }

    @Test
    fun `an arrow is searchable by a name that can be typed`() {
        assertTrue(composed { chord(Key.DirectionLeft).searchText() }.contains("left arrow"))
    }

    @Test
    fun `search text is lower-cased so a typed query matches`() {
        val text = composed { chord(Key.N, ctrl = true, shift = true).searchText() }
        assertEquals(text.lowercase(), text)
    }

    // ── A whole map ───────────────────────────────────────────────────────────

    private val action = ShortcutAction.entries.first()
    private val other = ShortcutAction.entries[1]

    @Test
    fun `an action with one binding reads as that binding`() {
        val bound = map(action to listOf(chord(Key.Escape)))
        assertEquals("Esc", composed { bound.label(action) })
    }

    @Test
    fun `two bindings are joined`() {
        val bound = map(action to listOf(chord(Key.DirectionLeft), chord(Key.DirectionUp)))
        assertEquals("← / ↑", composed { bound.label(action) })
    }

    @Test
    fun `an unbound action reads empty, so a caller can drop the hint`() {
        assertEquals("", composed { map().label(action) })
    }

    @Test
    fun `an unbound action reads Not set where something must be drawn`() {
        assertEquals("Not set", composed { map().labelOrUnbound(action) })
    }

    @Test
    fun `a bound action is unaffected by the Not set substitution`() {
        val bound = map(action to listOf(chord(Key.Tab)))
        assertEquals("Tab", composed { bound.labelOrUnbound(action) })
    }

    @Test
    fun `a pair of opposed actions is drawn side by side`() {
        val bound = map(
            action to listOf(chord(Key.DirectionLeft)),
            other to listOf(chord(Key.DirectionRight)),
        )
        assertEquals("←  →", composed { bound.pairLabel(action, other) })
    }

    @Test
    fun `a pair with one half unbound drops the empty half`() {
        val bound = map(action to listOf(chord(Key.DirectionLeft)))
        assertEquals("←", composed { bound.pairLabel(action, other) })
    }

    @Test
    fun `a pair with neither half bound reads empty`() {
        assertEquals("", composed { map().pairLabel(action, other) })
    }

    @Test
    fun `a map's search text covers every chord bound to the action`() {
        val bound = map(action to listOf(chord(Key.DirectionLeft), chord(Key.N, ctrl = true)))
        val text = composed { bound.searchText(action) }
        assertTrue(text.contains("left arrow"), text)
        assertTrue(text.contains("ctrl+n"), text)
    }

    @Test
    fun `an unbound action has nothing to search`() {
        assertEquals("", composed { map().searchText(action) })
    }
}
