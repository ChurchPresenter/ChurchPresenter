package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.composables.button.focusRing
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_close
import org.churchpresenter.icons.generated.resources.ic_warning
import org.jetbrains.compose.resources.painterResource

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal fun isOverLimit(value: String, maxLength: Int?): Boolean = maxLength != null && value.length > maxLength

internal fun counterText(value: String, maxLength: Int): String = "${value.length} / $maxLength"

internal fun showClear(clearable: Boolean, value: String, editable: Boolean, active: Boolean): Boolean =
    clearable && value.isNotEmpty() && editable && active

@Composable
fun TextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    label: String? = null,
    placeholder: String? = null,
    multiline: Boolean = false,
    leadingIcon: Painter? = null,
    suffix: String? = null,
    clearContentDescription: String? = null,
    helper: String? = null,
    error: String? = null,
    maxLength: Int? = null,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val overLimit = isOverLimit(value, maxLength)
    val colors = HosannaTheme.colors.fieldColors(
        fieldState(enabled, readOnly, hovered, focused),
        error = error != null || overLimit,
    )
    val spec = tween<Color>(FieldDefaults.TRANSITION_MILLIS, easing = EaseOut)
    val container by animateColorAsState(colors.container, spec)
    val border by animateColorAsState(colors.border, spec)
    val shape = HosannaTheme.shapes.m
    val typography = HosannaTheme.typography
    val clear = showClear(clearContentDescription != null, value, enabled && !readOnly, hovered || focused)
    Column(modifier) {
        Box(Modifier.fillMaxWidth().hoverable(interactionSource, enabled)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
                .background(container, shape)
                .then(
                    if (readOnly) {
                        Modifier.dashedBorder(border, FieldDefaults.Border, HosannaRadius.M)
                    } else {
                        Modifier.border(FieldDefaults.Border, border, shape)
                    },
                )
                .then(fieldHeight(multiline, label != null))
                .padding(
                    horizontal = FieldDefaults.HorizontalPadding,
                    vertical = if (multiline) FieldDefaults.MultilineVerticalPadding else 0.dp,
                ),
            enabled = enabled,
            readOnly = readOnly,
            textStyle = typography.body.copy(color = colors.value),
            singleLine = !multiline,
            cursorBrush = SolidColor(colors.caret),
            interactionSource = interactionSource,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            decorationBox = { inner ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
                    verticalAlignment = if (multiline) Alignment.Top else Alignment.CenterVertically,
                ) {
                    leadingIcon?.let { FieldGlyph(it, colors.icon, FieldDefaults.Icon) }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        if (label != null) {
                            BasicText(label.asOverline(), style = typography.overline.copy(color = colors.label))
                            Spacer(Modifier.height(FieldDefaults.LabelGap))
                        }
                        Box {
                            if (value.isEmpty() && placeholder != null) {
                                BasicText(placeholder, style = typography.body.copy(color = colors.icon), maxLines = 1)
                            }
                            inner()
                        }
                    }
                    suffix?.let { BasicText(it, style = typography.caption.copy(color = colors.icon)) }
                    if (clear) Spacer(Modifier.width(FieldDefaults.ClearCircle))
                }
            },
        )
            if (clear) {
                ClearButton(
                    contentDescription = clearContentDescription.orEmpty(),
                    tint = colors.icon,
                    modifier = Modifier
                        .align(if (multiline) Alignment.TopEnd else Alignment.CenterEnd)
                        .padding(
                            end = FieldDefaults.HorizontalPadding,
                            top = if (multiline) FieldDefaults.MultilineVerticalPadding else 0.dp,
                        ),
                ) { onValueChange("") }
            }
        }
        Supporting(value, helper, error, maxLength, enabled)
    }
}

private fun fieldHeight(multiline: Boolean, labeled: Boolean): Modifier = when {
    multiline -> Modifier.heightIn(min = FieldDefaults.MultilineMinHeight, max = FieldDefaults.MultilineMaxHeight)
    labeled -> Modifier.height(FieldDefaults.LabeledHeight)
    else -> Modifier.height(FieldDefaults.CompactHeight)
}

@Composable
internal fun Supporting(value: String, helper: String?, error: String?, maxLength: Int?, enabled: Boolean) {
    val message = error ?: helper
    if (message == null && maxLength == null) return
    val colors = HosannaTheme.colors
    val caption = HosannaTheme.typography.caption
    val muted = if (enabled) {
        colors.onSurfaceVariant
    } else {
        colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = FieldDefaults.HorizontalPadding, top = FieldDefaults.SupportingGap),
        horizontalArrangement = Arrangement.spacedBy(FieldDefaults.SupportingIconGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (error != null) {
            FieldGlyph(painterResource(Res.drawable.ic_warning), colors.error, FieldDefaults.SupportingIcon)
        }
        BasicText(
            message.orEmpty(),
            modifier = Modifier.weight(1f),
            style = caption.copy(color = if (error != null) colors.error else muted),
        )
        maxLength?.let {
            BasicText(
                counterText(value, it),
                style = caption.tabular().copy(color = if (isOverLimit(value, it)) colors.error else muted),
            )
        }
    }
}

@Composable
internal fun ClearButton(
    contentDescription: String,
    tint: Color,
    modifier: Modifier,
    side: Dp = FieldDefaults.ClearCircle,
    onClear: () -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .size(side)
            .background(
                if (hovered) tint.copy(alpha = HosannaStateAlpha.HOVER) else Color.Transparent,
                HosannaTheme.shapes.full,
            )
            .clickable(source, null, role = Role.Button, onClick = onClear)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(painterResource(Res.drawable.ic_close), tint, FieldDefaults.ClearGlyph)
    }
}

@Composable
internal fun FieldGlyph(icon: Painter, tint: Color, size: Dp, modifier: Modifier = Modifier) {
    Image(icon, null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
