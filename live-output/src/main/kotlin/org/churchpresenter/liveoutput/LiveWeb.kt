package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.models.Presenting
import org.churchpresenter.web.offscreen.OffscreenPage
import org.churchpresenter.web.presenter.CefManager
import javax.swing.Timer

/** The web page on the outputs: its address, its title, a picture of it and the browser showing it. */
interface LiveWeb {
    val websiteUrl: State<String>
    val webPageTitle: State<String>

    /**
     * A periodically-updated picture of the live page -- what the preview panel and the off-screen
     * outputs mirror, without a second browser of their own. Only the windowed browser takes it; the
     * off-screen page's [offscreenPage] frames are drawn instead.
     */
    val webSnapshot: State<ImageBitmap?>

    /** The output's live browser, which the Web tab forwards input to. */
    val liveBrowser: State<CefBrowser?>

    /**
     * Whether the outputs draw the live page off screen: the engine started windowless and no page
     * has failed to start this run. When it goes false, every output falls back to a windowed browser.
     */
    val offscreenWebAvailable: State<Boolean>

    /** The live page every output draws, while one is in use; see [useOffscreenPage]. */
    val offscreenPage: State<OffscreenPage?>

    fun setWebsiteUrl(url: String)
    fun setWebPageTitle(title: String)
    fun setWebSnapshot(bitmap: ImageBitmap?)
    fun setLiveBrowser(browser: CefBrowser?)

    /**
     * A surface starts showing the off-screen page: the first opens it. Pair each with
     * [releaseOffscreenPage]; the page closes a moment after the last one lets go, so a surface
     * replaced in the same frame keeps the page running rather than reloading it.
     */
    fun useOffscreenPage()
    fun releaseOffscreenPage()
}

/**
 * [LiveWeb]'s state. [offscreen] says whether the engine renders windowless, and [openPage] opens
 * the off-screen page (null when it cannot); both are seams for tests.
 */
internal class LiveWebState(
    private val context: PresenterContext,
    offscreen: Boolean = CefManager.offscreen,
    private val openPage: (String, (String) -> Unit, (String) -> Unit) -> OffscreenPage? = { url, onUrl, onTitle ->
        OffscreenPage.open(url, onUrl, onTitle)
    },
) : LiveWeb {

    private val _websiteUrl = mutableStateOf("")
    override val websiteUrl: State<String> = _websiteUrl

    private val _webPageTitle = mutableStateOf("")
    override val webPageTitle: State<String> = _webPageTitle

    private val _webSnapshot = mutableStateOf<ImageBitmap?>(null)
    override val webSnapshot: State<ImageBitmap?> = _webSnapshot

    private val _liveBrowser = mutableStateOf<CefBrowser?>(null)
    override val liveBrowser: State<CefBrowser?> = _liveBrowser

    private val _offscreenWebAvailable = mutableStateOf(offscreen)
    override val offscreenWebAvailable: State<Boolean> = _offscreenWebAvailable

    private val _offscreenPage = mutableStateOf<OffscreenPage?>(null)
    override val offscreenPage: State<OffscreenPage?> = _offscreenPage

    private var pageUsers = 0
    private val closeTimer = Timer(CLOSE_AFTER_MS) { closeUnusedPage() }.apply { isRepeats = false }

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

    override fun useOffscreenPage() {
        pageUsers++
        closeTimer.stop()
        if (_offscreenPage.value != null || !_offscreenWebAvailable.value) return
        val page = openPage(_websiteUrl.value, ::setWebsiteUrl, ::setWebPageTitle)
        if (page == null) {
            _offscreenWebAvailable.value = false
            return
        }
        _offscreenPage.value = page
        _webSnapshot.value = null
        setLiveBrowser(page.browser)
    }

    override fun releaseOffscreenPage() {
        pageUsers = (pageUsers - 1).coerceAtLeast(0)
        if (pageUsers == 0) closeTimer.restart()
    }

    /** Closes the page if no surface took it back since the last one let go; run by the close timer. */
    internal fun closeUnusedPage() {
        if (pageUsers > 0) return
        val page = _offscreenPage.value ?: return
        _offscreenPage.value = null
        if (_liveBrowser.value === page.browser) setLiveBrowser(null)
        page.close()
    }

    private companion object {
        const val CLOSE_AFTER_MS = 500
    }
}
