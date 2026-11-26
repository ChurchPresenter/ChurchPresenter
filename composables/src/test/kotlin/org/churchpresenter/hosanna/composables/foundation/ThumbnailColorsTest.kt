package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import org.churchpresenter.theme.themeColorsFor
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.StateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThumbnailColorsTest {

    private val c = themeColorsFor(ThemeMode.LIGHT)

    @Test
    fun `rest has a hairline, hover a firmer one and a scrim`() {
        val rest = c.thumbnailColors(selected = false, live = false, state = ButtonState.Rest)
        assertEquals(c.onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER), rest.edge)
        assertEquals(Color.Transparent, rest.scrim)
        assertNull(rest.border)
        assertNull(rest.ring)
        assertEquals(c.screen, rest.letterbox)
        assertEquals(c.onSurface, rest.caption)
        val hovered = c.thumbnailColors(selected = false, live = false, state = ButtonState.Hovered)
        assertEquals(c.onSurfaceFaint, hovered.edge)
        assertEquals(c.screen.copy(alpha = StateAlpha.SCRIM), hovered.scrim)
        val pressed = c.thumbnailColors(selected = false, live = false, state = ButtonState.Pressed)
        assertEquals(c.screen.copy(alpha = StateAlpha.OVERLAY), pressed.scrim)
        val focused = c.thumbnailColors(selected = false, live = false, state = ButtonState.Focused)
        assertEquals(c.primary.copy(alpha = StateAlpha.FOCUS_RING), focused.ring)
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
        assertEquals(c.screen.copy(alpha = StateAlpha.OVERLAY), rest.badge)
        assertEquals(c.onScreen, rest.onBadge)
    }
}
