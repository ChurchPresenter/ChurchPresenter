package org.churchpresenter.hosanna.composables.button

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
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import org.churchpresenter.hosanna.HosannaTheme
import org.churchpresenter.hosanna.hosannaElevation
import org.churchpresenter.icons.generated.resources.Res
import org.churchpresenter.icons.generated.resources.ic_align_bottom
import org.churchpresenter.icons.generated.resources.ic_align_center
import org.churchpresenter.icons.generated.resources.ic_align_justify
import org.churchpresenter.icons.generated.resources.ic_align_left
import org.churchpresenter.icons.generated.resources.ic_align_middle
import org.churchpresenter.icons.generated.resources.ic_align_right
import org.churchpresenter.icons.generated.resources.ic_align_top
import org.churchpresenter.icons.generated.resources.ic_layer_align_bottom
import org.churchpresenter.icons.generated.resources.ic_layer_align_center
import org.churchpresenter.icons.generated.resources.ic_layer_align_left
import org.churchpresenter.icons.generated.resources.ic_layer_align_middle
import org.churchpresenter.icons.generated.resources.ic_layer_align_right
import org.churchpresenter.icons.generated.resources.ic_layer_align_top
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

enum class TextAlignment { Left, Center, Right, Justify, Top, Middle, Bottom }

enum class LayerAlignment { Left, Center, Right, Top, Middle, Bottom }

internal val HorizontalTextAlignments =
    listOf(TextAlignment.Left, TextAlignment.Center, TextAlignment.Right, TextAlignment.Justify)
internal val VerticalTextAlignments = listOf(TextAlignment.Top, TextAlignment.Middle, TextAlignment.Bottom)
internal val HorizontalLayerAlignments = listOf(LayerAlignment.Left, LayerAlignment.Center, LayerAlignment.Right)
internal val VerticalLayerAlignments = listOf(LayerAlignment.Top, LayerAlignment.Middle, LayerAlignment.Bottom)

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)

internal fun TextAlignment.icon(): DrawableResource = when (this) {
    TextAlignment.Left -> Res.drawable.ic_align_left
    TextAlignment.Center -> Res.drawable.ic_align_center
    TextAlignment.Right -> Res.drawable.ic_align_right
    TextAlignment.Justify -> Res.drawable.ic_align_justify
    TextAlignment.Top -> Res.drawable.ic_align_top
    TextAlignment.Middle -> Res.drawable.ic_align_middle
    TextAlignment.Bottom -> Res.drawable.ic_align_bottom
}

internal fun LayerAlignment.icon(): DrawableResource = when (this) {
    LayerAlignment.Left -> Res.drawable.ic_layer_align_left
    LayerAlignment.Center -> Res.drawable.ic_layer_align_center
    LayerAlignment.Right -> Res.drawable.ic_layer_align_right
    LayerAlignment.Top -> Res.drawable.ic_layer_align_top
    LayerAlignment.Middle -> Res.drawable.ic_layer_align_middle
    LayerAlignment.Bottom -> Res.drawable.ic_layer_align_bottom
}

@Composable
fun TextAlignmentButtons(
    horizontal: TextAlignment?,
    vertical: TextAlignment?,
    onAlignmentChange: (TextAlignment) -> Unit,
    contentDescription: (TextAlignment) -> String,
    modifier: Modifier = Modifier,
    interactionSources: Map<TextAlignment, MutableInteractionSource> =
        remember { TextAlignment.entries.associateWith { MutableInteractionSource() } },
    compact: Boolean = false,
    enabled: Boolean = true,
    disabled: Set<TextAlignment> = emptySet(),
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AlignmentButtonDefaults.GroupGap),
        verticalArrangement = Arrangement.spacedBy(AlignmentButtonDefaults.GroupGap),
    ) {
        val groups = listOf(HorizontalTextAlignments to horizontal, VerticalTextAlignments to vertical)
        groups.forEach { (options, chosen) ->
            AlignmentGroup(
                options = options,
                selected = chosen,
                radio = true,
                onClick = onAlignmentChange,
                icon = { it.icon() },
                contentDescription = contentDescription,
                interactionSources = interactionSources,
                compact = compact,
                enabled = enabled,
                disabled = disabled,
            )
        }
    }
}

@Composable
fun LayerAlignmentButtons(
    onAlign: (LayerAlignment) -> Unit,
    contentDescription: (LayerAlignment) -> String,
    modifier: Modifier = Modifier,
    interactionSources: Map<LayerAlignment, MutableInteractionSource> =
        remember { LayerAlignment.entries.associateWith { MutableInteractionSource() } },
    compact: Boolean = false,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AlignmentButtonDefaults.GroupGap),
        verticalArrangement = Arrangement.spacedBy(AlignmentButtonDefaults.GroupGap),
    ) {
        listOf(HorizontalLayerAlignments, VerticalLayerAlignments).forEach { options ->
            AlignmentGroup(
                options = options,
                selected = null,
                radio = false,
                onClick = onAlign,
                icon = { it.icon() },
                contentDescription = contentDescription,
                interactionSources = interactionSources,
                compact = compact,
                enabled = enabled,
                disabled = emptySet(),
            )
        }
    }
}

