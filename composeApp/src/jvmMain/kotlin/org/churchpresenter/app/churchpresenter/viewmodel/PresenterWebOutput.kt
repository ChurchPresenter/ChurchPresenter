package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.WebOutput

/**
 * [PresenterManager] as the `:web` tab sees it: every call goes straight through.
 *
 * A separate class rather than `PresenterManager : WebOutput`, for the same reason as
 * [PresenterSlidesOutput]: the manager's own declaration stays untouched.
 */
class PresenterWebOutput(private val manager: PresenterManager) : WebOutput {
    override fun isLive(mode: Presenting): Boolean = manager.isLive(mode)
    override val websiteUrl: State<String> get() = manager.websiteUrl
    override val webPageTitle: State<String> get() = manager.webPageTitle
    override val liveBrowser: State<CefBrowser?> get() = manager.liveBrowser
    override val webSnapshot: State<ImageBitmap?> get() = manager.webSnapshot

    override fun setPresentingMode(mode: Presenting) = manager.setPresentingMode(mode)
    override fun setWebsiteUrl(url: String) = manager.setWebsiteUrl(url)
    override fun setWebPageTitle(title: String) = manager.setWebPageTitle(title)
    override fun setWebSnapshot(bitmap: ImageBitmap?) = manager.setWebSnapshot(bitmap)
}
