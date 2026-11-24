@file:OptIn(ExperimentalComposeUiApi::class)

package org.churchpresenter.hosanna.composables.field

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.button.ButtonHierarchy
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

internal object TimeSpinnerDefaults {
    val Padding: Dp = 8.dp
    val ColumnWidth: Dp = 52.dp
    val ColumnGap: Dp = 4.dp
    val ArrowHeight: Dp = 28.dp
    val RowHeight: Dp = 32.dp
    val Arrow: Dp = 12.dp
}

@Immutable
internal class SpinValues(val previous: String, val current: String, val next: String)

internal fun spinValues(
    minutes: Int,
    part: TimePart,
    use24Hour: Boolean,
    labels: TimeFieldLabels,
): SpinValues = when (part) {
    TimePart.Hour -> SpinValues(
        hourText(stepTime(minutes, TimePart.Hour, up = false), use24Hour),
        hourText(minutes, use24Hour),
        hourText(stepTime(minutes, TimePart.Hour, up = true), use24Hour),
    )
    TimePart.Minute -> SpinValues(
        minuteText(stepTime(minutes, TimePart.Minute, up = false)),
        minuteText(minutes),
        minuteText(stepTime(minutes, TimePart.Minute, up = true)),
    )
    TimePart.Period -> if (isPm(minutes)) {
        SpinValues(labels.am, labels.pm, "")
    } else {
        SpinValues("", labels.am, labels.pm)
    }
}

@Composable
internal fun TimeSpinner(
    minutes: Int,
    onMinutesChange: (Int) -> Unit,
    use24Hour: Boolean,
    labels: TimeFieldLabels,
    actions: TimeFieldActions,
    currentMinutes: () -> Int,
    onDone: () -> Unit,
) {
    val gap = with(LocalDensity.current) { MenuDefaults.Gap.roundToPx() }
    Popup(
        popupPositionProvider = remember(gap) { MenuPositionProvider(gap) },
        onDismissRequest = onDone,
        properties = PopupProperties(focusable = false),
    ) {
        MenuSurface(width = null, padding = TimeSpinnerDefaults.Padding) {
            Row(horizontalArrangement = Arrangement.spacedBy(TimeSpinnerDefaults.ColumnGap)) {
                timeParts(use24Hour).forEach { part ->
                    val title = when (part) {
                        TimePart.Hour -> labels.hour
                        TimePart.Minute -> labels.minute
                        TimePart.Period -> labels.period
                    }
                    SpinColumn(
                        title = title,
                        values = spinValues(minutes, part, use24Hour, labels),
                        actions = actions,
                        onStep = { up -> onMinutesChange(stepTime(minutes, part, up)) },
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = TimeSpinnerDefaults.Padding)
                    .height(FieldDefaults.Border)
                    .background(HosannaTheme.colors.outlineVariant),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                StandardButton(
                    actions.now,
                    { onMinutesChange(wrapDay(currentMinutes())) },
                    hierarchy = ButtonHierarchy.Tertiary,
                )
                Spacer(Modifier.weight(1f))
                StandardButton(actions.done, onDone)
            }
        }
    }
}

@Composable
private fun SpinColumn(title: String, values: SpinValues, actions: TimeFieldActions, onStep: (Boolean) -> Unit) {
    val colors = HosannaTheme.colors
    val typography = HosannaTheme.typography
    Column(
        modifier = Modifier
            .width(TimeSpinnerDefaults.ColumnWidth)
            .onPointerEvent(PointerEventType.Scroll) { event ->
                val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                if (delta != 0f) onStep(delta < 0f)
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(title, style = typography.caption.copy(color = colors.onSurfaceFaint), maxLines = 1)
        SpinArrow(Res.drawable.ic_arrow_up, actions.increase) { onStep(true) }
        SpinValue(values.previous, current = false) { onStep(false) }
        SpinValue(values.current, current = true) {}
        SpinValue(values.next, current = false) { onStep(true) }
        SpinArrow(Res.drawable.ic_arrow_down, actions.decrease) { onStep(false) }
    }
}

@Composable
private fun SpinArrow(icon: DrawableResource, contentDescription: String, onClick: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimeSpinnerDefaults.ArrowHeight)
            .background(
                if (hovered) HosannaTheme.colors.onSurface.copy(alpha = HosannaStateAlpha.HOVER) else Color.Transparent,
                HosannaTheme.shapes.m,
            )
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(painterResource(icon), HosannaTheme.colors.onSurfaceVariant, TimeSpinnerDefaults.Arrow)
    }
}

@Composable
private fun SpinValue(text: String, current: Boolean, onClick: () -> Unit) {
    val colors = HosannaTheme.colors
    val typography = HosannaTheme.typography
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(TimeSpinnerDefaults.RowHeight)
            .background(if (current) colors.primary else Color.Transparent, HosannaTheme.shapes.m)
            .then(if (!current && text.isNotEmpty()) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text,
            style = if (current) {
                typography.bodyStrong.tabular().copy(color = colors.onPrimary)
            } else {
                typography.body.tabular().copy(color = colors.onSurfaceFaint)
            },
            maxLines = 1,
        )
    }
}
