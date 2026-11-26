package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.theme.AppRadius
import org.churchpresenter.theme.StateAlpha
import org.churchpresenter.theme.AppTheme
import org.churchpresenter.theme.asOverline
import org.churchpresenter.hosanna.composables.button.IconButton
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.ColorFieldDefaults
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.defaults.TimeFieldDefaults
import org.churchpresenter.hosanna.composables.foundation.FieldColors
import org.churchpresenter.hosanna.composables.foundation.FieldState
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.ButtonIntent
import org.churchpresenter.hosanna.composables.model.ColorFieldLabels
import org.churchpresenter.hosanna.composables.model.IconButtonSize
import org.churchpresenter.hosanna.composables.model.drawCheckerboard
import org.churchpresenter.hosanna.composables.model.hexText
import org.churchpresenter.hosanna.composables.model.opacityText
import org.churchpresenter.hosanna.composables.model.parseColor
import org.churchpresenter.theme.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_palette
import org.jetbrains.compose.resources.painterResource

internal fun fieldText(color: Color?, none: String): String = color?.let(::hexText) ?: none

internal fun typedColor(text: String, current: Color?, allowOpacity: Boolean): Color? {
    val parsed = parseColor(text) ?: return null
    val keepsAlpha = text.trim().removePrefix("#").length != 8 && !text.trim().startsWith("rgba")
    return when {
        !allowOpacity -> parsed.copy(alpha = 1f)
        keepsAlpha -> parsed.copy(alpha = current?.alpha ?: 1f)
        else -> parsed
    }
}

internal fun isNoneText(text: String, none: String): Boolean =
    text.isBlank() || text.trim().equals(none, ignoreCase = true)

/**
 * A hex color field with a swatch that opens a color picker.
 *
 * @param color the color, or null when none is set.
 * @param onColorChange called with the new color, or null when cleared.
 * @param label the field's label.
 * @param labels the picker's texts.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param allowOpacity adds an opacity slider and keeps the color's alpha.
 * @param allowNone lets the color be cleared.
 * @param themeColors swatches from the current theme, shown in the picker.
 * @param recentColors recently used swatches, shown in the picker.
 * @param enabled whether the field can be used.
 */
@Composable
fun ColorField(
    color: Color?,
    onColorChange: (Color?) -> Unit,
    label: String,
    labels: ColorFieldLabels,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    allowOpacity: Boolean = false,
    allowNone: Boolean = false,
    themeColors: List<Color> = emptyList(),
    recentColors: List<Color> = emptyList(),
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    var text by remember { mutableStateOf(fieldText(color, labels.none)) }
    var invalid by remember { mutableStateOf(false) }
    var open by remember { mutableStateOf(false) }
    var original by remember { mutableStateOf(color) }
    LaunchedEffect(color, focused) { if (!focused) text = fieldText(color, labels.none) }
    val colors = AppTheme.colors.fieldColors(
        if (open) FieldState.Focused else fieldState(enabled, false, hovered, focused),
        invalid,
    )
    val container by animateColorAsState(colors.container, tween(FieldDefaults.TRANSITION_MILLIS))
    val border by animateColorAsState(colors.border, tween(FieldDefaults.TRANSITION_MILLIS))
    val shape = AppTheme.shapes.m
    val typography = AppTheme.typography
    fun openPicker() {
        original = color
        open = true
    }
    Column(modifier.width(IntrinsicSize.Max)) {
        Box {
            Row(
                modifier = Modifier
                    .widthIn(min = if (allowOpacity) ColorFieldDefaults.WidthWithOpacity else ColorFieldDefaults.Width)
                    .width(IntrinsicSize.Max)
                    .then(colors.ring?.let { Modifier.focusRing(it, AppRadius.M) } ?: Modifier)
                    .hoverable(interactionSource, enabled)
                    .background(container, shape)
                    .border(FieldDefaults.Border, border, shape)
                    .height(FieldDefaults.LabeledHeight)
                    .padding(start = FieldDefaults.HorizontalPadding, end = TimeFieldDefaults.EndPadding),
                horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ColorSwatch(color, enabled, labels.pick) { openPicker() }
                ColorValue(
                    label = label,
                    text = text,
                    colors = colors,
                    opacity = if (allowOpacity) color?.let(::opacityText) else null,
                    enabled = enabled,
                    interactionSource = interactionSource,
                    modifier = Modifier.weight(1f),
                    onText = { typed ->
                        text = typed
                        invalid = false
                        typedColor(typed, color, allowOpacity)?.let(onColorChange)
                    },
                    onLeave = {
                        when {
                            allowNone && isNoneText(text, labels.none) -> onColorChange(null)
                            typedColor(text, color, allowOpacity) == null -> invalid = true
                        }
                    },
                )
                IconButton(
                    painterResource(Res.drawable.ic_palette),
                    labels.pick,
                    { openPicker() },
                    size = IconButtonSize.Small,
                    hierarchy = ButtonHierarchy.Tertiary,
                    intent = ButtonIntent.Neutral,
                    enabled = enabled,
                )
            }
            if (open && enabled) {
                ColorPickerPopover(
                    color = color ?: original ?: AppTheme.colors.primary,
                    onColorChange = {
                        invalid = false
                        onColorChange(it)
                    },
                    allowOpacity = allowOpacity,
                    themeColors = themeColors,
                    recentColors = recentColors,
                    labels = labels,
                    onRestore = {
                        onColorChange(original)
                        open = false
                    },
                    onDismiss = { open = false },
                )
            }
        }
        Supporting("", null, if (invalid) labels.invalid else null, null, enabled)
    }
}

@Composable
private fun ColorSwatch(color: Color?, enabled: Boolean, contentDescription: String, onClick: () -> Unit) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.s
    val light = colors.surfaceContainerLowest
    val dark = colors.outlineVariant
    val none = colors.error
    Box(
        modifier = Modifier
            .size(ColorFieldDefaults.Swatch)
            .alpha(if (enabled) 1f else StateAlpha.DISABLED_CONTENT)
            .clip(shape)
            .drawBehind {
                drawCheckerboard(light, dark)
                if (color != null) {
                    drawRect(color)
                } else {
                    drawRect(light)
                    drawLine(none, Offset(0f, size.height), Offset(size.width, 0f), ColorFieldDefaults.Ring.toPx())
                }
            }
            .border(FieldDefaults.Border, colors.outline, shape)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
    )
}

@Composable
private fun ColorValue(
    label: String,
    text: String,
    colors: FieldColors,
    opacity: String?,
    enabled: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier,
    onText: (String) -> Unit,
    onLeave: () -> Unit,
) {
    val typography = AppTheme.typography
    var focusedOnce by remember { mutableStateOf(false) }
    Column(modifier) {
        BasicText(label.asOverline(), style = typography.overline.copy(color = colors.label))
        Spacer(Modifier.height(FieldDefaults.LabelGap))
        Row(
            horizontalArrangement = Arrangement.spacedBy(FieldDefaults.SupportingIconGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = text,
                onValueChange = onText,
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        if (it.isFocused) focusedOnce = true else if (focusedOnce) onLeave()
                    },
                enabled = enabled,
                textStyle = typography.bodyStrong.tabular().copy(color = colors.value),
                singleLine = true,
                cursorBrush = SolidColor(colors.caret),
                interactionSource = interactionSource,
            )
            opacity?.let { BasicText(it, style = typography.body.tabular().copy(color = colors.icon)) }
        }
    }
}
