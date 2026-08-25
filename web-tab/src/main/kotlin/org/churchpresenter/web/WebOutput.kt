package org.churchpresenter.web

import androidx.compose.ui.graphics.ImageBitmap
import org.cef.browser.CefBrowser

/**
 * What the Web tab needs from the screens, and nothing more.
 *
 * A wider port than the other tabs', and honestly so: this one does not merely put content on a
 * screen, it *shares a live browser* with it. When the page is live the tab's preview and the
 * output are the same `CefBrowser` — typing an address in the tab has to reach the browser the
 * audience is looking at — so the reference has to cross the boundary. Splitting that into
 * something narrower would mean two browsers and two page loads.
 *
 * [isLive] is a `Boolean` rather than the `Presenting` enum for the same reason as the other ports:
 * the tab never asks what is live when it is not the website. `:composeApp` implements this over
 * `PresenterManager` in `PresenterWebOutput`.
 */
interface WebOutput {

    /** The address the outputs are showing, which survives leaving and returning to the tab. */
    val url: String

    /** The title of that page, shown in the schedule and on the stage monitor. */
    val title: String

    /** Whether the website is what the outputs are currently showing. */
    val isLive: Boolean

    /**
     * The last still captured from the live page.
     *
     * The tab draws this behind its preview so switching back does not flash an empty panel while
     * Chromium re-paints.
     */
    val snapshot: ImageBitmap?

    /**
     * The browser the outputs are drawing, when the website is live.
     *
     * `null` when nothing is live, or when the output window has not built one yet. The tab
     * navigates *this* browser rather than its own so that what it previews is what is on screen.
     */
    val liveBrowser: CefBrowser?

    fun setUrl(url: String)

    fun setTitle(title: String)

    fun setSnapshot(bitmap: ImageBitmap?)

    /** Put the website on the screens. */
    fun goLive()
}
