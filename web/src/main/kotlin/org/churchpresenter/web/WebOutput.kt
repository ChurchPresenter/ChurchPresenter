package org.churchpresenter.web

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.models.Presenting

/**
 * What the Web tab needs from the live output: the page on screen, its title, the live browser
 * itself and the snapshot the preview mirrors.
 *
 * The app's `PresenterWebOutput` is the one implementation, passing every call to
 * `PresenterManager`. The tab takes this rather than the manager itself so the module never reaches
 * into the app, and so a test can hand the tab a fake that records what it was told.
 */
interface WebOutput {
    val presentingMode: State<Presenting>
    val websiteUrl: State<String>
    val webPageTitle: State<String>

    /** The browser the output window is showing, once it has one. */
    val liveBrowser: State<CefBrowser?>

    /** The last frame captured from [liveBrowser], for the preview's mirror. */
    val webSnapshot: State<ImageBitmap?>

    fun setPresentingMode(mode: Presenting)
    fun setWebsiteUrl(url: String)
    fun setWebPageTitle(title: String)
    fun setWebSnapshot(bitmap: ImageBitmap?)
}
