package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.lowerthird.LowerThirdTab
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The `:lower-third` tab with the app's own part filled in: which output the preview stands for,
 * and the picker for it.
 */
@Composable
fun AppLowerThirdTab(
    appSettings: AppSettings,
    selectedLowerThirdItem: ScheduleItem.LowerThirdItem?,
    selectedLowerThirdItemVersion: Int,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    onAddToSchedule: (presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) -> Unit,
    onGoLive: (
        jsonContent: String,
        pauseAtFrame: Boolean,
        pauseFrame: Float,
        pauseDurationMs: Long,
        presetName: String,
    ) -> Unit,
    onOpenLottieGen: (outputDir: String, onFileSaved: (() -> Unit)?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LowerThirdTab(
        modifier = modifier,
        appSettings = appSettings,
        selectedLowerThirdItem = selectedLowerThirdItem,
        selectedLowerThirdItemVersion = selectedLowerThirdItemVersion,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = onAddToSchedule,
        onGoLive = onGoLive,
        onOpenLottieGen = onOpenLottieGen,
        previewOutput = rememberPreviewOutput(appSettings, Constants.PREVIEW_TAB_LOWER_THIRD, Presenting.LOWER_THIRD),
        outputPicker = { pickerModifier ->
            PreviewOutputPicker(
                settings = appSettings,
                tabId = Constants.PREVIEW_TAB_LOWER_THIRD,
                mode = Presenting.LOWER_THIRD,
                onSettingsChange = onSettingsChange,
                modifier = pickerModifier,
            )
        },
    )
}