@Composable
private fun <T> AlignmentGroup(
    options: List<T>,
    selected: T?,
    radio: Boolean,
    onClick: (T) -> Unit,
    icon: (T) -> DrawableResource,
    contentDescription: (T) -> String,
    interactionSources: Map<T, MutableInteractionSource>,
    compact: Boolean,
    enabled: Boolean,
    disabled: Set<T>,
) {
    val enabledFlags = options.map { enabled && it !in disabled }
    val selectedIndex = options.indexOf(selected)
    var current by remember { mutableIntStateOf(focusableSegment(enabledFlags, selectedIndex)) }
    var moved by remember { mutableStateOf(false) }
    val requesters = remember(options) { options.map { FocusRequester() } }
    LaunchedEffect(selectedIndex) { if (selectedIndex >= 0) current = selectedIndex }
    LaunchedEffect(current) {
        if (moved) requesters.getOrNull(current)?.requestFocus()
        moved = false
    }
    val focusIndex = if (enabledFlags.getOrElse(current) { false }) {
        current
    } else {
        focusableSegment(enabledFlags, selectedIndex)
    }
    val trackShape = RoundedCornerShape(AlignmentButtonDefaults.TrackRadius)
    Row(
        modifier = Modifier
            .onPreviewKeyEvent { event ->
                val step = arrowStep(event.key)
                if (event.type != KeyEventType.KeyDown || step == 0) return@onPreviewKeyEvent false
                nextEnabledSegment(enabledFlags, focusIndex, step)?.let {
                    moved = true
                    current = it
                    if (radio) onClick(options[it])
                }
                true
            }
            .background(HosannaTheme.colors.trackColor(enabled), trackShape)
            .border(SegmentedButtonDefaults.TrackEdge, HosannaTheme.colors.outlineVariant, trackShape)
            .padding(SegmentedButtonDefaults.TrackPadding)
            .then(if (radio) Modifier.selectableGroup() else Modifier),
        horizontalArrangement = Arrangement.spacedBy(SegmentedButtonDefaults.TrackGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { index, option ->
            AlignmentSegment(
                selected = index == selectedIndex,
                radio = radio,
                onClick = { onClick(option) },
                icon = icon(option),
                contentDescription = contentDescription(option),
                interactionSource = interactionSources.getValue(option),
                compact = compact,
                enabled = enabledFlags[index],
                focusable = index == focusIndex,
                requester = requesters[index],
            )
        }
    }
}

@Composable
private fun AlignmentSegment(
    selected: Boolean,
    radio: Boolean,
    onClick: () -> Unit,
    icon: DrawableResource,
    contentDescription: String,
    interactionSource: MutableInteractionSource,
    compact: Boolean,
    enabled: Boolean,
    focusable: Boolean,
    requester: FocusRequester,
) {
    val metrics = AlignmentButtonDefaults.metrics(compact)
    val hovered by interactionSource.collectIsHoveredAsState()
    val pressed by interactionSource.collectIsPressedAsState()
    val focused by interactionSource.collectIsFocusedAsState()
    val state = buttonState(enabled, false, hovered, pressed, focused)
    val colors = HosannaTheme.colors.segmentColors(selected, state)
    val container = remember(selected) { Animatable(colors.container) }
    LaunchedEffect(colors.container) {
        container.animateTo(colors.container, tween(ButtonFoundationDefaults.TRANSITION_MILLIS, easing = EaseOut))
    }
    val shape = RoundedCornerShape(AlignmentButtonDefaults.SegmentRadius)
    val click = if (radio) {
        Modifier.selectable(selected, interactionSource, null, enabled, Role.RadioButton, onClick)
    } else {
        Modifier.clickable(interactionSource, null, enabled, role = Role.Button, onClick = onClick)
    }
    Box(
        modifier = Modifier
            .focusRequester(requester)
            .focusProperties { canFocus = focusable }
            .hosannaElevation(segmentLevel(selected, state), shape, HosannaTheme.elevation)
            .then(colors.ring?.let { Modifier.focusRing(it, AlignmentButtonDefaults.SegmentRadius) } ?: Modifier)
            .size(metrics.width, metrics.height)
            .background(container.value, shape)
            .then(click)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painterResource(icon),
            null,
            Modifier.size(metrics.icon),
            colorFilter = ColorFilter.tint(colors.content),
        )
    }
}
