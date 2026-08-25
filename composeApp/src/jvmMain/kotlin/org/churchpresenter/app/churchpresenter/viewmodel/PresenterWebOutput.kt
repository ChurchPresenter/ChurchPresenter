package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.web.WebOutput

/**
 * `:composeApp`'s implementation of [WebOutput], over [PresenterManager].
 *
 * A pass-through with no state of its own: the address, the title, the snapshot and the live
 * browser all already live on `PresenterManager`, and going live is the `Presenting` enum the web
 * module cannot see. Anything that needs remembering belongs there or in the tab, not here.
 *
 * Mirrors `PresenterAnnouncementsOutput`, `PresenterQaOutput` and `PresenterSttOutput`.
 */
class PresenterWebOutput(
    private val presenterManager: PresenterManager,
    private val presenting: (Presenting) -> Unit,
) : WebOutput {

    override val url: String get() = presenterManager.websiteUrl.value

    override val title: String get() = presenterManager.webPageTitle.value

    override val isLive: Boolean get() = presenterManager.presentingMode.value == Presenting.WEBSITE

    override val snapshot: ImageBitmap? get() = presenterManager.webSnapshot.value

    override val liveBrowser: CefBrowser? get() = presenterManager.liveBrowser.value

    override fun setUrl(url: String) = presenterManager.setWebsiteUrl(url)

    override fun setTitle(title: String) = presenterManager.setWebPageTitle(title)

    override fun setSnapshot(bitmap: ImageBitmap?) = presenterManager.setWebSnapshot(bitmap)

    override fun goLive() = presenting(Presenting.WEBSITE)
}
