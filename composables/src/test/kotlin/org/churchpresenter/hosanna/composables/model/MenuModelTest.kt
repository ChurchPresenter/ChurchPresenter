package org.churchpresenter.hosanna.composables.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.HosannaLightColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.composables.field.labelOf

class MenuModelTest {

    private val entries = listOf(
        MenuEntry.Header("Time"),
        MenuEntry.Item("new", "Newest First"),
        MenuEntry.Item("old", "Oldest First", enabled = false),
        MenuEntry.Divider,
        MenuEntry.Item("most", "Most Votes"),
        MenuEntry.Item("least", "Least Votes"),
    )

    @Test
    fun `moving skips titles, dividers and disabled rows and stops at the ends`() {
        assertEquals(1, nextPickable(entries, -1, 1))
        assertEquals(4, nextPickable(entries, 1, 1))
        assertEquals(1, nextPickable(entries, 4, -1))
        assertEquals(5, nextPickable(entries, 5, 1))
        assertEquals(1, nextPickable(entries, 1, -1))
    }

    @Test
    fun `typing jumps to the first enabled row starting with that letter`() {
        assertEquals(4, firstMatch(entries, 'm'))
        assertEquals(1, firstMatch(entries, 'N'))
        assertEquals(-1, firstMatch(entries, 'o'))
        assertEquals(-1, firstMatch(entries, 'z'))
    }

    @Test
    fun `search keeps matching rows only, a blank query keeps everything`() {
        assertEquals(entries, filterEntries(entries, "  "))
        assertEquals(
            listOf(MenuEntry.Item("most", "Most Votes"), MenuEntry.Item("least", "Least Votes")),
            filterEntries(entries, "votes"),
        )
        assertEquals(emptyList(), filterEntries(entries, "zzz"))
        assertEquals(4, itemCount(entries))
    }

    @Test
    fun `the field shows the chosen row's label`() {
        assertEquals("Most Votes", labelOf(entries, "most"))
        assertNull(labelOf(entries, "missing"))
        assertNull(labelOf(entries, null))
    }

    @Test
    fun `a row is tinted when selected and lifted when highlighted`() {
        val c = HosannaLightColors
        val hover = c.onSurface.copy(alpha = HosannaStateAlpha.HOVER)
        assertEquals(Color.Transparent, c.menuRowContainer(selected = false, highlighted = false))
        assertEquals(c.primaryContainer, c.menuRowContainer(selected = true, highlighted = false))
        assertEquals(
            hover.compositeOver(Color.Transparent),
            c.menuRowContainer(selected = false, highlighted = true),
        )
        assertEquals(
            hover.compositeOver(c.primaryContainer),
            c.menuRowContainer(selected = true, highlighted = true),
        )
    }

    @Test
    fun `the menu opens below the field, or above it when there is no room`() {
        val provider = MenuPositionProvider(gap = 6)
        val window = IntSize(800, 600)
        val field = IntRect(100, 100, 360, 144)
        assertEquals(
            IntOffset(100, 150),
            provider.calculatePosition(field, window, LayoutDirection.Ltr, IntSize(260, 200)),
        )
        val low = IntRect(100, 500, 360, 544)
        assertEquals(
            IntOffset(100, 294),
            provider.calculatePosition(low, window, LayoutDirection.Ltr, IntSize(260, 200)),
        )
        val nearRight = IntRect(700, 100, 790, 144)
        assertEquals(
            IntOffset(540, 150),
            provider.calculatePosition(nearRight, window, LayoutDirection.Ltr, IntSize(260, 200)),
        )
        val tall = IntRect(100, 300, 360, 344)
        assertEquals(
            IntOffset(100, 350),
            provider.calculatePosition(tall, window, LayoutDirection.Ltr, IntSize(260, 500)),
        )
    }
}
