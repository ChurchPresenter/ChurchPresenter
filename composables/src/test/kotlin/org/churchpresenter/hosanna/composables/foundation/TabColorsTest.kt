package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.themeColorsFor
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.StateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabColorsTest {

    private val c = themeColorsFor(ThemeMode.LIGHT)

    @Test
    fun `an idle tab is quiet, a selected one is ink with the indicator`() {
        val idle = c.tabColors(selected = false, state = ButtonState.Rest)
        assertEquals(c.onSurfaceVariant, idle.content)
        assertEquals(Color.Transparent, idle.indicator)
        assertEquals(Color.Transparent, idle.layer)
        assertNull(idle.ring)
        val selected = c.tabColors(selected = true, state = ButtonState.Rest)
        assertEquals(c.onSurface, selected.content)
        assertEquals(c.primary, selected.indicator)
        assertEquals(c.primary, selected.badge)
        assertEquals(c.onPrimary, selected.onBadge)
    }

    @Test
    fun `hover and press darken the label and show the pill, focus the ring`() {
        val hovered = c.tabColors(selected = false, state = ButtonState.Hovered)
        assertEquals(c.onSurface, hovered.content)
        assertEquals(c.onSurface.copy(alpha = StateAlpha.HOVER), hovered.layer)
        val pressed = c.tabColors(selected = false, state = ButtonState.Pressed)
        assertEquals(c.onSurface, pressed.content)
        assertEquals(c.onSurface.copy(alpha = StateAlpha.PRESSED), pressed.layer)
        val focused = c.tabColors(selected = false, state = ButtonState.Focused)
        assertEquals(c.onSurfaceVariant, focused.content)
        assertEquals(c.primary.copy(alpha = StateAlpha.FOCUS_RING), focused.ring)
    }
}
