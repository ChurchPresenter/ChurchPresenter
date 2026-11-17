package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.keyboardFocusRing

/** One row's eye: what it toggles, and whether this row may be hidden (not the last one shown). */
internal class OrderRowEye(val shown: OrderShown, val canHide: Boolean)

/**
 * The eye on one row of a Display order panel: open while [entry] is shown, struck through while it
 * is hidden. Disabled on the one entry still shown, so the last cannot be hidden.
 */
@Composable
internal fun OrderShownToggle(entry: OrderEntry, eye: OrderRowEye) {
    val enabled = !entry.shown || eye.canHide
    val shape = AppShape(6.dp)
    val tint = if (entry.shown) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = Modifier
            .keyboardFocusRing(shape)
            .size(26.dp)
            .background(
                if (entry.shown) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                else MaterialTheme.colorScheme.surfaceVariant,
                shape,
            )
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .toggleable(
                value = entry.shown,
                enabled = enabled,
                role = Role.Checkbox,
                onValueChange = { eye.shown.onToggle(entry.key, it) },
            )
            .semantics { contentDescription = "${eye.shown.label}: ${entry.shownName}" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (entry.shown) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = tint.copy(alpha = if (enabled) 1f else 0.45f),
        )
    }
}
