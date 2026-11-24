package org.churchpresenter.hosanna.composables.chip

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.ChipDefaults
import org.churchpresenter.hosanna.composables.foundation.ChipCircleButton
import org.churchpresenter.hosanna.composables.foundation.ChipFrame
import org.churchpresenter.hosanna.composables.foundation.ChipGlyph
import org.churchpresenter.hosanna.composables.foundation.accent
import org.churchpresenter.hosanna.composables.foundation.chipTextStyle
import org.churchpresenter.hosanna.composables.model.ChipColor
import org.churchpresenter.hosanna.composables.model.ChipSize
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_drag_dots
import org.jetbrains.compose.resources.painterResource

internal fun chipPadding(size: ChipSize, leading: Boolean, end: Boolean = true): PaddingValues {
    val metrics = ChipDefaults.metrics(size)
    return PaddingValues(
        start = if (leading) metrics.iconSidePadding else metrics.padding,
        end = if (end) metrics.padding else ChipDefaults.RemoveGap,
    )
}

internal fun isRemoveKey(event: KeyEvent): Boolean =
    event.type == KeyEventType.KeyDown && (event.key == Key.Delete || event.key == Key.Backspace)

@Composable
fun ActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    icon: Painter? = null,
    size: ChipSize = ChipSize.Default,
    color: ChipColor = ChipColor.Neutral,
    enabled: Boolean = true,
) {
    ChipFrame(
        modifier = modifier,
        interactionSource = interactionSource,
        size = size,
        color = color,
        selected = false,
        enabled = enabled,
        click = Modifier.clickable(interactionSource, null, enabled, role = Role.Button, onClick = onClick),
        padding = chipPadding(size, leading = icon != null),
    ) { colors ->
        icon?.let { ChipGlyph(it, colors.sub, ChipDefaults.Icon) }
        BasicText(label, style = chipTextStyle(size).copy(color = colors.content), maxLines = 1, softWrap = false)
    }
}

@Composable
fun FilterChip(
    label: String,
    selected: Boolean,
    onSelectedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    icon: Painter? = null,
    size: ChipSize = ChipSize.Default,
    color: ChipColor = ChipColor.Neutral,
    enabled: Boolean = true,
) {
    val leading = if (selected) painterResource(Res.drawable.ic_check) else icon
    ChipFrame(
        modifier = modifier,
        interactionSource = interactionSource,
        size = size,
        color = color,
        selected = selected,
        enabled = enabled,
        click = Modifier.toggleable(selected, interactionSource, null, enabled, Role.Checkbox, onSelectedChange),
        padding = chipPadding(size, leading = leading != null),
    ) { colors ->
        leading?.let { ChipGlyph(it, if (selected) colors.content else colors.sub, ChipDefaults.Icon) }
        BasicText(label, style = chipTextStyle(size).copy(color = colors.content), maxLines = 1, softWrap = false)
    }
}

@Composable
fun InputChip(
    label: String,
    onRemove: () -> Unit,
    removeContentDescription: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    removeInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    icon: Painter? = null,
    onClick: (() -> Unit)? = null,
    dragHandle: Boolean = false,
    size: ChipSize = ChipSize.Default,
    color: ChipColor = ChipColor.Neutral,
    enabled: Boolean = true,
) {
    val removeKeys = Modifier.onKeyEvent { event -> (enabled && isRemoveKey(event)).also { if (it) onRemove() } }
    val click = if (onClick != null) {
        Modifier.clickable(interactionSource, null, enabled, role = Role.Button, onClick = onClick)
    } else {
        Modifier.semantics(mergeDescendants = true) {}.focusable(enabled, interactionSource)
    }
    ChipFrame(
        modifier = modifier,
        interactionSource = interactionSource,
        size = size,
        color = color,
        selected = false,
        enabled = enabled,
        click = removeKeys.then(click),
        padding = chipPadding(size, leading = dragHandle || icon != null, end = false),
    ) { colors ->
        if (dragHandle) {
            Image(
                painterResource(Res.drawable.ic_drag_dots),
                null,
                Modifier.height(ChipDefaults.DragHandleHeight),
                colorFilter = ColorFilter.tint(colors.sub),
            )
        }
        icon?.let { ChipGlyph(it, colors.sub, ChipDefaults.Icon) }
        BasicText(label, style = chipTextStyle(size).copy(color = colors.content), maxLines = 1, softWrap = false)
        ChipCircleButton(
            icon = painterResource(Res.drawable.ic_close),
            glyph = ChipDefaults.RemoveGlyph,
            side = ChipDefaults.RemoveCircle,
            tint = colors.sub,
            fill = colors.content.copy(alpha = HosannaStateAlpha.HOVER),
            contentDescription = removeContentDescription,
            onClick = onRemove,
            interactionSource = removeInteractionSource,
            enabled = enabled,
        )
    }
}

@Composable
fun Tag(
    text: String,
    modifier: Modifier = Modifier,
    color: ChipColor = ChipColor.Neutral,
) {
    val colors = HosannaTheme.colors
    val accent = colors.accent(color)
    Box(
        modifier = modifier
            .height(ChipDefaults.TagHeight)
            .background(
                accent?.copy(alpha = HosannaStateAlpha.CONTENT_TYPE_CONTAINER) ?: colors.secondaryContainer,
                RoundedCornerShape(ChipDefaults.TagRadius),
            )
            .padding(horizontal = ChipDefaults.TagPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text.asOverline(),
            style = HosannaTheme.typography.overline.copy(color = accent ?: colors.onSurfaceVariant),
            maxLines = 1,
            softWrap = false,
        )
    }
}
