package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

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
