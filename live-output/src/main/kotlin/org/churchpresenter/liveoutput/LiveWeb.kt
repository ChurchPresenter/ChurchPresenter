package org.churchpresenter.liveoutput

import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
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

    /**
     * The live page the Web tab draws and steers, while one is in use: the one in the layout the
     * Web tab's button says ([webMobile]), or the other when no output shows that one.
     */
    val offscreenPage: State<OffscreenPage?>

    /** Whether the Web tab's Desktop/Mobile button says mobile; outputs that follow it show that page. */
    val webMobile: State<Boolean>

    fun setWebsiteUrl(url: String)
    fun setWebPageTitle(title: String)
    fun setWebSnapshot(bitmap: ImageBitmap?)
    fun setLiveBrowser(browser: CefBrowser?)

    /** The Web tab's Desktop/Mobile button: what the outputs that follow it show. */
    fun setWebMobile(mobile: Boolean)

    /** The off-screen page in the [mobile] layout, while a surface shows it. */
    fun offscreenPageFor(mobile: Boolean): OffscreenPage?

    /**
     * A surface starts showing the off-screen page in the [mobile] layout: the first opens it. Pair
     * each with [releaseOffscreenPage]; the page closes a moment after the last one lets go, so a
     * surface replaced in the same frame keeps the page running rather than reloading it. Each
     * layout is a page of its own, open only while some surface wants it.
     */
    fun useOffscreenPage(mobile: Boolean = false)
    fun releaseOffscreenPage(mobile: Boolean = false)
}

/**
 * [LiveWeb]'s state. [offscreen] says whether the engine renders windowless, and [openPage] opens
 * the off-screen page (null when it cannot); both are seams for tests.
 */
internal class LiveWebState(
    private val context: PresenterContext,
    offscreen: Boolean = CefManager.offscreen,
    private val openPage: (String, PageCallbacks) -> OffscreenPage? = { url, callbacks ->
        OffscreenPage.open(url, callbacks.onUrlChanged, callbacks.onTitleChanged, mobileMode = callbacks.mobileMode)
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

    private val _webMobile = mutableStateOf(false)
    override val webMobile: State<Boolean> = _webMobile

    private val desktop = PageSlot(mobile = false)
    private val mobile = PageSlot(mobile = true)

    override val offscreenPage: State<OffscreenPage?> = derivedStateOf { primary() }

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

    override fun setWebMobile(mobile: Boolean) {
        if (_webMobile.value == mobile) return
        _webMobile.value = mobile
        steerPrimary()
    }

    override fun offscreenPageFor(mobile: Boolean): OffscreenPage? = slot(mobile).page.value

    override fun useOffscreenPage(mobile: Boolean) {
        val slot = slot(mobile)
        slot.users++
        slot.closeTimer.stop()
        if (slot.page.value != null || !_offscreenWebAvailable.value) return
        val page = openPage(
            _websiteUrl.value,
            PageCallbacks(
                // Only the page the Web tab steers moves the address; the other follows it.
                onUrlChanged = { url -> if (primary() === slot.page.value) setWebsiteUrl(url) },
                onTitleChanged = { title -> if (primary() === slot.page.value) setWebPageTitle(title) },
                mobileMode = { mobile },
            ),
        )
        if (page == null) {
            _offscreenWebAvailable.value = false
            return
        }
        slot.page.value = page
        _webSnapshot.value = null
        steerPrimary()
    }

    override fun releaseOffscreenPage(mobile: Boolean) {
        val slot = slot(mobile)
        slot.users = (slot.users - 1).coerceAtLeast(0)
        if (slot.users == 0) slot.closeTimer.restart()
    }

    /** Closes the [mobile] page if no surface took it back since the last one let go; run by its close timer. */
    internal fun closeUnusedPage(mobile: Boolean = false) {
        val slot = slot(mobile)
        if (slot.users > 0) return
        val page = slot.page.value ?: return
        slot.page.value = null
        if (_liveBrowser.value === page.browser) setLiveBrowser(null)
        page.close()
        steerPrimary()
    }

    private fun slot(mobile: Boolean) = if (mobile) this.mobile else desktop

    /** The page the Web tab steers: the one in its button's layout, or the other when that is not open. */
    private fun primary(): OffscreenPage? =
        slot(_webMobile.value).page.value ?: slot(!_webMobile.value).page.value

    /** Points the live browser at [primary], when the outputs draw off screen. */
    private fun steerPrimary() {
        val page = primary() ?: return
        setLiveBrowser(page.browser)
    }

    /** One layout's page, how many surfaces show it, and the timer that closes it once none do. */
    private inner class PageSlot(mobile: Boolean) {
        val page = mutableStateOf<OffscreenPage?>(null)
        var users = 0
        val closeTimer = Timer(CLOSE_AFTER_MS) { closeUnusedPage(mobile) }.apply { isRepeats = false }
    }

    private companion object {
        const val CLOSE_AFTER_MS = 500
    }
}

/** What a live page tells the app (its own navigation) and asks it (whether it is the mobile page). */
internal class PageCallbacks(
    val onUrlChanged: (String) -> Unit,
    val onTitleChanged: (String) -> Unit,
    val mobileMode: () -> Boolean,
)
