package org.churchpresenter.hosanna.composables.foundation

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.Dp
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.button.arrowStep
import org.churchpresenter.hosanna.composables.button.focusableSegment
import org.churchpresenter.hosanna.composables.button.icon
import org.churchpresenter.hosanna.composables.button.nextEnabledSegment
import org.churchpresenter.hosanna.composables.button.segmentColors
import org.churchpresenter.hosanna.composables.button.segmentLevel
import org.churchpresenter.hosanna.composables.button.trackColor
import org.churchpresenter.hosanna.composables.defaults.ButtonFoundationDefaults
import org.churchpresenter.hosanna.composables.defaults.IconSegmentDefaults
import org.churchpresenter.hosanna.composables.defaults.IconSegmentMetrics
import org.churchpresenter.hosanna.composables.defaults.SegmentedButtonDefaults
import org.churchpresenter.hosanna.hosannaElevation
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal fun HosannaColors.iconSegmentColors(value: ToggleableState, state: ButtonState): ButtonColors {
    if (value != ToggleableState.Indeterminate || state == ButtonState.Disabled) {
        return segmentColors(value == ToggleableState.On, state)
    }
    fun layer(alpha: Float) = onPrimaryContainer.copy(alpha = alpha).compositeOver(primaryContainer)
    return when (state) {
        ButtonState.Hovered -> ButtonColors(layer(HosannaStateAlpha.HOVER), onPrimaryContainer, null)
        ButtonState.Pressed -> ButtonColors(layer(HosannaStateAlpha.PRESSED), onPrimaryContainer, null)
        ButtonState.Focused -> ButtonColors(
            primaryContainer,
            onPrimaryContainer,
            primary.copy(alpha = HosannaStateAlpha.FOCUS_RING),
        )
        else -> ButtonColors(primaryContainer, onPrimaryContainer, null)
    }
}

internal fun startSegment(role: SegmentRole, values: List<ToggleableState>, enabled: List<Boolean>): Int =
    focusableSegment(enabled, if (role == SegmentRole.Radio) values.indexOf(ToggleableState.On) else -1)

@Composable
internal fun IconSegmentTrack(
    enabled: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val trackShape = RoundedCornerShape(IconSegmentDefaults.TrackRadius)
    Row(
        modifier = modifier
            .background(HosannaTheme.colors.trackColor(enabled), trackShape)
            .border(SegmentedButtonDefaults.TrackEdge, HosannaTheme.colors.outlineVariant, trackShape)
            .padding(SegmentedButtonDefaults.TrackPadding),
        horizontalArrangement = Arrangement.spacedBy(SegmentedButtonDefaults.TrackGap),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

internal class IconSegmentOption(
    val icon: DrawableResource,
    val contentDescription: String,
    val value: ToggleableState,
    val enabled: Boolean,
    val interactionSource: MutableInteractionSource,
    val onClick: () -> Unit,
)

@Composable
internal fun IconSegmentGroup(
    options: List<IconSegmentOption>,
    role: SegmentRole,
    metrics: IconSegmentMetrics,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val enabledFlags = options.map { enabled && it.enabled }
    val values = options.map { it.value }
    val selectedIndex = if (role == SegmentRole.Radio) values.indexOf(ToggleableState.On) else -1
    var current by remember { mutableIntStateOf(startSegment(role, values, enabledFlags)) }
    var moved by remember { mutableStateOf(false) }
    val requesters = remember(options.size) { List(options.size) { FocusRequester() } }
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) current = selectedIndex }
    LaunchedEffect(current) {
        if (moved) requesters.getOrNull(current)?.requestFocus()
        moved = false
    }
    val focusIndex = if (enabledFlags.getOrElse(current) { false }) {
        current
    } else {
        startSegment(role, values, enabledFlags)
    }
    IconSegmentTrack(
        enabled = enabled,
        modifier = modifier
            .onPreviewKeyEvent { event ->
                val step = arrowStep(event.key)
                if (event.type != KeyEventType.KeyDown || step == 0) return@onPreviewKeyEvent false
                nextEnabledSegment(enabledFlags, focusIndex, step)?.let {
                    moved = true
                    current = it
                    if (role == SegmentRole.Radio) options[it].onClick()
                }
                true
            }
            .then(if (role == SegmentRole.Radio) Modifier.selectableGroup() else Modifier),
    ) {
        options.forEachIndexed { index, option ->
            IconSegment(
                value = values[index],
                role = role,
                onClick = option.onClick,
                contentDescription = option.contentDescription,
                interactionSource = option.interactionSource,
                metrics = metrics,
                enabled = enabledFlags[index],
                focusable = index == focusIndex,
                requester = requesters[index],
            ) { colors -> SegmentGlyph(painterResource(option.icon), colors.content, metrics.icon) }
        }
    }
}

@Composable
internal fun IconSegment(
    value: ToggleableState,
    role: SegmentRole,
    onClick: () -> Unit,
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    metrics: IconSegmentMetrics,
    enabled: Boolean,
    focusable: Boolean = true,
    requester: FocusRequester = remember { FocusRequester() },
    width: Dp = metrics.width,
    content: @Composable (ButtonColors) -> Unit,
) {
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = HosannaTheme.colors.iconSegmentColors(value, state)
    val container = remember(value) { Animatable(colors.container) }
    LaunchedEffect(colors.container) {
        container.animateTo(colors.container, tween(ButtonFoundationDefaults.TRANSITION_MILLIS, easing = EaseOut))
    }
    val shape = RoundedCornerShape(IconSegmentDefaults.SegmentRadius)
    val click = when (role) {
        SegmentRole.Radio -> Modifier.selectable(
            value == ToggleableState.On, interactionSource, null, enabled, Role.RadioButton, onClick,
        )
        SegmentRole.Toggle -> Modifier.triStateToggleable(
            value, interactionSource, null, enabled, Role.Checkbox, onClick,
        )
        SegmentRole.Action -> Modifier.clickable(
            interactionSource, null, enabled, role = Role.Button, onClick = onClick,
        )
    }
    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties { canFocus = focusable }
            .hosannaElevation(segmentLevel(value == ToggleableState.On, state), shape, HosannaTheme.elevation)
            .then(colors.ring?.let { Modifier.focusRing(it, IconSegmentDefaults.SegmentRadius) } ?: Modifier)
            .size(width, metrics.height)
            .background(container.value, shape)
            .then(click)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        content(colors)
    }
}

@Composable
internal fun SegmentGlyph(icon: Painter, tint: Color, size: Dp) {
    Image(icon, null, Modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
