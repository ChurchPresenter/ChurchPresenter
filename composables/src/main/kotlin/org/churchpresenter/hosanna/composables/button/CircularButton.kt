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
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

/**
 * A round icon button, as used for transport controls.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the button's diameter.
 * @param hierarchy how much the button stands out.
 * @param intent what the button does, which picks its colors.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the icon and stops presses.
 */
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

/**
 * A round icon button that stays on or off, such as Loop.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param checked whether it is on.
 * @param onCheckedChange called with the new state when pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param size the button's diameter.
 * @param hierarchy how much the button stands out while off; on, it is filled.
 * @param intent what the button does, which picks its colors.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the icon and stops presses.
 */
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

/**
 * A row of circular buttons with the group's spacing, such as back, play and forward.
 *
 * @param modifier applied to the outermost layout.
 * @param content the buttons.
 */
@Composable
fun CircularButtonGroup(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .background(ThemeColors.current.secondaryContainer, HosannaTheme.shapes.full)
            .padding(CircularButtonDefaults.GroupPadding),
        horizontalArrangement = Arrangement.spacedBy(CircularButtonDefaults.GroupGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}
