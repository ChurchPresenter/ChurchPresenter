package org.churchpresenter.hosanna.composables.field

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import java.time.LocalTime
import org.churchpresenter.theme.HosannaRadius
import org.churchpresenter.theme.HosannaStateAlpha
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.theme.asOverline
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.defaults.TimeFieldDefaults
import org.churchpresenter.hosanna.composables.foundation.FieldState
import org.churchpresenter.hosanna.composables.foundation.fieldColors
import org.churchpresenter.hosanna.composables.foundation.fieldState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.MINUTES_PER_HOUR
import org.churchpresenter.hosanna.composables.model.TimeFieldActions
import org.churchpresenter.hosanna.composables.model.TimeFieldLabels
import org.churchpresenter.hosanna.composables.model.TimeKey
import org.churchpresenter.hosanna.composables.model.TimePart
import org.churchpresenter.hosanna.composables.model.hourText
import org.churchpresenter.hosanna.composables.model.isPm
import org.churchpresenter.hosanna.composables.model.minuteText
import org.churchpresenter.hosanna.composables.model.nextPart
import org.churchpresenter.hosanna.composables.model.setPeriod
import org.churchpresenter.hosanna.composables.model.stepTime
import org.churchpresenter.hosanna.composables.model.timeKey
import org.churchpresenter.hosanna.composables.model.timeParts
import org.churchpresenter.hosanna.composables.model.typeDigit
import org.churchpresenter.theme.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

private fun nowMinutes(): Int = LocalTime.now().let { it.hour * MINUTES_PER_HOUR + it.minute }

/**
 * A time of day typed part by part, with a spinner that opens on click.
 *
 * @param minutes the time as minutes after midnight.
 * @param onMinutesChange called with the new time.
 * @param label the field's label.
 * @param labels the parts' screen-reader names and the AM/PM words.
 * @param actions the spinner's button texts.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param use24Hour shows 0–23 hours instead of AM/PM.
 * @param nextDayTag a tag after the time, such as "+1 day", for a time past midnight.
 * @param error an error under the field, which also turns its edge red.
 * @param enabled whether the field can be used.
 * @param currentMinutes the time the spinner's Now button sets; the clock by default.
 */
