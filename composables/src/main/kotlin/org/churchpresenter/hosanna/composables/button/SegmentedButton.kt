package org.churchpresenter.hosanna.composables.button

import androidx.compose.animation.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import org.churchpresenter.hosanna.HosannaColors
import org.churchpresenter.hosanna.HosannaLevel
import org.churchpresenter.hosanna.HosannaStateAlpha
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.composables.defaults.ButtonFoundationDefaults
import org.churchpresenter.hosanna.composables.defaults.SegmentedButtonDefaults
import org.churchpresenter.hosanna.composables.foundation.ButtonColors
import org.churchpresenter.hosanna.composables.foundation.ButtonIcon
import org.churchpresenter.hosanna.composables.foundation.ButtonState
import org.churchpresenter.hosanna.composables.foundation.buttonState
import org.churchpresenter.hosanna.composables.foundation.focusRing
import org.churchpresenter.hosanna.composables.model.SegmentedButtonSize
import org.churchpresenter.hosanna.composables.model.SegmentedOption
import org.churchpresenter.hosanna.hosannaElevation

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

@Composable
fun SegmentedButton(
    options: List<SegmentedOption>,
    selectedIndex: Int,
    onSelectionChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    interactionSources: List<MutableInteractionSource> =
        remember(options.size) { List(options.size) { MutableInteractionSource() } },
    size: SegmentedButtonSize = SegmentedButtonSize.Default,
    fillWidth: Boolean = false,
    enabled: Boolean = true,
) {
    val requesters = remember(options.size) { List(options.size) { FocusRequester() } }
    var focusInside by remember { mutableStateOf(false) }
    var movedByKey by remember { mutableStateOf(false) }
    LaunchedEffect(selectedIndex) {
        if (focusInside || movedByKey) requesters.getOrNull(selectedIndex)?.requestFocus()
        movedByKey = false
    }
    val enabledFlags = options.map { enabled && it.enabled }
    val focusIndex = focusableSegment(enabledFlags, selectedIndex)
    val trackShape = RoundedCornerShape(SegmentedButtonDefaults.TrackRadius)
    SegmentLayout(
        fillWidth = fillWidth,
        modifier = modifier
            .onFocusChanged { focusInside = it.hasFocus }
            .onPreviewKeyEvent { event ->
                val step = arrowStep(event.key)
                if (event.type != KeyEventType.KeyDown || step == 0) return@onPreviewKeyEvent false
                nextEnabledSegment(enabledFlags, selectedIndex, step)?.let {
                    movedByKey = true
                    onSelectionChange(it)
                }
                true
            }
            .background(HosannaTheme.colors.trackColor(enabled), trackShape)
            .border(SegmentedButtonDefaults.TrackEdge, HosannaTheme.colors.outlineVariant, trackShape)
            .padding(SegmentedButtonDefaults.TrackPadding)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, option ->
            Segment(
                option = option,
                selected = index == selectedIndex,
                onClick = { onSelectionChange(index) },
                interactionSource = interactionSources[index],
                size = size,
                enabled = enabledFlags[index],
                controlEnabled = enabled,
                focusable = index == focusIndex,
                requester = requesters[index],
            )
        }
    }
}

internal fun arrowStep(key: Key): Int = when (key) {
    Key.DirectionLeft, Key.DirectionUp -> -1
    Key.DirectionRight, Key.DirectionDown -> 1
    else -> 0
}

internal fun nextEnabledSegment(enabled: List<Boolean>, from: Int, step: Int): Int? {
    var index = from + step
    while (index in enabled.indices) {
        if (enabled[index]) return index
        index += step
    }
    return null
}

internal fun focusableSegment(enabled: List<Boolean>, selectedIndex: Int): Int =
    if (enabled.getOrElse(selectedIndex) { false }) selectedIndex else enabled.indexOfFirst { it }

internal fun segmentShare(available: Int, gap: Int, widths: List<Int>): Int? {
    if (widths.isEmpty()) return null
    val share = (available - gap * (widths.size - 1)) / widths.size
    return share.takeIf { widths.all { it <= share } }
}

internal fun HosannaColors.trackColor(enabled: Boolean): Color =
    if (enabled) secondaryContainer else onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER)

