package org.churchpresenter.hosanna.composables.selection

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.SwitchDefaults
import org.churchpresenter.hosanna.composables.defaults.SwitchMetrics
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.Spinner
import org.churchpresenter.hosanna.composables.foundation.SwitchColors
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.foundation.switchColors
import org.churchpresenter.hosanna.composables.model.SwitchLayout
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.hosanna.tabular
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_check
import org.jetbrains.compose.resources.painterResource
import org.churchpresenter.hosanna.composables.foundation.ThemeColors
import org.churchpresenter.hosanna.composables.foundation.firstPassClick

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

/**
 * A switch for a setting that takes effect at once. The whole row toggles it.
 *
 * @param checked whether it is on.
 * @param onCheckedChange called with the new state.
 * @param label the switch's label.
 * @param modifier applied to the outermost layout.
 * @param interactionSource drives the hover, pressed and focus looks; pass one to watch or freeze them.
 * @param description an optional line under the label.
 * @param trailing optional text at the end, such as "3 phones connected".
 * @param live draws [trailing] in the success color while on.
 * @param layout where the switch sits beside its label.
 * @param compact uses the small switch.
 * @param showCheck shows a check inside the knob while on.
 * @param isLoading shows a spinner in the knob and stops presses while a change is applied.
 * @param enabled whether it can be toggled.
 */
@Composable
fun Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    description: String? = null,
    trailing: String? = null,
    live: Boolean = false,
    layout: SwitchLayout = SwitchLayout.Form,
    compact: Boolean = false,
    showCheck: Boolean = false,
    isLoading: Boolean = false,
    enabled: Boolean = true,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = ThemeColors.current.switchColors(checked, state, isLoading)
    val described = description != null && !compact && layout != SwitchLayout.Inline
    val minHeight = when {
        compact -> SwitchDefaults.CompactHeight
        layout == SwitchLayout.Row -> SwitchDefaults.RowHeight
        else -> SwitchDefaults.MinHeight
    }
    val gap = when {
        compact -> SwitchDefaults.CompactGap
        layout == SwitchLayout.Inline -> SwitchDefaults.InlineGap
        else -> SwitchDefaults.FormGap
    }
    val track: @Composable () -> Unit = {
        SwitchTrack(
            checked = checked,
            pressed = state == ButtonState.Pressed,
            isLoading = isLoading,
            showCheck = showCheck,
            metrics = SwitchDefaults.metrics(compact),
            colors = colors,
        )
    }
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = minHeight)
            .firstPassClick(enabled && !isLoading, { onCheckedChange(!checked) })
            .toggleable(checked, interactionSource, null, enabled && !isLoading, Role.Switch, onCheckedChange)
            .padding(vertical = if (described) SwitchDefaults.FormGap / 2 else 0.dp),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (layout == SwitchLayout.Form) track()
        SwitchText(
            label = label,
            description = if (described) description else null,
            strong = compact || layout == SwitchLayout.Inline,
            colors = colors,
            fill = trailing != null || layout == SwitchLayout.Row,
        )
        trailing?.let {
            val color = if (live && checked && enabled) ThemeColors.current.success else colors.description
            BasicText(
                it,
                modifier = Modifier.padding(start = SwitchDefaults.TrailingGap - gap),
                style = HosannaTheme.typography.caption.tabular().copy(color = color),
            )
        }
        if (layout != SwitchLayout.Form) track()
    }
}

@Composable
private fun RowScope.SwitchText(
    label: String,
    description: String?,
    strong: Boolean,
    colors: SwitchColors,
    fill: Boolean,
) {
    val typography = HosannaTheme.typography
    Column(
        modifier = if (fill) Modifier.weight(1f) else Modifier,
        verticalArrangement = Arrangement.spacedBy(SwitchDefaults.DescriptionGap),
    ) {
        BasicText(label, style = (if (strong) typography.label else typography.bodyStrong).copy(color = colors.label))
        description?.let { BasicText(it, style = typography.caption.copy(color = colors.description)) }
    }
}

@Composable
private fun SwitchTrack(
    checked: Boolean,
    pressed: Boolean,
    isLoading: Boolean,
    showCheck: Boolean,
    metrics: SwitchMetrics,
    colors: SwitchColors,
) {
    val slide = tween<Dp>(SwitchDefaults.SLIDE_MILLIS, easing = EaseOut)
    val knob by animateDpAsState(if (checked || isLoading) metrics.knobOn else metrics.knobOff, slide)
    val stretch by animateDpAsState(if (pressed) SwitchDefaults.Stretch else 0.dp, slide)
    val inset = (metrics.height - knob) / 2
    val x by animateDpAsState(
        if (checked) metrics.width - (metrics.height - metrics.knobOn) / 2 - metrics.knobOn - stretch else inset,
        slide,
    )
    val track by animateColorAsState(colors.track, tween(SwitchDefaults.SLIDE_MILLIS))
    val knobColor by animateColorAsState(colors.knob, tween(SwitchDefaults.SLIDE_MILLIS))
    Box(
        modifier = Modifier
            .size(metrics.width, metrics.height)
            .then(colors.ring?.let { Modifier.focusRing(it, null) } ?: Modifier)
            .background(track, CircleShape)
            .border(SwitchDefaults.Edge, colors.edge, CircleShape),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset(x = x)
                .size(knob + stretch, knob)
                .hosannaElevation(HosannaLevel.Level1, CircleShape, HosannaTheme.elevation)
                .background(knobColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                isLoading -> Spinner(colors.icon, SwitchDefaults.Spinner)
                showCheck && checked -> Image(
                    painterResource(Res.drawable.ic_check),
                    null,
                    Modifier.size(SwitchDefaults.Check),
                    colorFilter = ColorFilter.tint(colors.icon),
                )
            }
        }
    }
}
