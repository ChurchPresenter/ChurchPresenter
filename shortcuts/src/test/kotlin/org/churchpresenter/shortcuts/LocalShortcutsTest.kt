package org.churchpresenter.shortcuts

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.settings.KeyboardShortcutSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * How a tab gets at the bindings in force.
 *
 * Every tab reads `LocalShortcuts.current` rather than being handed a map, so that a binding the
 * operator changes in the settings dialog reaches all of them without threading a parameter through
 * the whole tree. The default matters as much as the override: a composable rendered outside the
 * app's provider — a preview, a screenshot harness, a settings page composed on its own — still has
 * to get a working map rather than null.
 */
@OptIn(ExperimentalTestApi::class)
class LocalShortcutsTest {

    private fun <T> composed(body: @androidx.compose.runtime.Composable () -> T): T {
        var out: T? = null
        runComposeUiTest {
            setContent { out = body() }
            waitForIdle()
        }
        @Suppress("UNCHECKED_CAST")
        return out as T
    }

    @Test
    fun `with nothing provided the defaults are in force`() {
        val map = composed { LocalShortcuts.current }

        assertNotNull(map)
        assertSame(ShortcutMap.DEFAULT, map, "a preview must get the shipped bindings, not null")
    }

    @Test
    fun `the default map has the actions bound that ship bound`() {
        val map = composed { LocalShortcuts.current }

        assertTrue(map.chordsFor(ShortcutAction.UNDO).isNotEmpty(), "Undo ships with a binding")
    }

    @Test
    fun `a provided map replaces the defaults for everything below it`() {
        val custom = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(ShortcutAction.UNDO.name to listOf(KeyChord.of(Key.J))),
            ),
        )

        val seen = composed<ShortcutMap> {
            var inner: ShortcutMap? = null
            CompositionLocalProvider(LocalShortcuts provides custom) { inner = LocalShortcuts.current }
            inner!!
        }

        assertSame(custom, seen)
        assertEquals(listOf(KeyChord.of(Key.J)), seen.chordsFor(ShortcutAction.UNDO))
    }

    @Test
    fun `an action the provided map leaves alone keeps its shipped binding`() {
        val custom = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(ShortcutAction.UNDO.name to listOf(KeyChord.of(Key.J))),
            ),
        )

        val redo = composed<List<KeyChord>> {
            var inner: List<KeyChord>? = null
            CompositionLocalProvider(LocalShortcuts provides custom) {
                inner = LocalShortcuts.current.chordsFor(ShortcutAction.REDO)
            }
            inner!!
        }

        // Overriding one action must not silently unbind every other one.
        assertEquals(ShortcutAction.REDO.defaults, redo)
    }

    @Test
    fun `an action with no chords at all reports an empty list rather than throwing`() {
        val custom = ShortcutMap.from(
            KeyboardShortcutSettings(overrides = mapOf(ShortcutAction.UNDO.name to emptyList())),
        )

        assertEquals(emptyList(), custom.chordsFor(ShortcutAction.UNDO))
    }

    // ── The string-resource accessors on the enums ──────────────────────────────

    @Test
    fun `every scope names a title and a hint`() {
        ShortcutScope.entries.forEach {
            assertNotNull(it.titleRes, "${it.name} has no title")
            assertNotNull(it.hintRes, "${it.name} has no hint")
        }
    }

    @Test
    fun `every action names a description`() {
        // The shortcuts dialog lists all of them; one without a description would render blank.
        ShortcutAction.entries.forEach {
            assertNotNull(it.descriptionRes, "${it.name} has no description")
        }
    }
}
