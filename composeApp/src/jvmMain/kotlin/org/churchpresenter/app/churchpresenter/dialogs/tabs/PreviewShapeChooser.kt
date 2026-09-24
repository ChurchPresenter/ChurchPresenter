package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.output_profile_shape_custom
import churchpresenter.composeapp.generated.resources.output_profile_shape_height
import churchpresenter.composeapp.generated.resources.output_profile_shape_ratio
import churchpresenter.composeapp.generated.resources.output_profile_shape_resolution
import churchpresenter.composeapp.generated.resources.output_profile_shape_tooltip
import churchpresenter.composeapp.generated.resources.output_profile_shape_width
import org.churchpresenter.app.churchpresenter.composables.LocalSegmentedButtonTone
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonTone
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

/** The value the Custom segment stands for among the preset names. */
private const val CUSTOM = "CUSTOM"

private val SHAPE_SEGMENT_HEIGHT = 28.dp
private val MODE_SEGMENT_WIDTH = 82.dp
private val FIELD_WIDTH = 86.dp

/**
 * The preview's shape: five presets and a Custom that takes either a ratio or an exact size.
 *
 * Stored on the profile as `previewWidth`/`previewHeight` -- a preset as its own size, a ratio at
 * [RATIO_STORED_HEIGHT] tall, a resolution exactly as typed -- so a settings file written before
 * this control needs no migration, and one written by it reads back the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviewShapeChooser(
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    segmentWidth: Dp,
) {
    val preset = PreviewShapePreset.matching(profile.previewWidth, profile.previewHeight)
    // Custom can be picked while the stored size is still a preset -- the fields then open on that
    // size, ready to be changed -- so "is Custom open" is not the same question as "is it a preset".
    var customPicked by remember(profile.id) { mutableStateOf(false) }
    val customShown = customPicked || preset == null
    var mode by remember(profile.id) {
        mutableStateOf(
            if (preset == null && profile.previewHeight != RATIO_STORED_HEIGHT) CustomShapeMode.RESOLUTION
            else CustomShapeMode.RATIO,
        )
    }
    fun store(width: Int, height: Int) {
        onProfileChange(profile.copy(previewWidth = width, previewHeight = height))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        TooltipBox(
            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
            tooltip = { PlainTooltip { Text(stringResource(Res.string.output_profile_shape_tooltip)) } },
            state = rememberTooltipState(),
        ) {
            CompositionLocalProvider(LocalSegmentedButtonTone provides SegmentedButtonTone.ACCENT) {
                SegmentedButton(
                    items = PreviewShapePreset.entries.map {
                        SegmentedButtonItem(it.name, it.label, testTag = previewShapeTag(it.name))
                    } + SegmentedButtonItem(
                        CUSTOM,
                        stringResource(Res.string.output_profile_shape_custom),
                        testTag = previewShapeTag(CUSTOM),
                    ),
                    selectedValue = preset?.takeUnless { customShown }?.name ?: CUSTOM,
                    onValueChange = { picked ->
                        if (picked == CUSTOM) {
                            customPicked = true
                        } else {
                            customPicked = false
                            val chosen = PreviewShapePreset.valueOf(picked)
                            store(chosen.width, chosen.height)
                        }
                    },
                    buttonWidth = segmentWidth,
                    buttonHeight = SHAPE_SEGMENT_HEIGHT,
                    fontSize = MaterialTheme.typography.labelSmall.fontSize,
                )
            }
        }
        if (customShown) {
            CustomShapeRow(
                profile = profile,
                mode = mode,
                onModeChange = { mode = it },
                onStore = ::store,
            )
        }
    }
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
