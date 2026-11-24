package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * The frame [TextField] draws — its container, edge, hover and focus ring — for a text field the
 * caller builds itself, such as an editor with its own highlighting or paste handling. Pass the
 * same [interactionSource] to the `BasicTextField` inside so the frame follows its focus.
 *
 * @param interactionSource the field's interaction source, shared with the `BasicTextField`.
 * @param enabled whether the field can be used; a disabled one is drawn faded.
 */
@Composable
fun Modifier.textFieldFrame(interactionSource: MutableInteractionSource, enabled: Boolean = true): Modifier {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val colors = ThemeColors.current.fieldColors(fieldState(enabled, false, hovered, focused), error = false)
    val spec = tween<Color>(FieldDefaults.TRANSITION_MILLIS, easing = EaseOut)
    val container by animateColorAsState(colors.container, spec)
    val border by animateColorAsState(colors.border, spec)
    val shape = HosannaTheme.shapes.m
    return this
        .hoverable(interactionSource, enabled)
        .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
        .background(container, shape)
        .border(FieldDefaults.Border, border, shape)
}
