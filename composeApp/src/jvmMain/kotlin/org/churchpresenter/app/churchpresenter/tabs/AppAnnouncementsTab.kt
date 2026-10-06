package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.announcements.AnnouncementsTab
import org.churchpresenter.profiles.PreviewOutputPicker
import org.churchpresenter.profiles.rememberPreviewOutput
import org.churchpresenter.profiles.stageMonitorScreenIndices
import org.churchpresenter.liveoutput.PresenterManager
import org.churchpresenter.settings.AnnouncementsSettings
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting

/**
 * The `:announcements` tab with the app's own parts filled in: the live output through
 * [PresenterManager.announcementsOutput], the screens that are stage monitors, and which output the
 * preview stands for, with the picker for it.
 */
@Composable
fun AppAnnouncementsTab(
    appSettings: AppSettings,
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    presenterManager: PresenterManager,
    onAddToSchedule: ((settings: AnnouncementsSettings) -> Unit)?,
    onSavePreset: ((settings: AnnouncementsSettings) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    AnnouncementsTab(
        modifier = modifier,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        output = presenterManager.announcementsOutput,
        onAddToSchedule = onAddToSchedule,
        onSavePreset = onSavePreset,
        previewOutput = rememberPreviewOutput(
            appSettings, Constants.PREVIEW_TAB_ANNOUNCEMENTS, Presenting.ANNOUNCEMENTS
        ),
        stageMonitorScreens = stageMonitorScreenIndices(appSettings.projectionSettings),
        outputPicker = {
            PreviewOutputPicker(
                settings = appSettings,
                tabId = Constants.PREVIEW_TAB_ANNOUNCEMENTS,
                mode = Presenting.ANNOUNCEMENTS,
                onSettingsChange = onSettingsChange,
            )
        },
    )
}
