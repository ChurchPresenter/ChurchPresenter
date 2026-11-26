package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.hosanna.composables.defaults.StandardButtonDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonFrame
import org.churchpresenter.hosanna.composables.foundation.ButtonIcon
import org.churchpresenter.hosanna.composables.foundation.ButtonLook
import org.churchpresenter.hosanna.composables.foundation.Spinner
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent

private fun look(hierarchy: ButtonHierarchy, intent: ButtonIntent) =
    ButtonLook(hierarchy, intent, StandardButtonDefaults.Height, AppRadius.M)

/**
 * A button with a text label and an optional leading icon.
 *
 * @param text the label.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param hierarchy how much the button stands out.
 * @param intent what the button does, which picks its colors.
 * @param icon an optional icon before the label.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the content and stops presses.
 */
@Composable
fun StandardButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hierarchy: ButtonHierarchy = ButtonHierarchy.Primary,
    intent: ButtonIntent = ButtonIntent.Default,
    icon: Painter? = null,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val tertiary = hierarchy == ButtonHierarchy.Tertiary
    val end = if (tertiary) {
        StandardButtonDefaults.TertiaryHorizontalPadding
    } else {
        StandardButtonDefaults.HorizontalPadding
    }
    val start = if (icon != null && !tertiary) StandardButtonDefaults.IconSidePadding else end
    ButtonFrame(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minWidth = StandardButtonDefaults.MinWidth),
        look = look(hierarchy, intent),
        enabled = enabled,
        loading = loading,
    ) { content ->
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier
                    .padding(PaddingValues(start = start, end = end))
                    .alpha(if (loading && icon == null) 0f else 1f),
                horizontalArrangement = Arrangement.spacedBy(StandardButtonDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (icon != null) {
                    if (loading) {
                        Spinner(content, StandardButtonDefaults.IconSize)
                    } else {
                        ButtonIcon(icon, content, StandardButtonDefaults.IconSize)
                    }
                }
                BasicText(
                    text = text,
                    style = AppTheme.typography.label.copy(color = content),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            if (loading && icon == null) Spinner(content, StandardButtonDefaults.IconSize)
        }
    }
}

/**
 * A standard-height button that shows only an icon.
 *
 * @param icon the icon.
 * @param contentDescription the button's name for screen readers.
 * @param onClick called when the button is pressed.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param hierarchy how much the button stands out.
 * @param intent what the button does, which picks its colors.
 * @param enabled whether the button can be pressed.
 * @param loading shows a spinner in place of the icon and stops presses.
 */
@Composable
fun StandardButton(
    icon: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    hierarchy: ButtonHierarchy = ButtonHierarchy.Primary,
    intent: ButtonIntent = ButtonIntent.Default,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    ButtonFrame(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .size(StandardButtonDefaults.Height)
            .semantics { this.contentDescription = contentDescription },
        look = look(hierarchy, intent),
        enabled = enabled,
        loading = loading,
    ) { content ->
        if (loading) {
            Spinner(content, StandardButtonDefaults.IconSize)
        } else {
            ButtonIcon(icon, content, StandardButtonDefaults.IconSize)
        }
    }
}
