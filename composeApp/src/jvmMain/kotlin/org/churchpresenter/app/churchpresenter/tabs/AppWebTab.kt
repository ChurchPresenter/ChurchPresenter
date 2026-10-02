package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import org.churchpresenter.app.churchpresenter.composables.PreviewOutputPicker
import org.churchpresenter.app.churchpresenter.composables.rememberPreviewOutput
import org.churchpresenter.app.churchpresenter.viewmodel.PresenterManager
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.utils.Constants
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.presenter.CefManager
import org.churchpresenter.web.tabs.WebTab

/**
 * The `:web` tab with the app's own parts filled in: the live output through
 * [PresenterManager.webOutput], and the preview output the operator picked, both its shape and its
 * picker. Takes what [WebTab] takes otherwise.
 */
@Composable
fun AppWebTab(
    modifier: Modifier = Modifier,
    presenterManager: PresenterManager? = null,
    selectedWebsiteItem: ScheduleItem.WebsiteItem? = null,
    selectedWebsiteItemVersion: Int = 0,
    appSettings: AppSettings = AppSettings(),
    onSettingsChange: ((AppSettings) -> AppSettings) -> Unit = {},
    onAddToSchedule: ((url: String, title: String) -> Unit)? = null,
    onUpdateScheduleTitle: ((url: String, title: String) -> Unit)? = null,
    cefInitialized: Boolean = CefManager.initialized,
    cefMacOsUnsupported: Boolean = CefManager.macOsUnsupported,
    cefBlockedByPolicy: Boolean = CefManager.blockedByPolicy,
) {
    // Recomputed as the settings change, not cached once: a keyless `remember` here meant a
    // projector plugged in mid-service never reached the preview. It also sizes a real JCEF native
    // viewport, so the wrong shape lays the page out differently from the way it will go out.
    val previewOutput = rememberPreviewOutput(appSettings, Constants.PREVIEW_TAB_WEB, Presenting.WEBSITE)
    WebTab(
        modifier = modifier,
        output = presenterManager?.webOutput,
        selectedWebsiteItem = selectedWebsiteItem,
        selectedWebsiteItemVersion = selectedWebsiteItemVersion,
        appSettings = appSettings,
        onSettingsChange = onSettingsChange,
        onAddToSchedule = onAddToSchedule,
        onUpdateScheduleTitle = onUpdateScheduleTitle,
        cefInitialized = cefInitialized,
        cefMacOsUnsupported = cefMacOsUnsupported,
        cefBlockedByPolicy = cefBlockedByPolicy,
        previewAspectRatio = previewOutput.size.aspectRatio,
        outputPicker = { pickerModifier ->
            PreviewOutputPicker(
                settings = appSettings,
                tabId = Constants.PREVIEW_TAB_WEB,
                mode = Presenting.WEBSITE,
                onSettingsChange = onSettingsChange,
                modifier = pickerModifier,
            )
        },
    )
}
