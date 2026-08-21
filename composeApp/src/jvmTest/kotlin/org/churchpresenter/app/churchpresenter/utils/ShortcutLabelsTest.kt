package org.churchpresenter.app.churchpresenter.utils

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.shortcuts.KeyChord
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ShortcutLabelsTest {

    private fun chord(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = KeyChord(keyCode = key.keyCode, ctrl = ctrl, shift = shift, alt = alt, meta = meta)

    private fun labels(useSymbols: Boolean, vararg chords: KeyChord): List<String> {
        val out = mutableListOf<String>()
        runComposeUiTest {
            setContent {
                out.clear()
                chords.forEach { out.add(it.label(useSymbols)) }
            }
            waitForIdle()
        }
        return out.toList()
    }

    @Test
    fun `the named keys are spelled out rather than shown as codes`() {
        val keys = listOf(
            Key.Spacebar, Key.Escape, Key.Enter, Key.NumPadEnter, Key.Tab, Key.Backspace,
            Key.Delete, Key.Insert, Key.MoveHome, Key.MoveEnd, Key.PageUp, Key.PageDown,
        )

        val rendered = labels(false, *keys.map { chord(it) }.toTypedArray())

        assertEquals(keys.size, rendered.size)
        rendered.forEachIndexed { index, label ->
            assertTrue(label.isNotBlank(), "${keys[index]} rendered blank")
            assertTrue(
                !label.contains("Key:") && !label.contains("Key("),
                "${keys[index]} fell through to the Compose toString: $label",
            )
        }
    }

    @Test
    fun `both enter keys read the same`() {
        val rendered = labels(false, chord(Key.Enter), chord(Key.NumPadEnter))

        assertEquals(rendered[0], rendered[1], "a shortcut bound to either enter must read the same")
    }

    @Test
    fun `the arrows are drawn as arrows`() {
        val rendered = labels(
            false,
            chord(Key.DirectionUp), chord(Key.DirectionDown),
            chord(Key.DirectionLeft), chord(Key.DirectionRight),
        )

        assertEquals(listOf("↑", "↓", "←", "→"), rendered)
    }

    @Test
    fun `punctuation is drawn as itself`() {
        val expected = listOf(
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

        val rendered = labels(false, *expected.map { chord(it.first) }.toTypedArray())

        assertEquals(expected.map { it.second }, rendered)
    }

    @Test
    fun `a key with no name of its own still reads as something`() {
        val rendered = labels(false, chord(Key.F12)).single()

        assertTrue(rendered.isNotBlank())
        assertTrue(rendered.contains("F12"), "the fallback must still name the key: $rendered")
    }

    @Test
    fun `a letter key is its own label`() {
        val rendered = labels(false, chord(Key.S)).single()

        assertTrue(rendered.isNotBlank())
    }

    @Test
    fun `spelled-out modifiers are joined with plus signs in a fixed order`() {
        val rendered = labels(
            false,
            chord(Key.S, ctrl = true, shift = true, alt = true, meta = true),
        ).single()

        assertEquals(4, rendered.count { it == '+' }, "four modifiers means four separators: $rendered")
    }

    @Test
    fun `a chord with no modifiers is just its key`() {
        val rendered = labels(false, chord(Key.Escape)).single()

        assertTrue(!rendered.contains("+"), "nothing to join: $rendered")
    }

    @Test
    fun `the mac form uses symbols and no separator`() {
        val rendered = labels(
            true,
            chord(Key.S, ctrl = true, shift = true, alt = true, meta = true),
        ).single()

        assertEquals("⌃⌥⇧⌘S", rendered)
    }

    @Test
    fun `the mac form orders modifiers the mac way whatever was pressed`() {
        val rendered = labels(true, chord(Key.S, meta = true, shift = true)).single()

        assertEquals("⇧⌘S", rendered, "shift always precedes command on a Mac")
    }

    @Test
    fun `a single mac modifier carries only its own symbol`() {
        val rendered = labels(
            true,
            chord(Key.S, ctrl = true),
            chord(Key.S, alt = true),
            chord(Key.S, shift = true),
            chord(Key.S, meta = true),
        )

        assertEquals(listOf("⌃S", "⌥S", "⇧S", "⌘S"), rendered)
    }

    @Test
    fun `the mac form still names a spelled-out key`() {
        val rendered = labels(true, chord(Key.Escape, meta = true)).single()

        assertTrue(rendered.startsWith("⌘"), rendered)
        assertTrue(rendered.length > 1, "the key name must follow the symbol: $rendered")
    }
}
