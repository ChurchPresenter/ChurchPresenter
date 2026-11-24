package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.ui.geometry.Offset
import org.churchpresenter.hosanna.composables.testing.FrozenInteractionSource

internal fun frozenSource(state: ButtonState): MutableInteractionSource = when (state) {
    ButtonState.Hovered -> FrozenInteractionSource(HoverInteraction.Enter())
    ButtonState.Pressed -> FrozenInteractionSource(PressInteraction.Press(Offset.Zero))
    ButtonState.Focused -> FrozenInteractionSource(FocusInteraction.Focus())
    else -> MutableInteractionSource()
}
