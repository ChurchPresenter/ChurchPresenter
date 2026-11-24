package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import org.churchpresenter.hosanna.HosannaStateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class SliderColorsTest {

    private val c = LightThemeColors

    @Test
    fun `rest is a primary fill on a quiet track`() {
        val rest = c.sliderColors(ButtonState.Rest)
        assertEquals(c.outlineVariant, rest.track)
        assertEquals(c.primary, rest.fill)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.SECONDARY_FILL), rest.buffered)
        assertEquals(c.onPrimary, rest.thumb)
        assertEquals(c.primary, rest.thumbRing)
        assertEquals(Color.Transparent, rest.halo)
        assertEquals(c.inverseSurface, rest.bubble)
        assertEquals(c.inverseOnSurface, rest.bubbleInk)
        assertEquals(c.onSurfaceVariant, rest.value)
        assertNull(rest.ring)
    }

    @Test
    fun `hover and drag show a halo, focus a ring`() {
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.HOVER), c.sliderColors(ButtonState.Hovered).halo)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.DRAGGED), c.sliderColors(ButtonState.Pressed).halo)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING), c.sliderColors(ButtonState.Focused).ring)
    }

    @Test
    fun `disabled is gray with no halo or ring`() {
        val ink = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        val disabled = c.sliderColors(ButtonState.Disabled)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), disabled.track)
        assertEquals(ink, disabled.fill)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), disabled.buffered)
        assertEquals(c.surfaceContainerHigh, disabled.thumb)
        assertEquals(ink, disabled.value)
        assertEquals(Color.Transparent, disabled.halo)
        assertNull(disabled.ring)
    }
}
