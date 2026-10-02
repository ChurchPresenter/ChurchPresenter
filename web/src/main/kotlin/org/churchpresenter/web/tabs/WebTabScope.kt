package org.churchpresenter.web.tabs

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.presenter.WebNavController
import org.churchpresenter.web.WebOutput
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier

/** What the Web tab remembers while it is composed: the address, the page, and the preview's mode. */
@Stable
internal class WebTabState(savedUrl: String, savedTitle: String) {
    /** Current URL typed by the user. */
    var urlInput by mutableStateOf(savedUrl.ifBlank { "https://" })

    /** URL actually loaded in the preview / sent live. */
    var liveUrl by mutableStateOf(savedUrl)
    var pageTitle by mutableStateOf(savedTitle)

    /** Toggle between screenshot mirror (matched layout) and interactive local browser. */
    var useInteractivePreview by mutableStateOf(false)

    /** Zoom level (0.0 = 100%, each ±1.0 ≈ 1.2x scale change). */
    var zoomLevel by mutableStateOf(0.0)
    var isMobileView by mutableStateOf(false)

    // Local buffer for the "Type to page" field shown in live mirror mode.
    // We cannot forward raw keystrokes to the live CefBrowser on macOS/Linux —
    // native event routing drops injected events when the browser window isn't
    // the OS key window. Instead we diff this buffer on every change and inject
    // the delta into the live page via CefBrowser.executeJavaScript, which is an
    // in-process Chromium API that works identically on all platforms.
    var typeBuffer by mutableStateOf("")
}

/** Everything the Web tab's pieces read, for one composition, and the navigation they share. */
@Suppress("LongParameterList")
internal class WebTabScope(
    val output: WebOutput?,
    val appSettings: AppSettings,
    val onSettingsChange: ((AppSettings) -> AppSettings) -> Unit,
    val onAddToSchedule: ((url: String, title: String) -> Unit)?,
    val onUpdateScheduleTitle: ((url: String, title: String) -> Unit)?,
    val state: WebTabState,
    /** Whether the website is what is on screen, as of this composition. */
    val isLive: Boolean,
    val navController: WebNavController,
    val previewAspectRatio: Float,
    val outputPicker: @Composable (Modifier) -> Unit,
    /** Whether a screen beyond the operator's own is attached, as Go Live needs. */
    val hasSecondaryDisplay: Boolean = false,
) {
    var urlInput by state::urlInput
    var liveUrl by state::liveUrl
    var pageTitle by state::pageTitle
    var useInteractivePreview by state::useInteractivePreview
    var zoomLevel by state::zoomLevel
    var isMobileView by state::isMobileView
    var typeBuffer by state::typeBuffer
    val bookmarks get() = appSettings.webBookmarks
    val currentUrlNormalised get() = normaliseUrl(urlInput)
    val isBookmarked get() = bookmarks.any { it.url == currentUrlNormalised }

    // Keep the presenter in sync whenever the preview navigates to a new page
    fun onPreviewNavigated(newUrl: String) {
        urlInput = newUrl
        liveUrl = newUrl
        output?.setWebsiteUrl(newUrl)
        // Directly navigate the presenter browser so it updates immediately
        if (isLive) {
            output?.liveBrowser?.value?.loadURL(newUrl)
        }
    }

    fun onTitleChanged(title: String) {
        pageTitle = title
        output?.setWebPageTitle(title)
        // Update the schedule item title if it was added before the page finished loading
        if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, title)
    }

    fun applyZoom(level: Double) {
        zoomLevel = level
        val browser = if (isLive && !useInteractivePreview)
            output?.liveBrowser?.value else navController.browser
        browser?.setZoomLevel(level)
    }

    fun onMobileToggle(mobile: Boolean) {
        isMobileView = mobile
        navController.setMobileEmulation(mobile)
        // Also toggle on the live browser if presenting
        if (isLive) {
            output?.liveBrowser?.value?.let { liveBrowser ->
                // The live browser uses a separate NavController, so override UA + reload directly
                liveBrowser.reload()
            }
        }
    }
}

/** Leaving live mode, a scheduled website, and following the presenter's own navigation. */
@Composable
internal fun WebTabScope.WebTabEffects(
    selectedWebsiteItem: ScheduleItem.WebsiteItem?,
    selectedWebsiteItemVersion: Int,
) {
    // Clear the buffer whenever we leave live mode so stale text doesn't
    // re-inject when the user goes live again on a different page.
    LaunchedEffect(isLive) { if (!isLive) typeBuffer = "" }

    // Clear snapshot when no longer live
    LaunchedEffect(isLive) {
        if (!isLive) output?.setWebSnapshot(null)
    }

    // When a schedule item selects this tab, restore its URL and go live
    LaunchedEffect(selectedWebsiteItem, selectedWebsiteItemVersion) {
        selectedWebsiteItem?.let { item ->
            urlInput = item.url
            liveUrl = item.url
            pageTitle = item.title
            output?.setWebsiteUrl(item.url)
            output?.setWebPageTitle(item.title)
            output?.setPresentingMode(Presenting.WEBSITE)
        }
    }

    // Sync URL bar from presenter when the presenter navigates (Mirror mode clicks)
    val presenterUrl = output?.websiteUrl?.value ?: ""
    val presenterTitle = output?.webPageTitle?.value ?: ""
    LaunchedEffect(presenterUrl) {
        if (isLive && !useInteractivePreview && presenterUrl.isNotBlank()) {
            urlInput = presenterUrl
            liveUrl = presenterUrl
        }
    }
    LaunchedEffect(presenterTitle) {
        if (isLive && !useInteractivePreview && presenterTitle.isNotBlank()) {
            pageTitle = presenterTitle
            if (liveUrl.isNotBlank()) onUpdateScheduleTitle?.invoke(liveUrl, presenterTitle)
        }
    }

    // Apply zoom level when presenter browser becomes available
    val liveBrowserRef = output?.liveBrowser?.value
    LaunchedEffect(liveBrowserRef) {
        if (liveBrowserRef != null && isLive) {
            liveBrowserRef.setZoomLevel(zoomLevel)
        }
    }
}
