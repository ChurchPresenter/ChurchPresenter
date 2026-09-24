package org.churchpresenter.theme.components

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role

/**
 * Makes a row holding a label and its switch or checkbox one toggle, so a click on the label flips
 * it. Give the control `onCheckedChange = null` and the same [interaction], so hovering the label
 * lights the control and the row publishes a single click target.
 */
fun Modifier.toggleRow(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    interaction: MutableInteractionSource,
    role: Role = Role.Switch,
    enabled: Boolean = true,
): Modifier = this
    .hoverable(interaction, enabled = enabled)
    .toggleable(
        value = checked,
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        role = role,
        onValueChange = onCheckedChange,
    )
