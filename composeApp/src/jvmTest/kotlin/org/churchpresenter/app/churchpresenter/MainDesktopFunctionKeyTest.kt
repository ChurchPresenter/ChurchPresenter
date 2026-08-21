package org.churchpresenter.app.churchpresenter

import androidx.compose.ui.input.key.Key
import org.churchpresenter.app.churchpresenter.data.settings.KeyboardShortcutSettings
import org.churchpresenter.core.models.shortcuts.KeyChord
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.models.ShortcutScope
import org.churchpresenter.app.churchpresenter.tabs.Tabs
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.keyDown
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tab switching, resolved through the shortcut registry.
 *
 * These were assertions about `tabForFunctionKey`, a `when` over `Key`. That function is gone —
 * the same mapping is now `ShortcutAction.targetTab` looked up through a `ShortcutMap`, so the keys
 * can be rebound. The behaviour being pinned is unchanged: F6–F12 in tab order, and nothing else.
 */
class MainDesktopFunctionKeyTest {

    private fun tabFor(key: Key, map: ShortcutMap = ShortcutMap.DEFAULT): Tabs? =
        map.actionFor(keyDown(key), ShortcutScope.GLOBAL)?.targetTab

    @Test
    fun `each function key opens its own tab`() {
        assertEquals(Tabs.BIBLE, tabFor(Key.F6))
        assertEquals(Tabs.SONGS, tabFor(Key.F7))
        assertEquals(Tabs.PICTURES, tabFor(Key.F8))
        assertEquals(Tabs.PRESENTATION, tabFor(Key.F9))
        assertEquals(Tabs.MEDIA, tabFor(Key.F10))
        assertEquals(Tabs.LOWER_THIRD, tabFor(Key.F11))
        assertEquals(Tabs.ANNOUNCEMENTS, tabFor(Key.F12))
    }

    @Test
    fun `no two shortcuts land on the same tab`() {
        val mapped = listOf(Key.F6, Key.F7, Key.F8, Key.F9, Key.F10, Key.F11, Key.F12)
            .mapNotNull { tabFor(it) }

        assertEquals(mapped.size, mapped.toSet().size, "two keys opening one tab leaves a tab unreachable: $mapped")
    }

    @Test
    fun `keys the app uses for other things are not tab shortcuts`() {
        listOf(Key.Escape, Key.PageUp, Key.PageDown, Key.Z, Key.Enter, Key.Spacebar).forEach {
            assertNull(tabFor(it), "$it is handled elsewhere and must not also switch tabs")
        }
    }

    @Test
    fun `the function keys either side of the range are not claimed`() {
        assertNull(tabFor(Key.F5))
        assertNull(tabFor(Key.F1))
    }

    @Test
    fun `an ordinary letter is not a tab shortcut`() {
        assertNull(tabFor(Key.A))
        assertNull(tabFor(Key.D))
    }

    @Test
    fun `a rebound tab shortcut moves to the new key and leaves the old one dead`() {
        val remapped = ShortcutMap.from(
            KeyboardShortcutSettings(
                overrides = mapOf(ShortcutAction.SWITCH_TO_BIBLE.name to listOf(KeyChord.of(Key.F5)))
            )
        )

        assertEquals(Tabs.BIBLE, tabFor(Key.F5, remapped))
        assertNull(tabFor(Key.F6, remapped), "the shipped key must stop working once rebound")
    }

    @Test
    fun `an unbound tab shortcut stops responding entirely`() {
        val cleared = ShortcutMap.from(
            KeyboardShortcutSettings(overrides = mapOf(ShortcutAction.SWITCH_TO_SONGS.name to emptyList()))
        )

        assertNull(tabFor(Key.F7, cleared))
        assertEquals(Tabs.BIBLE, tabFor(Key.F6, cleared), "clearing one binding must not disturb the others")
    }
}
