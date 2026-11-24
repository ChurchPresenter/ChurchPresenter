package org.churchpresenter.hosanna.composables.button

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
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

enum class TextAlignment { Left, Center, Right, Justify, Top, Middle, Bottom }

enum class LayerAlignment { Left, Center, Right, Top, Middle, Bottom }

internal val HorizontalTextAlignments =
    listOf(TextAlignment.Left, TextAlignment.Center, TextAlignment.Right, TextAlignment.Justify)
internal val VerticalTextAlignments = listOf(TextAlignment.Top, TextAlignment.Middle, TextAlignment.Bottom)
internal val HorizontalLayerAlignments = listOf(LayerAlignment.Left, LayerAlignment.Center, LayerAlignment.Right)
internal val VerticalLayerAlignments = listOf(LayerAlignment.Top, LayerAlignment.Middle, LayerAlignment.Bottom)

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
        horizontalArrangement = Arrangement.spacedBy(IconSegmentDefaults.GroupGap),
        verticalArrangement = Arrangement.spacedBy(IconSegmentDefaults.GroupGap),
    ) {
        val groups = listOf(HorizontalTextAlignments to horizontal, VerticalTextAlignments to vertical)
        groups.forEach { (options, chosen) ->
            IconSegmentGroup(
                options = options,
                role = SegmentRole.Radio,
                value = { if (it == chosen) ToggleableState.On else ToggleableState.Off },
                onClick = onAlignmentChange,
                icon = { it.icon() },
                contentDescription = contentDescription,
                interactionSources = interactionSources,
                metrics = IconSegmentDefaults.metrics(compact),
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
        horizontalArrangement = Arrangement.spacedBy(IconSegmentDefaults.GroupGap),
        verticalArrangement = Arrangement.spacedBy(IconSegmentDefaults.GroupGap),
    ) {
        listOf(HorizontalLayerAlignments, VerticalLayerAlignments).forEach { options ->
            IconSegmentGroup(
                options = options,
                role = SegmentRole.Action,
                value = { ToggleableState.Off },
                onClick = onAlign,
                icon = { it.icon() },
                contentDescription = contentDescription,
                interactionSources = interactionSources,
                metrics = IconSegmentDefaults.metrics(compact),
                enabled = enabled,
                disabled = emptySet(),
            )
        }
    }
}
