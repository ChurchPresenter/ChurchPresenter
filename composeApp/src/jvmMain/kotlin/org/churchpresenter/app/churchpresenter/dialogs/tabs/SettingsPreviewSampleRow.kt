package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import churchpresenter.composeapp.generated.resources.Res
import churchpresenter.composeapp.generated.resources.preview_on_screen
import churchpresenter.composeapp.generated.resources.preview_on_screen_stop
import churchpresenter.composeapp.generated.resources.preview_sample
import churchpresenter.composeapp.generated.resources.preview_sample_long
import churchpresenter.composeapp.generated.resources.preview_sample_medium
import churchpresenter.composeapp.generated.resources.preview_sample_short
import org.churchpresenter.app.churchpresenter.composables.SegmentedButton
import org.churchpresenter.app.churchpresenter.composables.SegmentedButtonItem
import org.jetbrains.compose.resources.stringResource

/** The width each of the three sample buttons is given. */
private val SAMPLE_BUTTON_WIDTH = 74.dp
private val SAMPLE_BUTTON_HEIGHT = 30.dp

/** Test tag on the on-screen preview toggle, so a UI test can drive it. */
internal const val PREVIEW_ON_SCREEN_TAG = "previewOnScreenToggle"

/**
 * The strip under the preview: which sample it draws, and whether it is also on the real output.
 *
 * Under and not beside -- the row above the preview already carries the output switch, the
 * translation chips and the scope note, and on a 1366x768 laptop there is nothing left of it.
 */
@Composable
internal fun SettingsPreviewSampleRow(
    slot: PreviewSampleSlot,
    onSlotChange: (PreviewSampleSlot) -> Unit,
    onScreen: Boolean,
    onScreenChange: (Boolean) -> Unit,
    onScreenEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = stringResource(Res.string.preview_sample),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedButton(
            items = listOf(
                SegmentedButtonItem(PreviewSampleSlot.SHORT, stringResource(Res.string.preview_sample_short)),
                SegmentedButtonItem(PreviewSampleSlot.MEDIUM, stringResource(Res.string.preview_sample_medium)),
                SegmentedButtonItem(PreviewSampleSlot.LONG, stringResource(Res.string.preview_sample_long)),
            ),
            selectedValue = slot,
            onValueChange = onSlotChange,
            buttonWidth = SAMPLE_BUTTON_WIDTH,
            buttonHeight = SAMPLE_BUTTON_HEIGHT,
            fontSize = MaterialTheme.typography.labelSmall.fontSize,
        )
        Spacer(Modifier.weight(1f))
        TextButton(
            onClick = { onScreenChange(!onScreen) },
            enabled = onScreenEnabled,
            modifier = Modifier.testTag(PREVIEW_ON_SCREEN_TAG),
        ) {
            Text(
                text = stringResource(
                    if (onScreen) Res.string.preview_on_screen_stop else Res.string.preview_on_screen,
                ),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}
