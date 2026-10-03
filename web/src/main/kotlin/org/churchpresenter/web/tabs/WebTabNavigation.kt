package org.churchpresenter.web.tabs

import org.cef.browser.CefBrowser
import org.churchpresenter.web.presenter.CefManager
import java.io.File

/** How far one press of zoom in or out moves the zoom level. */
internal const val ZOOM_STEP = 0.5

/**
 * The live window's browser while the tab steers it — live and mirroring — or null when the toolbar
 * steers the preview's own browser instead.
 */
private val WebTabScope.steeredLiveBrowser: CefBrowser?
    get() = output?.liveBrowser?.value?.takeIf { isLive && !useInteractivePreview }

/** Back, in whichever browser the toolbar steers. */
internal fun WebTabScope.goBack() {
    val live = steeredLiveBrowser
    if (live != null) live.goBack() else navController.goBack()
}

/** Forward, in whichever browser the toolbar steers. */
internal fun WebTabScope.goForward() {
    val live = steeredLiveBrowser
    if (live != null) live.goForward() else navController.goForward()
}

/** Reloads whichever browser the toolbar steers. */
internal fun WebTabScope.refresh() {
    val live = steeredLiveBrowser
    if (live != null) live.reload() else navController.browser?.reload()
}

/**
 * Empties the engine's disk cache, leaving the directory in place.
 *
 * [cacheDir] is asked of the engine rather than assumed to be under the home directory: on Windows
 * it prefers %ProgramData%, and this button used to clear an empty legacy folder there while the
 * live cache stayed put. Null before the engine has installed, when there is nothing to clear.
 */
internal fun clearWebCache(cacheDir: File? = CefManager.webviewCacheDir) {
    if (cacheDir == null) return
    if (cacheDir.exists()) cacheDir.deleteRecursively()
    cacheDir.mkdirs()
}

/** Zooms one step out, or in when [zoomIn]. */
internal fun WebTabScope.stepZoom(zoomIn: Boolean) {
    applyZoom(if (zoomIn) zoomLevel + ZOOM_STEP else zoomLevel - ZOOM_STEP)
}

/** Loads what is in the address bar, normalised, into the preview — and the live window while live. */
internal fun WebTabScope.submitUrl() {
    val url = normaliseUrl(urlInput)
    urlInput = url
    liveUrl = url
    output?.setWebsiteUrl(url)
    if (isLive) output?.liveBrowser?.value?.loadURL(url)
}

/** Switches the preview between mirroring the live window and browsing on its own. */
internal fun WebTabScope.toggleInteractivePreview() {
    useInteractivePreview = !useInteractivePreview
}
