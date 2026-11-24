package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.hosanna.composables.defaults.IconButtonDefaults
import org.churchpresenter.hosanna.composables.defaults.IconButtonMetrics
import org.churchpresenter.hosanna.composables.foundation.ButtonFrame
import org.churchpresenter.hosanna.composables.foundation.ButtonIcon
import org.churchpresenter.hosanna.composables.foundation.ButtonLook
import org.churchpresenter.hosanna.composables.foundation.Spinner
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

/**
 * A rounded-square button that shows only an icon.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the button's side.
 * @param hierarchy how much the button stands out.
 * @param intent what the button does, which picks its colors.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the icon and stops presses.
 */
@Composable
fun IconButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: IconButtonSize = IconButtonSize.Medium,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Secondary,
    intent: ButtonIntent = ButtonIntent.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    IconButtonBody(
        icon, contentDescription, onClick, interactionSource, modifier,
        IconButtonDefaults.metrics(size), hierarchy, intent, enabled, loading,
    )
}

/**
 * An icon button that stays on or off, reported to screen readers as a toggle.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param checked whether it is on.
 * @param onCheckedChange called with the new state when pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the button's side.
 * @param hierarchy how much the button stands out while off; on, it is filled.
 * @param intent what the button does, which picks its colors.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the icon and stops presses.
 */
@Composable
fun IconToggleButton(
    icon: Painter,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: IconButtonSize = IconButtonSize.Medium,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Secondary,
    intent: ButtonIntent = ButtonIntent.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    IconButtonBody(
        icon = icon,
        contentDescription = contentDescription,
        onClick = { onCheckedChange(!checked) },
        interactionSource = interactionSource,
        modifier = modifier,
        metrics = IconButtonDefaults.metrics(size),
        hierarchy = toggleHierarchy(hierarchy, checked),
        intent = intent,
        enabled = enabled,
        loading = loading,
        checked = checked,
    )
}

internal fun toggleHierarchy(hierarchy: ButtonHierarchy, checked: Boolean): ButtonHierarchy =
    if (checked) ButtonHierarchy.Primary else hierarchy

@Composable
internal fun IconButtonBody(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    metrics: IconButtonMetrics,
    hierarchy: ButtonHierarchy,
    intent: ButtonIntent,
    enabled: Boolean,
    loading: Boolean,
    checked: Boolean? = null,
) {
    ButtonFrame(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(metrics.side)
            .semantics { this.contentDescription = contentDescription },
        look = ButtonLook(hierarchy, intent, metrics.side, metrics.cornerRadius),
        enabled = enabled,
        loading = loading,
        checked = checked,
    ) { content ->
        if (loading) Spinner(content, metrics.spinner) else ButtonIcon(icon, content, metrics.icon)
    }
}