internal fun HosannaColors.segmentColors(selected: Boolean, state: ButtonState): ButtonColors {
    val container = if (selected) primary else Color.Transparent
    val content = if (selected) onPrimary else onSurfaceVariant
    val active = if (selected) onPrimary else onSurface
    fun layer(alpha: Float) = active.copy(alpha = alpha).compositeOver(container)
    return when (state) {
        ButtonState.Rest, ButtonState.Loading -> ButtonColors(container, content, null)
        ButtonState.Hovered -> ButtonColors(layer(HosannaStateAlpha.HOVER), active, null)
        ButtonState.Pressed -> ButtonColors(layer(HosannaStateAlpha.PRESSED), active, null)
        ButtonState.Focused -> ButtonColors(container, content, primary.copy(alpha = HosannaStateAlpha.FOCUS_RING))
        ButtonState.Disabled -> ButtonColors(
            if (selected) onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTAINER) else Color.Transparent,
            onSurface.copy(alpha = HosannaStateAlpha.DISABLED_CONTENT),
            null,
        )
    }
}

internal fun segmentLevel(selected: Boolean, state: ButtonState): HosannaLevel =
    if (selected && state != ButtonState.Disabled) HosannaLevel.Level1 else HosannaLevel.Level0

@Composable
private fun Segment(
    option: SegmentedOption,
    selected: Boolean,
    onClick: () -> Unit,
    interactionSource: MutableInteractionSource,
    size: SegmentedButtonSize,
    enabled: Boolean,
    controlEnabled: Boolean,
    focusable: Boolean,
    requester: FocusRequester,
) {
    val metrics = SegmentedButtonDefaults.metrics(size)
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = HosannaTheme.colors.segmentColors(selected, state)
    val container = remember(selected, controlEnabled) { Animatable(colors.container) }
    LaunchedEffect(colors.container) {
        container.animateTo(colors.container, tween(ButtonFoundationDefaults.TRANSITION_MILLIS, easing = EaseOut))
    }
    val shape = RoundedCornerShape(SegmentedButtonDefaults.SegmentRadius)
    val iconOnly = option.iconOnly && option.icon != null
    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties { canFocus = focusable }
            .hosannaElevation(segmentLevel(selected, state), shape, HosannaTheme.elevation)
            .then(colors.ring?.let { Modifier.focusRing(it, SegmentedButtonDefaults.SegmentRadius) } ?: Modifier)
            .height(metrics.height)
            .then(
                if (iconOnly) {
                    Modifier.width(metrics.height)
                } else {
                    Modifier.defaultMinSize(minWidth = SegmentedButtonDefaults.MinWidth)
                },
            )
            .background(container.value, shape)
            .selectable(selected, interactionSource, null, enabled, Role.RadioButton, onClick)
            .then(if (iconOnly) Modifier.semantics { contentDescription = option.label } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (iconOnly) {
            ButtonIcon(option.icon, colors.content, metrics.icon)
        } else {
            Row(
                modifier = Modifier.padding(horizontal = metrics.padding),
                horizontalArrangement = Arrangement.spacedBy(SegmentedButtonDefaults.IconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                option.icon?.let { ButtonIcon(it, colors.content, metrics.icon) }
                val style = if (size == SegmentedButtonSize.Small) {
                    HosannaTheme.typography.captionStrong
                } else {
                    HosannaTheme.typography.label
                }
                BasicText(option.label, style = style.copy(color = colors.content), maxLines = 1, softWrap = false)
            }
        }
    }
}

@Composable
private fun SegmentLayout(fillWidth: Boolean, modifier: Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier) { measurables, constraints ->
        val gap = SegmentedButtonDefaults.TrackGap.roundToPx()
        val share = if (fillWidth && constraints.hasBoundedWidth) {
            segmentShare(constraints.maxWidth, gap, measurables.map { it.maxIntrinsicWidth(constraints.maxHeight) })
        } else {
            null
        }
        val placeables = measurables.map {
            it.measure(if (share != null) Constraints.fixedWidth(share) else Constraints())
        }
        val hug = placeables.sumOf { it.width } + gap * (placeables.size - 1).coerceAtLeast(0)
        val width = if (share != null) constraints.maxWidth else hug
        val height = placeables.maxOfOrNull { it.height } ?: 0
        layout(width.coerceIn(constraints.minWidth, constraints.maxWidth), height) {
            var x = 0
            placeables.forEach {
                it.placeRelative(x, (height - it.height) / 2)
                x += it.width + gap
            }
        }
    }
}
