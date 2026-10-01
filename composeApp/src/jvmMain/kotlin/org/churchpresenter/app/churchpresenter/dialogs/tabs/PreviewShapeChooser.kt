package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.canvas_size_custom
import churchpresenter.composeapp.generated.resources.output_profile_shape_height
import churchpresenter.composeapp.generated.resources.output_profile_shape_ratio
import churchpresenter.composeapp.generated.resources.output_profile_shape_resolution
import churchpresenter.composeapp.generated.resources.output_profile_shape_tooltip
import churchpresenter.composeapp.generated.resources.output_profile_shape_width
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.OutputProfile
import org.churchpresenter.theme.AppShape
import org.churchpresenter.theme.components.KeyButton
import org.jetbrains.compose.resources.stringResource

/** The value the Custom entry stands for among the preset names. */
private const val CUSTOM = "CUSTOM"

private val SHAPE_SEGMENT_HEIGHT = 28.dp
private val MODE_SEGMENT_WIDTH = 82.dp
private val FIELD_WIDTH = 86.dp
private val TRIGGER_HEIGHT = 28.dp

/**
 * The preview's shape, held for one profile: which entry the menu shows, whether Custom's fields
 * are open, and how a custom shape is typed.
 *
 * Custom can be picked while the stored size is still a preset -- the fields then open on that size,
 * ready to be changed -- so "is Custom open" is not the same question as "is it a preset", and has to
 * be remembered here rather than derived from the stored size.
 */
internal class PreviewShapeState(initialMode: CustomShapeMode) {
    var customPicked by mutableStateOf(false)
    var mode by mutableStateOf(initialMode)
}

/** A [PreviewShapeState] for [profile], forgotten when another profile is opened. */
@Composable
internal fun rememberPreviewShapeState(profile: OutputProfile): PreviewShapeState {
    val preset = PreviewShapePreset.matching(profile.previewWidth, profile.previewHeight)
    return remember(profile.id) {
        PreviewShapeState(
            if (preset == null && profile.previewHeight != RATIO_STORED_HEIGHT) CustomShapeMode.RESOLUTION
            else CustomShapeMode.RATIO,
        )
    }
}

/** Whether the Custom fields are showing for [profile]: picked, or a stored size that is no preset. */
internal fun PreviewShapeState.customShown(profile: OutputProfile): Boolean =
    customPicked || PreviewShapePreset.matching(profile.previewWidth, profile.previewHeight) == null

/**
 * The shape menu of the preview's toolbar: the five presets and Custom, on one small key that names
 * the shape the preview is drawn at.
 *
 * Stored on the profile as `previewWidth`/`previewHeight` -- a preset as its own size, a ratio at
 * [RATIO_STORED_HEIGHT] tall, a resolution exactly as typed -- so a settings file written before
 * this control needs no migration, and one written by it reads back the same way. Custom's fields
 * are [PreviewCustomShapeFields], drawn by the toolbar under its row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewShapeMenu(
    profile: OutputProfile,
    state: PreviewShapeState,
    onProfileChange: (OutputProfile) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(stringResource(Res.string.output_profile_shape_tooltip)) } },
        state = rememberTooltipState(),
    ) {
        Box {
            KeyButton(
                onClick = { open = true },
                shape = AppShape(7.dp),
                contentPadding = PaddingValues(start = 10.dp, end = 6.dp),
                modifier = Modifier.height(TRIGGER_HEIGHT).testTag(PREVIEW_SHAPE_TRIGGER_TAG),
            ) {
                Text(
                    text = previewShapeLabel(profile.previewWidth, profile.previewHeight),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Icon(Icons.Filled.UnfoldMore, contentDescription = null, modifier = Modifier.size(14.dp))
            }
            DropdownMenu(
                expanded = open,
                onDismissRequest = { open = false },
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                val current = PreviewShapePreset.matching(profile.previewWidth, profile.previewHeight)
                PreviewShapePreset.entries.forEach { preset ->
                    ShapeMenuItem(
                        label = preset.label,
                        selected = preset == current && !state.customShown(profile),
                        tag = previewShapeTag(preset.name),
                    ) {
                        state.customPicked = false
                        onProfileChange(profile.copy(previewWidth = preset.width, previewHeight = preset.height))
                        open = false
                    }
                }
                ShapeMenuItem(
                    label = stringResource(Res.string.canvas_size_custom),
                    selected = state.customShown(profile),
                    tag = previewShapeTag(CUSTOM),
                ) {
                    state.customPicked = true
                    open = false
                }
            }
        }
    }
}

@Composable
private fun ShapeMenuItem(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        onClick = onClick,
        modifier = Modifier.testTag(tag),
    )
}

/** Custom's fields -- a ratio or an exact size -- written straight onto the profile. */
@Composable
internal fun PreviewCustomShapeFields(
    profile: OutputProfile,
    state: PreviewShapeState,
    onProfileChange: (OutputProfile) -> Unit,
) {
    CustomShapeRow(
        profile = profile,
        mode = state.mode,
        onModeChange = { state.mode = it },
        onStore = { width, height -> onProfileChange(profile.copy(previewWidth = width, previewHeight = height)) },
    )
}

