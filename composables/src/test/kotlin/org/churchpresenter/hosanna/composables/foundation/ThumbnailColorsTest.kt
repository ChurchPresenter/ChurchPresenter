package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import org.churchpresenter.hosanna.HosannaStateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class ThumbnailColorsTest {

    private val c = LightThemeColors

    @Test
    fun `rest has a hairline, hover a firmer one and a scrim`() {
        val rest = c.thumbnailColors(selected = false, live = false, state = ButtonState.Rest)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), rest.edge)
        assertEquals(Color.Transparent, rest.scrim)
        assertNull(rest.border)
        assertNull(rest.ring)
        assertEquals(c.screen, rest.letterbox)
        assertEquals(c.onSurface, rest.caption)
        val hovered = c.thumbnailColors(selected = false, live = false, state = ButtonState.Hovered)
        assertEquals(c.faint, hovered.edge)
        assertEquals(c.screen.copy(alpha = HosannaStateAlpha.SCRIM), hovered.scrim)
        val pressed = c.thumbnailColors(selected = false, live = false, state = ButtonState.Pressed)
        assertEquals(c.screen.copy(alpha = HosannaStateAlpha.OVERLAY), pressed.scrim)
        val focused = c.thumbnailColors(selected = false, live = false, state = ButtonState.Focused)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING), focused.ring)
    }

    @Test
    fun `selected is blue, live is red and wins`() {
        val selected = c.thumbnailColors(selected = true, live = false, state = ButtonState.Rest)
        assertEquals(c.primary, selected.border)
        assertEquals(c.primary, selected.caption)
        val live = c.thumbnailColors(selected = true, live = true, state = ButtonState.Rest)
        assertEquals(c.live, live.border)
        assertEquals(c.onSurface, live.caption)
    }

    @Test
    fun `badges sit on the screen color in both themes`() {
        val rest = c.thumbnailColors(selected = false, live = false, state = ButtonState.Rest)
        assertEquals(c.screen.copy(alpha = HosannaStateAlpha.OVERLAY), rest.badge)
        assertEquals(c.onScreen, rest.onBadge)
    }
}
