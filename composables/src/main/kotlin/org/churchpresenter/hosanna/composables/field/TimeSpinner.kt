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
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.churchpresenter.theme.HosannaStateAlpha
import org.churchpresenter.theme.HosannaTheme
import org.churchpresenter.hosanna.composables.button.StandardButton
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.defaults.FieldDefaults
import org.churchpresenter.hosanna.composables.defaults.MenuDefaults
import org.churchpresenter.hosanna.composables.defaults.TimeSpinnerDefaults
import org.churchpresenter.hosanna.composables.model.ButtonHierarchy
import org.churchpresenter.hosanna.composables.model.MenuPositionProvider
import org.churchpresenter.hosanna.composables.model.SpinValues
import org.churchpresenter.hosanna.composables.model.TimeFieldActions
import org.churchpresenter.hosanna.composables.model.TimeFieldLabels
import org.churchpresenter.hosanna.composables.model.TimePart
import org.churchpresenter.hosanna.composables.model.spinValues
import org.churchpresenter.hosanna.composables.model.stepTime
import org.churchpresenter.hosanna.composables.model.timeParts
import org.churchpresenter.hosanna.composables.model.wrapDay
import org.churchpresenter.theme.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_arrow_down
import org.churchpresenter.icons.generated.resources.ic_arrow_up
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors

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
                    .background(ThemeColors.current.outlineVariant),
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
    val colors = ThemeColors.current
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
        BasicText(title, style = typography.caption.copy(color = colors.faint), maxLines = 1)
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
                if (hovered) ThemeColors.current.onSurface.copy(alpha = HosannaStateAlpha.HOVER) else Color.Transparent,
                HosannaTheme.shapes.m,
            )
            .clickable(source, null, role = Role.Button, onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FieldGlyph(painterResource(icon), ThemeColors.current.onSurfaceVariant, TimeSpinnerDefaults.Arrow)
    }
}

@Composable
private fun SpinValue(text: String, current: Boolean, onClick: () -> Unit) {
    val colors = ThemeColors.current
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
                typography.body.tabular().copy(color = colors.faint)
            },
            maxLines = 1,
        )
    }
}
