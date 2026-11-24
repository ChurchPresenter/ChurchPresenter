package org.churchpresenter.hosanna.composables.list

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.defaults.ListDefaults
import org.churchpresenter.hosanna.composables.feedback.Tooltip
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.tabular

@Composable
fun ListIconTile(icon: Painter, modifier: Modifier = Modifier) {
    val colors = HosannaTheme.colors
    Box(
        modifier
            .size(ListDefaults.IconTile)
            .background(colors.surfaceContainerHighest, RoundedCornerShape(ListDefaults.RowRadius)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            icon,
            null,
            Modifier.size(ListDefaults.IconTileIcon),
            colorFilter = ColorFilter.tint(colors.onSurfaceVariant),
        )
    }
}

@Composable
fun ListMeta(text: String, modifier: Modifier = Modifier) {
    BasicText(
        text,
        modifier,
        style = HosannaTheme.typography.caption.tabular().copy(color = HosannaTheme.colors.onSurfaceVariant),
    )
}

@Composable
fun RowAction(
    icon: Painter,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    destructive: Boolean = false,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    Tooltip(label, modifier) {
        IconButton(
            icon,
            label,
            onClick,
            interactionSource = interactionSource,
            size = IconButtonSize.Small,
            hierarchy = ButtonHierarchy.Tertiary,
            intent = if (destructive && hovered) ButtonIntent.Critical else ButtonIntent.Neutral,
        )
    }
}
