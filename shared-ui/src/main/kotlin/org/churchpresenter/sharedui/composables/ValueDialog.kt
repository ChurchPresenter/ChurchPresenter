package org.churchpresenter.sharedui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_remove
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.cancel
import org.churchpresenter.strings.generated.resources.ok
import org.churchpresenter.strings.generated.resources.value_dialog_decrease
import org.churchpresenter.strings.generated.resources.value_dialog_increase
import org.churchpresenter.strings.generated.resources.value_dialog_keys_hint
import org.churchpresenter.strings.generated.resources.value_dialog_out_of_range
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.GhostButton
import org.churchpresenter.theme.components.KeyButton
import org.churchpresenter.theme.components.RaisedButton
import org.churchpresenter.theme.elevationPalette
import org.churchpresenter.theme.isDarkScheme
import org.churchpresenter.theme.sunken
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.churchpresenter.icons.generated.resources.Res as IconRes

private val ValueDialogShape = AppShape(18.dp)
private val ValueFieldShape = AppShape(12.dp)
private val ValueChipShape = AppShape(9.dp)
private const val VALUE_MAX_DIGITS = 5
private val VALUE_DIGIT_WIDTH = 17.dp
private val VALUE_FIELD_SLACK = 8.dp
private const val VALUE_CHIP_ALPHA_DARK = 0.22f
private const val VALUE_CHIP_ALPHA_LIGHT = 0.13f
private const val VALUE_FOCUS_RING_ALPHA = 0.25f
private const val VALUE_KEYS_HINT_ALPHA = 0.7f

/** What a [ValueDialog] edits: its range, the step − and + take, the quick picks and the unit. */
data class ValueDialogSpec(
    val min: Int,
    val max: Int,
    val step: Int,
    val presets: List<Int>,
    val unit: String,
)

/**
 * The "enter a number" dialog: a title and a line saying what the value does, the number large between
 * − and + with its unit, quick-pick chips, then Cancel and OK. Enter confirms, Esc cancels, ↑ ↓ step.
 */
@Composable
fun ValueDialog(
    title: String,
    hint: String,
    icon: Painter,
    accent: Color,
    initial: Int,
    spec: ValueDialogSpec,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by remember {
        val text = initial.toString()
        mutableStateOf(TextFieldValue(text, TextRange(0, text.length)))
    }
    val value = field.text.toIntOrNull()
    val valid = value != null && value in spec.min..spec.max
    fun set(v: Int) {
        val text = v.coerceIn(spec.min, spec.max).toString()
        field = TextFieldValue(text, TextRange(text.length))
    }
    fun step(delta: Int) = set((value ?: spec.min) + delta)
    fun confirm() {
        if (valid) onConfirm(value)
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .shadow(24.dp, ValueDialogShape)
                .clip(ValueDialogShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, ValueDialogShape)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter, Key.NumPadEnter -> { confirm(); true }
                        Key.Escape -> { onDismiss(); true }
                        Key.DirectionUp -> { step(spec.step); true }
                        Key.DirectionDown -> { step(-spec.step); true }
                        else -> false
                    }
                }
                .padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 18.dp),
        ) {
            ValueDialogHeader(title, hint, icon, accent)
            Spacer(Modifier.height(18.dp))
            ValueStepper(
                field = field,
                onFieldChange = { next ->
                    field = next.copy(text = next.text.filter(Char::isDigit).take(VALUE_MAX_DIGITS))
                },
                unit = spec.unit,
                valid = valid,
                onStep = { step(it * spec.step) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                spec.presets.forEach { preset ->
                    ValuePresetChip(
                        label = "$preset ${spec.unit}",
                        selected = value == preset,
                        onClick = { set(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (!valid) {
                Text(
                    stringResource(Res.string.value_dialog_out_of_range, spec.min, spec.max, spec.unit),
                    modifier = Modifier.padding(top = 9.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(Res.string.value_dialog_keys_hint),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = VALUE_KEYS_HINT_ALPHA),
                )
                GhostButton(onClick = onDismiss, shape = AppShape(10.dp)) {
                    Text(stringResource(Res.string.cancel), color = MaterialTheme.colorScheme.onSurface)
                }
                RaisedButton(
                    onClick = ::confirm,
                    enabled = valid,
                    shape = AppShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 22.dp),
                ) {
                    Text(stringResource(Res.string.ok), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ValueDialogHeader(title: String, hint: String, icon: Painter, accent: Color) {
    val chipAlpha = if (isDarkScheme(MaterialTheme.colorScheme)) VALUE_CHIP_ALPHA_DARK else VALUE_CHIP_ALPHA_LIGHT
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Box(
            modifier = Modifier.size(34.dp).clip(AppShape(10.dp)).background(accent.copy(alpha = chipAlpha)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = accent)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** − , the number with its unit, + . [onStep] takes −1 or +1. */
@Composable
private fun ValueStepper(
    field: TextFieldValue,
    onFieldChange: (TextFieldValue) -> Unit,
    unit: String,
    valid: Boolean,
    onStep: (Int) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    val edge = when {
        !valid -> scheme.error
        focused -> scheme.primary
        else -> scheme.outlineVariant
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ValueStepKey(painterResource(IconRes.drawable.ic_remove), stringResource(Res.string.value_dialog_decrease)) {
            onStep(-1)
        }
        Row(
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .then(
                    if (focused && valid) {
                        Modifier.border(3.dp, scheme.primary.copy(alpha = VALUE_FOCUS_RING_ALPHA), ValueFieldShape)
                    } else {
                        Modifier
                    }
                )
                .sunken(ValueFieldShape, elevationPalette())
                .border(1.5.dp, edge, ValueFieldShape)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = field,
                onValueChange = onFieldChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                cursorBrush = SolidColor(scheme.primary),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onSurface,
                    textAlign = TextAlign.End,
                    fontFeatureSettings = "tnum",
                ),
                modifier = Modifier
                    .widthIn(min = 28.dp)
                    .width(VALUE_DIGIT_WIDTH * field.text.length.coerceAtLeast(1) + VALUE_FIELD_SLACK)
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused },
            )
            Spacer(Modifier.width(6.dp))
            Text(unit, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = scheme.onSurfaceVariant)
        }
        ValueStepKey(painterResource(IconRes.drawable.ic_add), stringResource(Res.string.value_dialog_increase)) {
            onStep(1)
        }
    }
}

@Composable
private fun ValueStepKey(icon: Painter, description: String, onClick: () -> Unit) {
    KeyButton(
        onClick = onClick,
        modifier = Modifier.width(44.dp).height(52.dp),
        shape = ValueFieldShape,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        contentPadding = PaddingValues(0.dp),
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ValuePresetChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val chipAlpha = if (isDarkScheme(scheme)) VALUE_CHIP_ALPHA_DARK else VALUE_CHIP_ALPHA_LIGHT
    Box(
        modifier = modifier
            .height(30.dp)
            .clip(ValueChipShape)
            .background(if (selected) scheme.primary.copy(alpha = chipAlpha) else Color.Transparent)
            .border(BorderStroke(1.dp, if (selected) scheme.primary else scheme.outlineVariant), ValueChipShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium.copy(fontFeatureSettings = "tnum"),
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
