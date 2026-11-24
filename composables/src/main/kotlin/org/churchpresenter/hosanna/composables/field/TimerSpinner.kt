@file:OptIn(ExperimentalComposeUiApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import org.churchpresenter.hosanna.HosannaRadius
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.asOverline
import org.churchpresenter.hosanna.composables.button.focusRing
import org.churchpresenter.hosanna.composables.chip.FilterChip
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_add
import org.churchpresenter.icons.generated.resources.ic_remove
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
fun TimerSpinner(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    labels: TimerSpinnerLabels,
    modifier: Modifier = Modifier,
    interactionSources: Map<TimerUnit, MutableInteractionSource> =
        remember { TimerUnit.entries.associateWith { MutableInteractionSource() } },
    presets: List<TimerPreset> = emptyList(),
    enabled: Boolean = true,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(TimerSpinnerDefaults.PresetGap)) {
        Row(verticalAlignment = Alignment.Bottom) {
            TimerUnit.entries.forEachIndexed { index, unit ->
                if (index > 0) ColonDots(enabled)
                UnitStepper(
                    unit = unit,
                    label = when (unit) {
                        TimerUnit.Hours -> labels.hours
                        TimerUnit.Minutes -> labels.minutes
                        TimerUnit.Seconds -> labels.seconds
                    },
                    seconds = seconds,
                    onSecondsChange = onSecondsChange,
                    labels = labels,
                    interactionSource = interactionSources.getValue(unit),
                    enabled = enabled,
                )
            }
        }
        if (presets.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(TimerSpinnerDefaults.ChipGap),
                verticalArrangement = Arrangement.spacedBy(TimerSpinnerDefaults.ChipGap),
            ) {
                presets.forEach { preset ->
                    FilterChip(
                        preset.label,
                        selected = seconds == preset.seconds,
                        onSelectedChange = { onSecondsChange(preset.seconds) },
                        enabled = enabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnitStepper(
    unit: TimerUnit,
    label: String,
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    labels: TimerSpinnerLabels,
    interactionSource: MutableInteractionSource,
    enabled: Boolean,
) {
    val colors = HosannaTheme.colors
    val hovered by interactionSource.collectIsHoveredAsState()
    var editing by remember { mutableStateOf(false) }
    val shape = HosannaTheme.shapes.l
    val muted = if (enabled) {
        colors.onSurfaceVariant
    } else {
        colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    }
    val edge = when {
        editing -> colors.primary
        hovered && enabled -> colors.onSurface.copy(alpha = HosannaStateAlpha.HOVER).compositeOver(colors.outline)
        else -> colors.outline
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        BasicText(label.asOverline(), style = HosannaTheme.typography.overline.copy(color = muted))
        Spacer(Modifier.height(TimerSpinnerDefaults.LabelGap))
        Column(
            modifier = Modifier
                .width(TimerSpinnerDefaults.Width)
                .then(if (editing) Modifier.editRing(colors.primary) else Modifier)
                .hoverable(interactionSource, enabled)
                .clip(shape)
                .background(if (editing) colors.surfaceContainerLowest else colors.surfaceVariant)
                .border(FieldDefaults.Border, edge, shape)
                .onPointerEvent(PointerEventType.Scroll) { event ->
                    val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                    val up = delta < 0f
                    if (enabled && delta != 0f && canStep(seconds, unit, up)) {
                        onSecondsChange(stepTimer(seconds, unit, up))
                    }
                },
        ) {
            StepButton(Res.drawable.ic_add, labels.increase, enabled && canStep(seconds, unit, up = true)) { fast ->
                onSecondsChange(stepTimer(seconds, unit, up = true, fast = fast))
            }
            Divider(edge)
            NumberCell(seconds, unit, enabled, editing, { editing = it }, onSecondsChange)
            Divider(edge)
            StepButton(Res.drawable.ic_remove, labels.decrease, enabled && canStep(seconds, unit, up = false)) { fast ->
                onSecondsChange(stepTimer(seconds, unit, up = false, fast = fast))
            }
        }
    }
}

@Composable
private fun Divider(color: Color) {
    Box(Modifier.fillMaxWidth().height(FieldDefaults.Border).background(color))
}

@Composable
private fun StepButton(
    icon: DrawableResource,
    contentDescription: String,
    enabled: Boolean,
    onStep: (Boolean) -> Unit,
) {
    val colors = HosannaTheme.colors
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val hovered by source.collectIsHoveredAsState()
    val hold = rememberHoldRepeat(source, enabled, onStep)
    val fill = when {
        enabled && pressed -> colors.onSurface.copy(alpha = HosannaStateAlpha.PRESSED)
        enabled && hovered -> colors.onSurface.copy(alpha = HosannaStateAlpha.HOVER)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimerSpinnerDefaults.ButtonHeight)
            .background(fill)
            .clickable(source, null, enabled, role = Role.Button) { hold.click(onStep) }
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(
            painterResource(icon),
            if (enabled) colors.onSurfaceVariant else colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT),
            TimerSpinnerDefaults.Icon,
        )
    }
}

@Composable
private fun NumberCell(
    seconds: Int,
    unit: TimerUnit,
    enabled: Boolean,
    editing: Boolean,
    onEditing: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val colors = HosannaTheme.colors
    val ink = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    val style = HosannaTheme.typography.display.tabular().copy(color = ink, textAlign = TextAlign.Center)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimerSpinnerDefaults.NumberHeight)
            .then(if (enabled && !editing) Modifier.clickable { onEditing(true) } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (!editing) {
            BasicText(unitText(seconds, unit), style = style)
        } else {
            NumberEditor(seconds, unit, style, onEditing, onSecondsChange)
        }
    }
}

@Composable
private fun NumberEditor(
    seconds: Int,
    unit: TimerUnit,
    style: TextStyle,
    onEditing: (Boolean) -> Unit,
    onSecondsChange: (Int) -> Unit,
) {
    val text = unitText(seconds, unit)
    var value by remember { mutableStateOf(TextFieldValue(text, TextRange(0, text.length))) }
    val requester = remember { FocusRequester() }
    var focusedOnce by remember { mutableStateOf(false) }
    var finished by remember { mutableStateOf(false) }
    fun commit() {
        if (finished) return
        finished = true
        parseUnit(value.text)?.let { onSecondsChange(setUnit(seconds, unit, it)) }
        onEditing(false)
    }
    fun cancel() {
        finished = true
        onEditing(false)
    }
    LaunchedEffect(Unit) { requester.requestFocus() }
    BasicTextField(
        value = value,
        onValueChange = { next -> value = next.copy(text = next.text.filter { it.isDigit() }.take(2)) },
        modifier = Modifier
            .fillMaxWidth()
            .focusRequester(requester)
            .onFocusChanged {
                if (it.isFocused) focusedOnce = true
                if (!it.isFocused && focusedOnce) commit()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter, Key.NumPadEnter -> commit()
                    Key.Escape -> cancel()
                    Key.DirectionUp, Key.DirectionDown -> {
                        val up = event.key == Key.DirectionUp
                        if (canStep(seconds, unit, up)) {
                            val next = stepTimer(seconds, unit, up)
                            onSecondsChange(next)
                            val shown = unitText(next, unit)
                            value = TextFieldValue(shown, TextRange(0, shown.length))
                        }
                    }
                    else -> return@onPreviewKeyEvent false
                }
                true
            },
        textStyle = style,
        singleLine = true,
        cursorBrush = SolidColor(HosannaTheme.colors.primary),
    )
}

@Composable
private fun ColonDots(enabled: Boolean) {
    val colors = HosannaTheme.colors
    val dot = if (enabled) colors.onSurfaceFaint else colors.onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT)
    Box(
        modifier = Modifier
            .padding(horizontal = TimerSpinnerDefaults.ColonPadding)
            .height(TimerSpinnerDefaults.ButtonHeight * 2 + TimerSpinnerDefaults.NumberHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(TimerSpinnerDefaults.DotGap)) {
            repeat(2) { Box(Modifier.size(TimerSpinnerDefaults.Dot).background(dot, HosannaTheme.shapes.full)) }
        }
    }
}

private fun Modifier.editRing(primary: Color): Modifier =
    focusRing(primary.copy(alpha = HosannaStateAlpha.FOCUS_RING), HosannaRadius.L)
