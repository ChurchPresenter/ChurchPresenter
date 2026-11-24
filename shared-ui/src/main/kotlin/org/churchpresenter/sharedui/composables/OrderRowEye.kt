package org.churchpresenter.sharedui.composables

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import org.churchpresenter.hosanna.composables.button.IconToggleButton
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

/** One row's eye: what it toggles, and whether this row may be hidden (not the last one shown). */
internal class OrderRowEye(val shown: OrderShown, val canHide: Boolean)

/**
 * The eye on one row of a Display order panel: open while [entry] is shown, struck through while it
 * is hidden. Disabled on the one entry still shown, so the last cannot be hidden.
 */
@Composable
internal fun OrderShownToggle(entry: OrderEntry, eye: OrderRowEye) {
    IconToggleButton(
        icon = rememberVectorPainter(if (entry.shown) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff),
        contentDescription = "${eye.shown.label}: ${entry.shownName}",
        checked = entry.shown,
        onCheckedChange = { eye.shown.onToggle(entry.key, it) },
        size = IconButtonSize.Small,
        hierarchy = ButtonHierarchy.Secondary,
        intent = ButtonIntent.Neutral,
        enabled = !entry.shown || eye.canHide,
    )
}
