package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.state.ToggleableState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.churchpresenter.hosanna.composables.testing.LightThemeColors

class CheckboxColorsTest {

    private val c = LightThemeColors
    private val off = ToggleableState.Off
    private val on = ToggleableState.On
    private val mixed = ToggleableState.Indeterminate

    @Test
    fun `an unchecked box is the lowest surface with a faint edge`() {
        val rest = c.checkboxColors(off, ButtonState.Rest, error = false)
        assertEquals(c.surfaceContainerLowest, rest.fill)
        assertEquals(c.faint, rest.edge)
        assertEquals(Color.Transparent, rest.halo)
        assertEquals(c.onSurface, rest.label)
        assertEquals(c.onSurfaceVariant, rest.description)
        assertEquals(c.onSurfaceVariant, c.checkboxColors(off, ButtonState.Hovered, error = false).edge)
    }

    @Test
    fun `checked and mixed are filled with primary and lift on hover and press`() {
        listOf(on, mixed).forEach { value ->
            val rest = c.checkboxColors(value, ButtonState.Rest, error = false)
            assertEquals(c.primary, rest.fill)
            assertEquals(Color.Transparent, rest.edge)
            assertEquals(c.onPrimary, rest.mark)
            assertEquals(
                c.onPrimary.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(c.primary),
                c.checkboxColors(value, ButtonState.Hovered, error = false).fill,
            )
            assertEquals(
                c.onPrimary.copy(alpha = HosannaStateAlpha.PRESSED).compositeOver(c.primary),
                c.checkboxColors(value, ButtonState.Pressed, error = false).fill,
            )
        }
    }

    @Test
    fun `hover and press show the halo, focus shows the ring`() {
        val hovered = c.checkboxColors(off, ButtonState.Hovered, false)
        val pressed = c.checkboxColors(on, ButtonState.Pressed, false)
        val focused = c.checkboxColors(off, ButtonState.Focused, false)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.HOVER), hovered.halo)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.PRESSED), pressed.halo)
        assertEquals(c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING), focused.ring)
        assertNull(c.checkboxColors(off, ButtonState.Rest, false).ring)
    }

    @Test
    fun `an error edges an unchecked box in red`() {
        assertEquals(c.error, c.checkboxColors(off, ButtonState.Rest, error = true).edge)
        assertEquals(c.error, c.checkboxColors(off, ButtonState.Hovered, error = true).edge)
        assertEquals(Color.Transparent, c.checkboxColors(on, ButtonState.Rest, error = true).edge)
    }

    @Test
    fun `disabled fades everything but still shows a check`() {
        val ink = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        val checked = c.checkboxColors(on, ButtonState.Disabled, error = true)
        assertEquals(ink, checked.fill)
        assertEquals(c.surfaceContainerLowest, checked.mark)
        assertEquals(ink, checked.label)
        assertEquals(Color.Transparent, checked.halo)
        assertNull(checked.ring)
        val unchecked = c.checkboxColors(off, ButtonState.Disabled, error = false)
        assertEquals(c.surfaceContainerLowest, unchecked.fill)
        assertEquals(c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER), unchecked.edge)
    }
}
