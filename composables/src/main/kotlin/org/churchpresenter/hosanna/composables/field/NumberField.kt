@file:OptIn(ExperimentalComposeUiApi::class)

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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.defaults.NumberFieldDefaults
import org.churchpresenter.hosanna.composables.foundation.FieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.foundation.rememberHoldRepeat
import org.churchpresenter.hosanna.composables.model.NumberFieldActions
import org.churchpresenter.hosanna.composables.model.NumberRange
import org.churchpresenter.hosanna.composables.model.canStepNumber
import org.churchpresenter.hosanna.composables.model.clampNumber
import org.churchpresenter.hosanna.composables.model.filterNumberInput
import org.churchpresenter.hosanna.composables.model.formatNumber
import org.churchpresenter.hosanna.composables.model.inRange
import org.churchpresenter.hosanna.composables.model.parseNumber
import org.churchpresenter.hosanna.composables.model.stepNumber
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun NumberField(
    value: Double,
    onValueChange: (Double) -> Unit,
    label: String,
    range: NumberRange,
    actions: NumberFieldActions,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    helper: String? = null,
    rangeError: String? = null,
    enabled: Boolean = true,
) {
    var outOfRange by remember { mutableStateOf(false) }
    val colors = numberFieldColors(interactionSource, enabled, outOfRange)
    val shape = HosannaTheme.shapes.m
    Column(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .numberFrame(colors, shape, interactionSource, enabled)
                .height(FieldDefaults.LabeledHeight)
                .padding(start = FieldDefaults.HorizontalPadding, end = NumberFieldDefaults.ArrowInset),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                BasicText(label.asOverline(), style = HosannaTheme.typography.overline.copy(color = colors.label))
                Spacer(Modifier.height(FieldDefaults.LabelGap))
                NumberInput(
                    value, onValueChange, range, live = true, interactionSource, enabled,
                    HosannaTheme.typography.bodyStrong.tabular().copy(color = colors.value),
                    colors.caret,
                ) { outOfRange = it }
            }
            if (enabled) {
                Column {
                    NumberArrow(Res.drawable.ic_arrow_up, actions.increase, canStepNumber(value, range, up = true)) {
                        onValueChange(stepNumber(value, range, up = true))
                    }
                    NumberArrow(Res.drawable.ic_arrow_down, actions.decrease, canStepNumber(value, range, up = false)) {
                        onValueChange(stepNumber(value, range, up = false))
                    }
                }
            }
        }
        Supporting("", helper, if (outOfRange) rangeError else null, null, enabled)
    }
}

@Composable
fun UnitField(
    value: Double,
    onValueChange: (Double) -> Unit,
    unit: String,
    range: NumberRange,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    rangeError: String? = null,
    enabled: Boolean = true,
) {
    var outOfRange by remember { mutableStateOf(false) }
    val colors = numberFieldColors(interactionSource, enabled, outOfRange)
    val shape = HosannaTheme.shapes.m
    Column(modifier.width(IntrinsicSize.Max)) {
        Row(
            modifier = Modifier
                .widthIn(min = NumberFieldDefaults.UnitWidth)
                .width(IntrinsicSize.Max)
                .numberFrame(colors, shape, interactionSource, enabled)
                .height(FieldDefaults.CompactHeight)
                .padding(horizontal = FieldDefaults.HorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(NumberFieldDefaults.UnitGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NumberInput(
                value, onValueChange, range, live = false, interactionSource, enabled,
                HosannaTheme.typography.bodyStrong.tabular().copy(color = colors.value, textAlign = TextAlign.End),
                colors.caret,
                Modifier.weight(1f),
            ) { outOfRange = it }
            BasicText(unit, style = HosannaTheme.typography.body.copy(color = colors.icon))
        }
        Supporting("", null, if (outOfRange) rangeError else null, null, enabled)
    }
}

@Composable
private fun numberFieldColors(source: MutableInteractionSource, enabled: Boolean, error: Boolean): FieldColors {
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    return HosannaTheme.colors.fieldColors(fieldState(enabled, false, hovered, focused), error)
}

@Composable
private fun Modifier.numberFrame(
    colors: FieldColors,
    shape: Shape,
    source: MutableInteractionSource,
    enabled: Boolean,
): Modifier {
    val container by animateColorAsState(colors.container, tween(FieldDefaults.TRANSITION_MILLIS))
    val border by animateColorAsState(colors.border, tween(FieldDefaults.TRANSITION_MILLIS))
    return this
        .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
        .hoverable(source, enabled)
        .background(container, shape)
        .border(FieldDefaults.Border, border, shape)
}

@Composable
private fun NumberInput(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: NumberRange,
    live: Boolean,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
    style: TextStyle,
    caret: Color,
    modifier: Modifier = Modifier,
    onOutOfRange: (Boolean) -> Unit,
) {
    val focused by interactionSource.collectIsFocusedAsState()
    var text by remember { mutableStateOf(formatNumber(value, range.decimals)) }
    var focusedOnce by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) text = formatNumber(value, range.decimals) }
    fun commit() {
        val next = parseNumber(text)?.let { clampNumber(it, range) } ?: value
        if (next != value) onValueChange(next)
        text = formatNumber(next, range.decimals)
        onOutOfRange(false)
    }
    fun step(up: Boolean, big: Boolean) {
        val next = stepNumber(parseNumber(text)?.let { clampNumber(it, range) } ?: value, range, up, big)
        onValueChange(next)
        text = formatNumber(next, range.decimals)
        onOutOfRange(false)
    }
    BasicTextField(
        value = text,
        onValueChange = { typed ->
            text = filterNumberInput(typed, range)
            val parsed = parseNumber(text)
            onOutOfRange(parsed != null && !inRange(parsed, range))
            if (live && parsed != null && inRange(parsed, range)) onValueChange(parsed)
        },
        modifier = modifier
            .onFocusChanged {
                if (it.isFocused) focusedOnce = true else if (focusedOnce) commit()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> commit()
                    Key.DirectionUp -> step(up = true, big = event.isShiftPressed)
                    Key.DirectionDown -> step(up = false, big = event.isShiftPressed)
                    else -> return@onPreviewKeyEvent false
                }
                true
            }
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (focused && enabled && delta != 0f) step(up = delta < 0f, big = false)
            },
        enabled = enabled,
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(caret),
        interactionSource = interactionSource,
    )
}

@Composable
private fun NumberArrow(icon: DrawableResource, contentDescription: String, enabled: Boolean, onStep: () -> Unit) {
    val colors = HosannaTheme.colors
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val hold = rememberHoldRepeat(source, enabled) { onStep() }
    val fill = when {
        enabled && pressed -> colors.onSurface.copy(alpha = HosannaStateAlpha.PRESSED)
        enabled && hovered -> colors.onSurface.copy(alpha = HosannaStateAlpha.HOVER)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .size(NumberFieldDefaults.ArrowWidth, NumberFieldDefaults.ArrowHeight)
            .background(fill, HosannaTheme.shapes.xs)
            .clickable(source, null, enabled, role = Role.Button) { hold.click { onStep() } }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(
            painterResource(icon),
            if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT),
            NumberFieldDefaults.Arrow,
        )
    }
}
