package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.churchpresenter.strings.generated.resources.Res
import org.churchpresenter.strings.generated.resources.preview_background_actual
import org.churchpresenter.strings.generated.resources.preview_background_checker
import org.churchpresenter.strings.generated.resources.profile_value_off
import org.churchpresenter.strings.generated.resources.preview_sample_long
import org.churchpresenter.strings.generated.resources.preview_sample_medium
import org.churchpresenter.strings.generated.resources.preview_sample_short
import org.churchpresenter.strings.generated.resources.profile_preview_caption
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.churchpresenter.settings.OutputProfile
import org.jetbrains.compose.resources.stringResource

private val MODE_SEGMENT_WIDTH = 64.dp
private val SAMPLE_SEGMENT_WIDTH = 66.dp
private val TOOLBAR_SEGMENT_HEIGHT = 28.dp

/**
 * Everything that changes what the preview shows without changing the profile: the page it stands
 * for, its screen shape, what goes behind the text, and how much sample text it carries.
 *
 * One compact block rather than the three stacked rows it replaced -- a caption with the shape
 * beside it, and the two segmented choices on one line under them -- so the picture sits near the
 * top of the column instead of under a stack of controls. The shape is the only one of these
 * stored, and only as the profile's stand-in preview size; the other two are this session's
 * checking and are forgotten.
 *
 * [slot] is null on a page whose sample is one fixed piece -- a caption, a question, a card -- and
 * [backgroundMode] null on one with no background to show; each choice is left out where it has
 * nothing to change.
 */
@Composable
internal fun PreviewToolbar(
    pageLabel: String,
    profile: OutputProfile,
    onProfileChange: (OutputProfile) -> Unit,
    shapeState: PreviewShapeState,
    backgroundMode: PreviewBackgroundMode?,
    onBackgroundModeChange: (PreviewBackgroundMode) -> Unit,
    slot: PreviewSampleSlot?,
    onSlotChange: (PreviewSampleSlot) -> Unit,
    modifier: Modifier = Modifier,
    /** Keys that sit before the shape menu -- Larger, once there is a larger preview to open. */
    actions: @Composable RowScope.() -> Unit = {},
    /** Keys at the end of the Background / Sample row -- Reset positions, in the large preview. */
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            CustomizeCaption(stringResource(Res.string.profile_preview_caption, pageLabel))
            Spacer(modifier = Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
                PreviewShapeMenu(profile = profile, state = shapeState, onProfileChange = onProfileChange)
            }
        }
        if (shapeState.customShown(profile)) {
            PreviewCustomShapeFields(profile = profile, state = shapeState, onProfileChange = onProfileChange)
        }
        if (backgroundMode != null || slot != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (backgroundMode != null) {
                    SegmentedButton(
                        items = listOf(
                            SegmentedButtonItem(
                                PreviewBackgroundMode.ACTUAL,
                                stringResource(Res.string.preview_background_actual),
                                testTag = previewBackgroundTag(PreviewBackgroundMode.ACTUAL),
                            ),
                            SegmentedButtonItem(
                                PreviewBackgroundMode.OFF,
                                stringResource(Res.string.profile_value_off),
                                testTag = previewBackgroundTag(PreviewBackgroundMode.OFF),
                            ),
                            SegmentedButtonItem(
                                PreviewBackgroundMode.CHECKER,
                                stringResource(Res.string.preview_background_checker),
                                testTag = previewBackgroundTag(PreviewBackgroundMode.CHECKER),
                            ),
                        ),
                        selectedValue = backgroundMode,
                        onValueChange = onBackgroundModeChange,
                        buttonWidth = MODE_SEGMENT_WIDTH,
                        buttonHeight = TOOLBAR_SEGMENT_HEIGHT,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                    )
                }
                if (backgroundMode != null && slot != null) Spacer(modifier = Modifier.width(10.dp))
                if (slot != null) {
                    // How much text the picture stands in for. A layout that reads perfectly against
                    // one verse can overflow against a long one, and this is the only way to check
                    // that without putting the real thing live.
                    SegmentedButton(
                        items = PreviewSampleSlot.entries.map { entry ->
                            SegmentedButtonItem(
                                entry,
                                stringResource(
                                    when (entry) {
                                        PreviewSampleSlot.SHORT -> Res.string.preview_sample_short
                                        PreviewSampleSlot.MEDIUM -> Res.string.preview_sample_medium
                                        PreviewSampleSlot.LONG -> Res.string.preview_sample_long
                                    },
                                ),
                            )
                        },
                        selectedValue = slot,
                        onValueChange = onSlotChange,
                        buttonWidth = SAMPLE_SEGMENT_WIDTH,
                        buttonHeight = TOOLBAR_SEGMENT_HEIGHT,
                        fontSize = MaterialTheme.typography.labelSmall.fontSize,
                        modifier = Modifier.testTag(PREVIEW_SAMPLE_ROW_TAG),
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                trailing()
            }
        }
    }
}

/** Test handle for one of the Actual / Off / Checker segments. */
internal fun previewBackgroundTag(mode: PreviewBackgroundMode): String = "preview_background_${mode.name}"

/** Test handle for the Short / Medium / Long row. */
internal const val PREVIEW_SAMPLE_ROW_TAG = "preview_sample_row"
