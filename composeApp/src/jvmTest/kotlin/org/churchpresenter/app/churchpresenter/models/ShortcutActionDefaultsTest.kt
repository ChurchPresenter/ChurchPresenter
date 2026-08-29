package org.churchpresenter.app.churchpresenter.models

import androidx.compose.ui.input.key.Key
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.churchpresenter.core.models.shortcuts.KeyChord

/**
 * Guards the shipped bindings.
 *
 * Before the registry existed each binding was a `Key.X` literal at its handler, so a default that
 * collided with another was invisible until someone pressed the key. These assertions make a bad
 * default fail the build instead.
 */
class ShortcutActionDefaultsTest {

    @Test
    fun `no two defaults collide within overlapping scopes`() {
        val clashes = ShortcutAction.entries.flatMap { action ->
            action.defaults.mapNotNull { chord ->
                ShortcutAction.entries.firstOrNull { other ->
                    other != action &&
                        other.ordinal > action.ordinal &&      // report each pair once
                        other.scope.overlaps(action.scope) &&
                        chord in other.defaults
                }?.let { "$action and $it both default to $chord" }
            }
        }

        assertEquals(emptyList(), clashes)
    }

    @Test
    fun `every action except Save As and the tenth quick background ships with a binding`() {
        // Two deliberately unbound actions. Save As has no accelerator in the app today and exists
        // here so it can be given one. The tenth quick-background slot has no key left to take:
        // one to nine are spent on the first nine slots and Ctrl+0 on the reset, so it ships
        // unbound rather than claiming a two-handed chord nobody asked for.
        val unbound = ShortcutAction.entries.filter { it.defaults.isEmpty() }

        assertEquals(
            listOf(ShortcutAction.SAVE_SCHEDULE_AS, ShortcutAction.QUICK_BACKGROUND_10),
            unbound,
        )
    }

    @Test
    fun `exactly the tab-switching actions carry a target tab`() {
        val withTab = ShortcutAction.entries.filter { it.targetTab != null }

        assertEquals(
            listOf(
                Tabs.BIBLE, Tabs.SONGS, Tabs.PICTURES, Tabs.PRESENTATION,
                Tabs.MEDIA, Tabs.LOWER_THIRD, Tabs.ANNOUNCEMENTS,
            ),
            withTab.map { it.targetTab }
        )
        assertTrue(withTab.all { it.scope == ShortcutScope.GLOBAL })
    }

    @Test
    fun `the shipped bindings are the ones the app had before they became configurable`() {
        // Spot-check across every scope, so a careless edit to the table is caught rather than
        // silently changing what the app responds to.
        assertEquals(listOf(KeyChord.of(Key.Z, ctrl = true)), ShortcutAction.UNDO.defaults)
        assertEquals(listOf(KeyChord.of(Key.Z, ctrl = true, shift = true)), ShortcutAction.REDO.defaults)
        assertEquals(listOf(KeyChord.of(Key.Escape)), ShortcutAction.CLEAR_OUTPUT.defaults)
        assertEquals(listOf(KeyChord.of(Key.S, ctrl = true)), ShortcutAction.SAVE_SCHEDULE.defaults)
        assertEquals(listOf(KeyChord.of(Key.F6)), ShortcutAction.SWITCH_TO_BIBLE.defaults)
        assertEquals(listOf(KeyChord.of(Key.DirectionUp)), ShortcutAction.BIBLE_PREVIOUS_VERSE.defaults)
        assertEquals(listOf(KeyChord.of(Key.Spacebar)), ShortcutAction.MEDIA_PLAY_PAUSE.defaults)
        assertEquals(listOf(KeyChord.of(Key.M)), ShortcutAction.MEDIA_MUTE.defaults)
        assertEquals(
            listOf(KeyChord.of(Key.DirectionRight), KeyChord.of(Key.DirectionDown)),
            ShortcutAction.PRESENTATION_NEXT.defaults
        )
        assertEquals(
            listOf(KeyChord.of(Key.B), KeyChord.of(Key.Period)),
            ShortcutAction.PRESENTATION_BLANK.defaults
        )
        assertEquals(
            listOf(KeyChord.of(Key.Delete), KeyChord.of(Key.Backspace)),
            ShortcutAction.CANVAS_DELETE_SOURCE.defaults
        )
    }

    @Test
    fun `scope overlap is symmetric and global overlaps everything`() {
        ShortcutScope.entries.forEach { a ->
            ShortcutScope.entries.forEach { b ->
                assertEquals(a.overlaps(b), b.overlaps(a), "overlap of $a and $b must be symmetric")
            }
            assertTrue(ShortcutScope.GLOBAL.overlaps(a))
            assertTrue(a.overlaps(a))
        }
        // Two different tab scopes never compete.
        assertTrue(!ShortcutScope.MEDIA.overlaps(ShortcutScope.PICTURES))
        assertTrue(!ShortcutScope.MENU.overlaps(ShortcutScope.CANVAS))
    }
}
