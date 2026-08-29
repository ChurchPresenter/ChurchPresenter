package org.churchpresenter.app.churchpresenter.composables

import androidx.compose.ui.input.key.Key
import org.churchpresenter.app.churchpresenter.models.ShortcutAction
import org.churchpresenter.app.churchpresenter.utils.ShortcutMap
import org.churchpresenter.app.churchpresenter.utils.keyDown
import org.churchpresenter.core.models.songs.SongBackgroundType
import org.churchpresenter.settings.QuickBackground
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The tray's slot arithmetic, and what a new tile starts life as. */
class QuickBackgroundsTest {

    private val shortcuts = ShortcutMap.DEFAULT

    @Test
    fun `each digit reaches the slot it is printed on`() {
        (1..9).forEach { slot ->
            assertEquals(
                slot,
                quickBackgroundSlotFor(shortcuts, keyDown(digitKey(slot), ctrl = true)),
                "Ctrl+${'$'}slot must reach slot ${'$'}slot",
            )
        }
    }

    @Test
    fun `a key bound to nothing reaches no slot`() {
        assertNull(quickBackgroundSlotFor(shortcuts, keyDown(Key.Q, ctrl = true)))
    }

    @Test
    fun `the reset chord is not one of the slots`() {
        assertNull(
            quickBackgroundSlotFor(shortcuts, keyDown(Key.Zero, ctrl = true)),
            "Ctrl+0 puts the configured backgrounds back rather than picking a tile",
        )
    }

    @Test
    fun `every slot the tray holds has an action`() {
        (1..QUICK_BACKGROUND_SLOTS).forEach { slot ->
            assertTrue(quickBackgroundActionFor(slot) != null, "slot $slot must be bindable")
        }
    }

    @Test
    fun `there is no action past the last slot`() {
        assertNull(quickBackgroundActionFor(QUICK_BACKGROUND_SLOTS + 1))
        assertNull(quickBackgroundActionFor(0), "slots are numbered from one")
    }

    @Test
    fun `the tenth slot ships unbound`() {
        assertEquals(
            emptyList(),
            ShortcutAction.QUICK_BACKGROUND_10.defaults,
            "the digits are spent, so whoever wants a tenth chooses their own chord",
        )
    }

    @Test
    fun `a new tray entry overrides rather than inherits`() {
        val added = newQuickBackground()
        assertEquals(SongBackgroundType.COLOR, added.background.type)
        assertNotEquals(
            SongBackgroundType.INHERIT,
            added.background.type,
            "a tile that inherited would do nothing when pressed",
        )
        assertEquals(added.background.type, added.lowerThirdBackground.type, "both halves are set")
        assertTrue(added.id.isNotBlank(), "the tray addresses its entries by id")
    }

    @Test
    fun `two new entries do not share an id`() {
        assertNotEquals(newQuickBackground().id, newQuickBackground().id)
    }

    @Test
    fun `an entry with no label falls back to its background's own name`() {
        val added = newQuickBackground()
        assertEquals("", added.label, "a tray tile carries no label of its own")
        assertEquals(QuickBackground().label, added.label)
    }

    private fun digitKey(digit: Int): Key = when (digit) {
        1 -> Key.One
        2 -> Key.Two
        3 -> Key.Three
        4 -> Key.Four
        5 -> Key.Five
        6 -> Key.Six
        7 -> Key.Seven
        8 -> Key.Eight
        else -> Key.Nine
    }
}
