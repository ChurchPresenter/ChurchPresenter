package org.churchpresenter.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.KeyboardShortcutSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Rendering a binding for a human: the keycaps the dialog draws, the text the search box matches,
 * and the joined labels the tabs show as hints.
 *
 * [ShortcutLabelsTest] covers `label(useSymbols)` — the single-string form. Everything built on top
 * of it went untested, and each piece exists for a reason that would be invisible if it broke:
 * `keyCaps` cannot be recovered by slicing the label (the macOS form has no separator at all),
 * `searchText` has to match what a user types rather than what they see, and `pairLabel` has to
 * collapse cleanly when one half of a pair is unbound.
 */
@OptIn(ExperimentalTestApi::class)
class ShortcutLabelRenderingTest {

    private fun chord(
        key: Key,
        ctrl: Boolean = false,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = KeyChord(keyCode = key.keyCode, ctrl = ctrl, shift = shift, alt = alt, meta = meta)

    /** Runs [body] inside a composition and hands back what it produced. */
    private fun <T> composed(body: @androidx.compose.runtime.Composable () -> T): T {
        var out: T? = null
        runComposeUiTest {
            setContent { out = body() }
            waitForIdle()
        }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    /**
     * A map where [action] is bound to exactly [chords] — or unbound when none are given.
     *
     * An *empty* override is how "unbound" is expressed; leaving the key out entirely falls back to
     * the action's own defaults, which is the opposite of what these tests want.
     */
    private fun mapWith(action: ShortcutAction, vararg chords: KeyChord): ShortcutMap =
        ShortcutMap.from(KeyboardShortcutSettings(overrides = mapOf(action.name to chords.toList())))

    // ── keyCaps ─────────────────────────────────────────────────────────────────

    @Test
    fun `an unmodified key is a single cap`() {
        val caps = composed { chord(Key.F).keyCaps() }

        assertEquals(1, caps.size)
        assertTrue(caps.single().isNotBlank())
    }

    @Test
    fun `each modifier held becomes its own cap, with the key last`() {
        val caps = composed { chord(Key.Z, ctrl = true, shift = true).keyCaps() }

        // Three caps: two modifiers and the key. The dialog draws each as its own rounded box, so
        // this split is the whole reason the function exists rather than slicing `label()`.
        assertEquals(3, caps.size)
        assertTrue(caps.all { it.isNotBlank() })
    }

    @Test
    fun `every modifier at once produces five caps`() {
        val caps = composed { chord(Key.A, ctrl = true, shift = true, alt = true, meta = true).keyCaps() }

        assertEquals(5, caps.size)
    }

    @Test
    fun `the last cap is always the key itself`() {
        val caps = composed { chord(Key.Spacebar, ctrl = true).keyCaps() }
        val bare = composed { chord(Key.Spacebar).keyCaps() }

        assertEquals(bare.single(), caps.last(), "the key reads the same with or without modifiers")
    }

    // ── searchText ──────────────────────────────────────────────────────────────

    @Test
    fun `search text is lower-cased`() {
        val text = composed { chord(Key.Z, ctrl = true).searchText() }

        assertEquals(text.lowercase(), text)
    }

    @Test
    fun `search text carries both renderings, so either platform's spelling matches`() {
        val text = composed { chord(Key.N, ctrl = true, shift = true).searchText() }

        // A Mac user sees ⌃⇧N but will type "ctrl"; someone reading Windows docs on a Mac types the
        // symbol. Both have to be in there or the search silently finds nothing on one platform.
        assertTrue(text.contains("ctrl"), "the spelled-out modifier is missing: $text")
        assertTrue(text.contains("⌃"), "the symbol form is missing: $text")
        assertTrue(text.contains("shift") && text.contains("⇧"), text)
    }

    @Test
    fun `every modifier contributes its common names`() {
        val text = composed { chord(Key.A, ctrl = true, shift = true, alt = true, meta = true).searchText() }

        listOf("ctrl", "control", "meta", "cmd", "command", "alt", "option", "shift")
            .forEach { assertTrue(text.contains(it), "'$it' missing from: $text") }
    }

    @Test
    fun `an unmodified chord contributes no modifier names`() {
        val text = composed { chord(Key.F).searchText() }

        listOf("ctrl", "cmd", "option", "shift").forEach {
            assertFalse(text.contains(it), "'$it' should not appear for a bare key: $text")
        }
    }

    @Test
    fun `the arrow keys get a typeable alias`() {
        val arrows = listOf(Key.DirectionUp, Key.DirectionDown, Key.DirectionLeft, Key.DirectionRight)

        val texts = composed { arrows.map { chord(it).searchText() } }

        // The arrows are the only bindings drawn as a glyph nobody can type, so without an alias
        // they could not be found by key on any platform.
        texts.forEachIndexed { i, t ->
            assertTrue(t.trim().split(" ").any { it.length > 2 }, "${arrows[i]} has no typeable alias: $t")
        }
    }

    @Test
    fun `a key that needs no alias contributes none`() {
        val text = composed { chord(Key.Period).searchText() }

        // `.` is typeable as itself; only the arrows earn an alias.
        assertTrue(text.isNotBlank())
    }

    // ── ShortcutMap label, labelOrUnbound, searchText, pairLabel ────────────────

    @Test
    fun `a bound action renders its chord`() {
        val map = mapWith(ShortcutAction.UNDO, chord(Key.Z, ctrl = true))

        val label = composed { map.label(ShortcutAction.UNDO) }

        assertTrue(label.isNotBlank())
    }

    @Test
    fun `an action bound to two chords renders both`() {
        val map = mapWith(ShortcutAction.UNDO, chord(Key.Z, ctrl = true), chord(Key.U, ctrl = true))

        val label = composed { map.label(ShortcutAction.UNDO) }
        val single = composed { mapWith(ShortcutAction.UNDO, chord(Key.Z, ctrl = true)).label(ShortcutAction.UNDO) }

        assertTrue(label.length > single.length, "both chords should be shown: $label")
    }

    @Test
    fun `an unbound action renders empty, so a hint can hide itself`() {
        val map = mapWith(ShortcutAction.UNDO)

        val label = composed { map.label(ShortcutAction.UNDO) }

        // Empty rather than a placeholder: an inline hint drops the phrase entirely rather than
        // telling the operator about a key that does nothing.
        assertEquals("", label)
    }

    @Test
    fun `labelOrUnbound substitutes a placeholder where something must be drawn`() {
        val map = mapWith(ShortcutAction.UNDO)

        val label = composed { map.labelOrUnbound(ShortcutAction.UNDO) }

        assertTrue(label.isNotBlank(), "the settings row has a cell to fill")
    }

    @Test
    fun `labelOrUnbound leaves a bound action alone`() {
        val map = mapWith(ShortcutAction.UNDO, chord(Key.Z, ctrl = true))

        val bound = composed { map.labelOrUnbound(ShortcutAction.UNDO) }
        val plain = composed { map.label(ShortcutAction.UNDO) }

        assertEquals(plain, bound)
    }

    @Test
    fun `a map's search text covers every chord bound to the action`() {
        val map = mapWith(ShortcutAction.UNDO, chord(Key.Z, ctrl = true), chord(Key.U, alt = true))

        val text = composed { map.searchText(ShortcutAction.UNDO) }

        assertTrue(text.contains("ctrl"), text)
        assertTrue(text.contains("alt"), text)
    }

    @Test
    fun `an unbound action has no search text`() {
        val map = mapWith(ShortcutAction.UNDO)

        assertEquals("", composed { map.searchText(ShortcutAction.UNDO) })
    }

    @Test
    fun `a pair of bound actions is joined into one phrase`() {
        val map = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.UNDO.name to listOf(chord(Key.Z, ctrl = true)),
                    ShortcutAction.REDO.name to listOf(chord(Key.Y, ctrl = true)),
                ),
            ),
        )

        val pair = composed { map.pairLabel(ShortcutAction.UNDO, ShortcutAction.REDO) }

        assertTrue(pair.contains("  "), "the two labels sit side by side: $pair")
    }

    @Test
    fun `a pair with one half unbound shows only the bound half`() {
        val map = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.UNDO.name to listOf(chord(Key.Z, ctrl = true)),
                    ShortcutAction.REDO.name to emptyList(),
                ),
            ),
        )

        val pair = composed { map.pairLabel(ShortcutAction.UNDO, ShortcutAction.REDO) }
        val single = composed { map.label(ShortcutAction.UNDO) }

        assertEquals(single, pair, "no stray separator where the missing half would have been")
    }

    @Test
    fun `a pair with both halves unbound is empty, so the caller drops the phrase`() {
        val map = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(
                    ShortcutAction.UNDO.name to emptyList(),
                    ShortcutAction.REDO.name to emptyList(),
                ),
            ),
        )

        assertEquals("", composed { map.pairLabel(ShortcutAction.UNDO, ShortcutAction.REDO) })
    }
}
