package org.churchpresenter.core.models.shortcuts

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyShortcut
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.utils.keyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KeyChordTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Test
    fun `round-trips through json`() {
        val chord = KeyChord.of(Key.Z, ctrl = true, shift = true)

        assertEquals(chord, json.decodeFromString<KeyChord>(json.encodeToString(chord)))
    }

    @Test
    fun `decodes from a bare key code so an old settings file still loads`() {
        val decoded = json.decodeFromString<KeyChord>("""{"keyCode":${Key.F6.keyCode}}""")

        assertEquals(KeyChord.of(Key.F6), decoded)
        assertFalse(decoded.ctrl || decoded.shift || decoded.alt || decoded.meta)
    }

    @Test
    fun `matches its own key with no modifiers`() {
        assertTrue(KeyChord.of(Key.Spacebar).matches(keyDown(Key.Spacebar)))
    }

    @Test
    fun `does not match a different key`() {
        assertFalse(KeyChord.of(Key.Spacebar).matches(keyDown(Key.Enter)))
    }

    @Test
    fun `modifiers must match exactly, so Ctrl+S does not fire on Ctrl+Shift+S`() {
        val ctrlS = KeyChord.of(Key.S, ctrl = true)

        assertTrue(ctrlS.matches(keyDown(Key.S, ctrl = true)))
        assertFalse(ctrlS.matches(keyDown(Key.S, ctrl = true, shift = true)))
        assertFalse(ctrlS.matches(keyDown(Key.S)))
    }

    @Test
    fun `each modifier is distinguished from the others`() {
        val altA = KeyChord.of(Key.A, alt = true)

        assertTrue(altA.matches(keyDown(Key.A, alt = true)))
        assertFalse(altA.matches(keyDown(Key.A, ctrl = true)))
        assertFalse(altA.matches(keyDown(Key.A, meta = true)))
        assertFalse(altA.matches(keyDown(Key.A, shift = true)))
    }

    @Test
    fun `a meta chord does not fire on the bare key`() {
        // The only comparison in matches() that every other case short-circuits before reaching:
        // key, ctrl, shift and alt all agree here, and meta alone decides it. On macOS that is
        // Cmd+S having to not fire on a plain S.
        val metaS = KeyChord.of(Key.S, meta = true)

        assertTrue(metaS.matches(keyDown(Key.S, meta = true)))
        assertFalse(metaS.matches(keyDown(Key.S)))
    }

    @Test
    fun `of reads every modifier off the event`() {
        val chord = KeyChord.of(keyDown(Key.K, ctrl = true, shift = true, alt = true, meta = true))

        assertEquals(KeyChord.of(Key.K, ctrl = true, shift = true, alt = true, meta = true), chord)
    }

    @Test
    fun `converts to a menu accelerator carrying the same key and modifiers`() {
        val shortcut = KeyChord.of(Key.N, ctrl = true, shift = true).toKeyShortcut()

        // KeyShortcut has no public getters, so compare against an independently built one.
        assertEquals(
            KeyShortcut(key = Key.N, ctrl = true, shift = true),
            shortcut
        )
    }

    @Test
    fun `every modifier key is listed as unbindable on its own`() {
        // A bare modifier press must never become a binding — the capture dialog relies on this
        // set to ignore the key-down that arrives when the user starts holding Ctrl.
        listOf(Key.CtrlLeft, Key.ShiftRight, Key.AltLeft, Key.MetaRight).forEach {
            assertTrue(it in KeyChord.MODIFIER_KEYS, "$it should be treated as a modifier")
        }
        assertFalse(Key.A in KeyChord.MODIFIER_KEYS)
    }
}
