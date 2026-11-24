package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class TabColorsTest {

    private val c = LightThemeColors

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
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.HOVER), hovered.layer)
        val pressed = c.tabColors(selected = false, state = ButtonState.Pressed)
        assertEquals(c.onSurface, pressed.content)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.PRESSED), pressed.layer)
        val focused = c.tabColors(selected = false, state = ButtonState.Focused)
        assertEquals(c.onSurfaceVariant, focused.content)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING), focused.ring)
    }
}
