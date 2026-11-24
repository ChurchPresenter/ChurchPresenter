package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.CircularButtonDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize

@Composable
fun CircularButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: IconButtonSize = IconButtonSize.Medium,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Tertiary,
    intent: ButtonIntent = ButtonIntent.Neutral,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    IconButtonBody(
        icon, contentDescription, onClick, interactionSource, modifier,
        CircularButtonDefaults.metrics(size), hierarchy, intent, enabled, loading,
    )
}

@Composable
fun CircularToggleButton(
    icon: Painter,
    contentDescription: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    size: IconButtonSize = IconButtonSize.Medium,
    hierarchy: ButtonHierarchy = ButtonHierarchy.Tertiary,
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
        metrics = CircularButtonDefaults.metrics(size),
        hierarchy = toggleHierarchy(hierarchy, checked),
        intent = intent,
        enabled = enabled,
        loading = loading,
        checked = checked,
    )
}

@Composable
fun CircularButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(HosannaTheme.colors.secondaryContainer, HosannaTheme.shapes.full)
            .padding(CircularButtonDefaults.GroupPadding),
        horizontalArrangement = Arrangement.spacedBy(CircularButtonDefaults.GroupGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