@Composable
private fun CustomShapeRow(
    profile: OutputProfile,
    mode: CustomShapeMode,
    onModeChange: (CustomShapeMode) -> Unit,
    onStore: (Int, Int) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(CustomShapeMode.RATIO, stringResource(Res.string.output_profile_shape_ratio)),
                SegmentedButtonItem(
                    CustomShapeMode.RESOLUTION,
                    stringResource(Res.string.output_profile_shape_resolution),
                ),
            ),
            selectedValue = mode,
            onValueChange = onModeChange,
            buttonWidth = MODE_SEGMENT_WIDTH,
            buttonHeight = SHAPE_SEGMENT_HEIGHT,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        when (mode) {
            CustomShapeMode.RATIO -> {
                // Held here rather than re-derived from the stored size on every pass: 21:9 is kept
                // as 2520×1080, which reduces to 7:3, and a field that rewrote "21" to "7" under the
                // operator's cursor would be fighting them.
                val start = reducedRatio(profile.previewWidth, profile.previewHeight)
                    .takeIf { (w, h) -> w in PREVIEW_RATIO_RANGE && h in PREVIEW_RATIO_RANGE }
                    ?: DEFAULT_RATIO
                var ratio by remember(profile.id) { mutableStateOf(start) }
                NumberControl(
                    label = stringResource(Res.string.output_profile_shape_width),
                    value = ratio.first,
                    onValueChange = { v ->
                        ratio = v to ratio.second
                        val (w, h) = sizeForRatio(ratio.first, ratio.second)
                        onStore(w, h)
                    },
                    range = PREVIEW_RATIO_RANGE,
                    width = FIELD_WIDTH,
                )
                Text(":", color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberControl(
                    label = stringResource(Res.string.output_profile_shape_height),
                    value = ratio.second,
                    onValueChange = { v ->
                        ratio = ratio.first to v
                        val (w, h) = sizeForRatio(ratio.first, ratio.second)
                        onStore(w, h)
                    },
                    range = PREVIEW_RATIO_RANGE,
                    width = FIELD_WIDTH,
                )
            }
            CustomShapeMode.RESOLUTION -> {
                NumberControl(
                    label = stringResource(Res.string.output_profile_shape_width),
                    value = profile.previewWidth,
                    onValueChange = { v -> onStore(v, profile.previewHeight) },
                    range = PREVIEW_SIDE_RANGE,
                    width = FIELD_WIDTH,
                )
                Text("×", color = MaterialTheme.colorScheme.onSurfaceVariant)
                NumberControl(
                    label = stringResource(Res.string.output_profile_shape_height),
                    value = profile.previewHeight,
                    onValueChange = { v -> onStore(profile.previewWidth, v) },
                    range = PREVIEW_SIDE_RANGE,
                    width = FIELD_WIDTH,
                )
            }
        }
    }
}

/** Test handle for one segment of the shape chooser -- a preset's name, or `CUSTOM`. */
internal fun previewShapeTag(name: String): String = "preview_shape_$name"

/** Test handle for the key that opens the shape menu. */
internal const val PREVIEW_SHAPE_TRIGGER_TAG = "preview_shape_trigger"
