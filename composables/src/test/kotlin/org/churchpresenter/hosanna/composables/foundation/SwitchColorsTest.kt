package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.churchpresenter.theme.themeColorsFor
import org.churchpresenter.theme.ThemeMode
import org.churchpresenter.theme.StateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SwitchColorsTest {

    private val c = themeColorsFor(ThemeMode.LIGHT)

    @Test
    fun `off is a quiet track with an edge and a gray knob`() {
        val rest = c.switchColors(checked = false, state = ButtonState.Rest)
        assertEquals(c.surfaceContainerHighest, rest.track)
        assertEquals(c.onSurfaceFaint, rest.edge)
        assertEquals(c.onSurfaceFaint, rest.knob)
        assertEquals(c.onSurface, rest.label)
        assertEquals(c.onSurfaceVariant, rest.description)
        val hovered = c.switchColors(checked = false, state = ButtonState.Hovered)
        assertEquals(c.onSurfaceVariant, hovered.edge)
        assertEquals(c.onSurfaceVariant, hovered.knob)
    }

    @Test
    fun `on is a primary track that lifts on hover and press`() {
        val rest = c.switchColors(checked = true, state = ButtonState.Rest)
        assertEquals(c.primary, rest.track)
        assertEquals(Color.Transparent, rest.edge)
        assertEquals(c.onPrimary, rest.knob)
        assertEquals(c.primary, rest.icon)
        assertEquals(
            c.onPrimary.copy(alpha = StateAlpha.HOVER).compositeOver(c.primary),
            c.switchColors(checked = true, state = ButtonState.Hovered).track,
        )
        assertEquals(
            c.onPrimary.copy(alpha = StateAlpha.PRESSED).compositeOver(c.primary),
            c.switchColors(checked = true, state = ButtonState.Pressed).track,
        )
    }

    @Test
    fun `focus shows the ring, nothing else does`() {
        assertEquals(
            c.primary.copy(alpha = StateAlpha.FOCUS_RING),
            c.switchColors(checked = false, state = ButtonState.Focused).ring,
        )
        assertNull(c.switchColors(checked = true, state = ButtonState.Hovered).ring)
    }

    @Test
    fun `loading off shows a darker knob with a light spinner`() {
        val loading = c.switchColors(checked = false, state = ButtonState.Rest, loading = true)
        assertEquals(c.onSurfaceVariant, loading.knob)
        assertEquals(c.surfaceContainerLowest, loading.icon)
    }

    @Test
    fun `disabled fades the track and label but keeps the on knob`() {
        val ink = c.onSurface.copy(alpha = StateAlpha.DISABLED_CONTENT)
        val on = c.switchColors(checked = true, state = ButtonState.Disabled)
        assertEquals(ink, on.track)
        assertEquals(c.surfaceContainerLowest, on.knob)
        assertEquals(ink, on.label)
        assertNull(on.ring)
        val off = c.switchColors(checked = false, state = ButtonState.Disabled)
        assertEquals(c.surfaceContainerLowest, off.track)
        assertEquals(c.onSurface.copy(alpha = StateAlpha.DISABLED_CONTAINER), off.edge)
        assertEquals(ink, off.knob)
    }
}
