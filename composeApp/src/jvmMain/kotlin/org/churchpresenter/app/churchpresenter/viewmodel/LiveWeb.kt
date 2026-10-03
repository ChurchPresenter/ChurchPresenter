package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.models.Presenting

/** The web page on the outputs: its address, its title, a picture of it and the browser showing it. */
interface LiveWeb {
    val websiteUrl: State<String>
    val webPageTitle: State<String>

    /**
     * A periodically-updated picture of the live page -- what the preview panel and the off-screen
     * outputs mirror, without a second browser of their own.
     */
    val webSnapshot: State<ImageBitmap?>

    /** The output's live browser, which the Web tab forwards input to. */
    val liveBrowser: State<CefBrowser?>

    fun setWebsiteUrl(url: String)
    fun setWebPageTitle(title: String)
    fun setWebSnapshot(bitmap: ImageBitmap?)
    fun setLiveBrowser(browser: CefBrowser?)
}

internal class LiveWebState(private val context: PresenterContext) : LiveWeb {

    private val _websiteUrl = mutableStateOf("")
    override val websiteUrl: State<String> = _websiteUrl

    private val _webPageTitle = mutableStateOf("")
    override val webPageTitle: State<String> = _webPageTitle

    private val _webSnapshot = mutableStateOf<ImageBitmap?>(null)
    override val webSnapshot: State<ImageBitmap?> = _webSnapshot

    private val _liveBrowser = mutableStateOf<CefBrowser?>(null)
    override val liveBrowser: State<CefBrowser?> = _liveBrowser

    override fun setWebsiteUrl(url: String) {
        _websiteUrl.value = url
        context.notify(Presenting.WEBSITE)
    }

    override fun setWebPageTitle(title: String) {
        _webPageTitle.value = title
        context.notify(Presenting.WEBSITE)
    }

    override fun setWebSnapshot(bitmap: ImageBitmap?) {
        _webSnapshot.value = bitmap
    }

    override fun setLiveBrowser(browser: CefBrowser?) {
        _liveBrowser.value = browser
    }
}
