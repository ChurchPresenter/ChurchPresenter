package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import org.churchpresenter.hosanna.HosannaLightColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ListRowColorsTest {

    private val c = HosannaLightColors

    @Test
    fun `rest rows are clear, hover and press tint them`() {
        val rest = c.listRowColors(selected = false, live = false, marker = false, state = ButtonState.Rest)
        assertEquals(ListRowColors(Color.Transparent, c.onSurface, c.onSurfaceVariant, null, null), rest)
        assertEquals(
            c.onSurface.copy(alpha = HosannaStateAlpha.HOVER),
            c.listRowColors(false, false, false, ButtonState.Hovered).container,
        )
        assertEquals(
            c.onSurface.copy(alpha = HosannaStateAlpha.PRESSED),
            c.listRowColors(false, false, false, ButtonState.Pressed).container,
        )
        assertEquals(c.primary, c.listRowColors(false, false, false, ButtonState.Focused).ring)
    }

    @Test
    fun `selected rows are light blue, navigation lists add the bar`() {
        val selected = c.listRowColors(selected = true, live = false, marker = false, state = ButtonState.Rest)
        assertEquals(c.primaryContainer, selected.container)
        assertEquals(c.onPrimaryContainer, selected.content)
        assertEquals(c.onPrimaryContainer, selected.supporting)
        assertNull(selected.bar)
        assertEquals(c.primary, c.listRowColors(true, false, marker = true, state = ButtonState.Rest).bar)
        assertEquals(
            c.onSurface.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(c.primaryContainer),
            c.listRowColors(true, false, false, ButtonState.Hovered).container,
        )
    }

    @Test
    fun `live wins the bar and disabled fades everything`() {
        assertEquals(c.live, c.listRowColors(true, live = true, marker = true, state = ButtonState.Rest).bar)
        val ink = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        assertEquals(
            ListRowColors(Color.Transparent, ink, ink, null, null),
            c.listRowColors(true, true, true, ButtonState.Disabled),
        )
    }
}