@Composable
fun TimeField(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    label: String,
    labels: TimeFieldLabels,
    actions: TimeFieldActions,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    use24Hour: Boolean = false,
    nextDayTag: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    currentMinutes: () -> Int = ::nowMinutes,
) {
    var active by remember { mutableStateOf<TimePart?>(null) }
    var pending by remember { mutableStateOf<Int?>(null) }
    var open by remember { mutableStateOf(false) }
    val hovered by interactionSource.collectIsHoveredAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val parts = timeParts(use24Hour)
    val colors = ThemeColors.current.fieldColors(
        if (open) FieldState.Focused else fieldState(enabled, false, hovered, focused),
        error != null,
    )
    val container by animateColorAsState(colors.container, tween(FieldDefaults.TRANSITION_MILLIS))
    val border by animateColorAsState(colors.border, tween(FieldDefaults.TRANSITION_MILLIS))
    val requester = remember { FocusRequester() }
    val shape = HosannaTheme.shapes.m
    fun handle(key: TimeKey): Boolean {
        val part = active ?: TimePart.Minute
        when (key) {
            TimeKey.Open -> open = true
            TimeKey.Close -> open = false
            is TimeKey.Move -> active = nextPart(parts, active ?: TimePart.Hour, key.step)
            is TimeKey.Step -> onMinutesChange(stepTime(minutes, part, key.up))
            is TimeKey.Digit -> {
                val typed = typeDigit(minutes, part, pending, key.value, use24Hour)
                onMinutesChange(typed.minutes)
                pending = typed.pending
                if (typed.advance) active = nextPart(parts, part, 1)
                return true
            }
            is TimeKey.Period -> onMinutesChange(setPeriod(minutes, key.pm))
            TimeKey.None -> return false
        }
        pending = null
        return true
    }
    Column(modifier.width(IntrinsicSize.Max)) {
        Box {
            Row(
                modifier = Modifier
                    .widthIn(min = if (use24Hour) TimeFieldDefaults.Width24 else TimeFieldDefaults.Width12)
                    .width(IntrinsicSize.Max)
                    .then(colors.ring?.let { Modifier.focusRing(it, HosannaRadius.M) } ?: Modifier)
                    .hoverable(interactionSource, enabled)
                    .background(container, shape)
                    .border(FieldDefaults.Border, border, shape)
                    .height(FieldDefaults.LabeledHeight)
                    .focusRequester(requester)
                    .onFocusChanged {
                        if (it.isFocused && active == null) active = TimePart.Hour
                        if (!it.isFocused) {
                            active = null
                            pending = null
                        }
                    }
                    .onPreviewKeyEvent { enabled && handle(timeKey(it, open, use24Hour)) }
                    .semantics(mergeDescendants = true) {}
                    .focusable(enabled, interactionSource)
                    .pointerInput(enabled) {
                        if (enabled) {
                            detectTapGestures {
                                requester.requestFocus()
                                open = true
                            }
                        }
                    }
                    .padding(start = FieldDefaults.HorizontalPadding, end = TimeFieldDefaults.EndPadding),
                horizontalArrangement = Arrangement.spacedBy(FieldDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.Center) {
                    BasicText(label.asOverline(), style = HosannaTheme.typography.overline.copy(color = colors.label))
                    Spacer(Modifier.height(FieldDefaults.LabelGap))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        parts.forEachIndexed { index, part ->
                            if (index == 1) TimeText(":", colors.value)
                            if (index == 2) Spacer(Modifier.width(TimeFieldDefaults.PartPadding * 2))
                            TimePartText(
                                text = when (part) {
                                    TimePart.Hour -> hourText(minutes, use24Hour)
                                    TimePart.Minute -> minuteText(minutes)
                                    TimePart.Period -> if (isPm(minutes)) labels.pm else labels.am
                                },
                                active = focused && active == part,
                                ink = colors.value,
                                enabled = enabled,
                            ) {
                                active = part
                                requester.requestFocus()
                                open = true
                            }
                        }
                    }
                }
                nextDayTag?.let { NextDayTag(it) }
                Spacer(Modifier.weight(1f))
                if (enabled && listOf(hovered, focused, open).any { it }) {
                    Column {
                        TimeArrow(Res.drawable.ic_arrow_up, actions.increase) {
                            onMinutesChange(stepTime(minutes, active ?: TimePart.Minute, up = true))
                        }
                        TimeArrow(Res.drawable.ic_arrow_down, actions.decrease) {
                            onMinutesChange(stepTime(minutes, active ?: TimePart.Minute, up = false))
                        }
                    }
                } else {
                    Spacer(Modifier.width(TimeFieldDefaults.ArrowWidth))
                }
            }
            if (open && enabled) {
                TimeSpinner(minutes, onMinutesChange, use24Hour, labels, actions, currentMinutes) { open = false }
            }
        }
        Supporting("", null, error, null, enabled)
    }
}

@Composable
private fun TimeText(text: String, ink: Color) {
    BasicText(text, style = HosannaTheme.typography.bodyStrong.tabular().copy(color = ink))
}

@Composable
private fun TimePartText(text: String, active: Boolean, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    val colors = ThemeColors.current
    Box(
        modifier = Modifier
            .background(if (active) colors.primaryContainer else Color.Transparent, HosannaTheme.shapes.xs)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = TimeFieldDefaults.PartPadding),
    ) {
        TimeText(text, if (active) colors.onPrimaryContainer else ink)
    }
}

@Composable
private fun NextDayTag(text: String) {
    val colors = ThemeColors.current
    Box(
        modifier = Modifier
            .height(TimeFieldDefaults.TagHeight)
            .background(colors.tertiaryContainer, HosannaTheme.shapes.xs)
            .padding(horizontal = TimeFieldDefaults.TagPadding),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = HosannaTheme.typography.captionStrong.copy(color = colors.onTertiaryContainer),
            maxLines = 1,
        )
    }
}

@Composable
private fun TimeArrow(icon: DrawableResource, contentDescription: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(TimeFieldDefaults.ArrowWidth, TimeFieldDefaults.ArrowHeight)
            .background(
                if (hovered) ThemeColors.current.onSurface.copy(alpha = HosannaStateAlpha.HOVER) else Color.Transparent,
                HosannaTheme.shapes.xs,
            )
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(painterResource(icon), ThemeColors.current.onSurfaceVariant, TimeFieldDefaults.Arrow)
    }
}
