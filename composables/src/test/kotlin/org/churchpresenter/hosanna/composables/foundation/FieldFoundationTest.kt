package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.churchpresenter.hosanna.composables.field.counterText
import org.churchpresenter.hosanna.composables.field.isOverLimit
import org.churchpresenter.hosanna.composables.field.showClear
import org.churchpresenter.hosanna.composables.testing.LightThemeColors
import org.churchpresenter.theme.HosannaStateAlpha

class FieldFoundationTest {

    private val c = LightThemeColors

    @Test
    fun `disabled outranks read-only, which outranks focus and hover`() {
        assertEquals(FieldState.Disabled, fieldState(enabled = false, readOnly = true, hovered = true, focused = true))
        assertEquals(FieldState.ReadOnly, fieldState(enabled = true, readOnly = true, hovered = true, focused = true))
        assertEquals(FieldState.Focused, fieldState(enabled = true, readOnly = false, hovered = true, focused = true))
        assertEquals(FieldState.Hovered, fieldState(enabled = true, readOnly = false, hovered = true, focused = false))
        assertEquals(FieldState.Rest, fieldState(enabled = true, readOnly = false, hovered = false, focused = false))
    }

    @Test
    fun `rest, hover and focus come from the theme roles`() {
        assertEquals(
            FieldColors(
                c.surfaceVariant,
                c.outline,
                null,
                c.onSurfaceVariant,
                c.onSurface,
                c.faint,
                c.primary,
            ),
            c.fieldColors(FieldState.Rest, error = false),
        )
        val hover = c.onSurface.copy(alpha = HosannaStateAlpha.HOVER)
        assertEquals(
            FieldColors(
                hover.compositeOver(c.surfaceVariant),
                hover.compositeOver(c.outline),
                null,
                c.onSurfaceVariant,
                c.onSurface,
                c.faint,
                c.primary,
            ),
            c.fieldColors(FieldState.Hovered, error = false),
        )
        assertEquals(
            FieldColors(
                c.surfaceContainerLowest,
                c.primary,
                c.primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
                c.primary,
                c.onSurface,
                c.faint,
                c.primary,
            ),
            c.fieldColors(FieldState.Focused, error = false),
        )
    }

    @Test
    fun `an error turns the edge, label, ring and caret red`() {
        val rest = c.fieldColors(FieldState.Rest, error = true)
        assertEquals(c.error, rest.border)
        assertEquals(c.error, rest.label)
        assertEquals(c.error, rest.caret)
        assertEquals(c.error, c.fieldColors(FieldState.Hovered, error = true).border)
        val focused = c.fieldColors(FieldState.Focused, error = true)
        assertEquals(c.error, focused.border)
        assertEquals(c.error.copy(alpha = HosannaStateAlpha.FOCUS_RING), focused.ring)
    }

    @Test
    fun `read-only has no fill and no caret, disabled fades everything`() {
        assertEquals(
            FieldColors(
                Color.Transparent,
                c.outline,
                null,
                c.onSurfaceVariant,
                c.onSurface,
                c.faint,
                Color.Transparent,
            ),
            c.fieldColors(FieldState.ReadOnly, error = true),
        )
        val ink = c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
        assertEquals(
            FieldColors(
                c.surfaceVariant,
                c.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER),
                null,
                ink,
                ink,
                ink,
                Color.Transparent,
            ),
            c.fieldColors(FieldState.Disabled, error = true),
        )
    }

    @Test
    fun `the counter and limit`() {
        assertEquals("3 / 10", counterText("abc", 10))
        assertFalse(isOverLimit("abc", null))
        assertFalse(isOverLimit("abc", 3))
        assertTrue(isOverLimit("abcd", 3))
    }

    @Test
    fun `the clear button needs text, an editable field and hover or focus`() {
        assertTrue(showClear(clearable = true, value = "a", editable = true, active = true))
        assertFalse(showClear(clearable = false, value = "a", editable = true, active = true))
        assertFalse(showClear(clearable = true, value = "", editable = true, active = true))
        assertFalse(showClear(clearable = true, value = "a", editable = false, active = true))
        assertFalse(showClear(clearable = true, value = "a", editable = true, active = false))
    }
}
