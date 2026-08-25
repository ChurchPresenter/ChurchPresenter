package org.churchpresenter.web

import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest

// ── The CEF handlers, as functions ──────────────────────────────────────────────────────────────
//
// These used to be `object :` expressions inside `EmbeddedWebView`, which meant they could only be
// constructed after `CefManager.createClient()` returned — that is, only with Chromium running, and
// so never in a test. Each one is a handful of lines that decide something (is this the main frame,
// is there a popup target, is mobile emulation on) and the deciding is already delegated to
// `handleAddressChange`, `handlePopupTarget` and `applyMobileUserAgent`. Naming them lets a test
// build one and call it, which is the same shape as `:stt-tab`'s socket seam.

/** Forwards address and title changes from the browser to [onUrlChanged] and [onTitleChanged]. */
internal fun displayHandler(
    onUrlChanged: ((String) -> Unit)?,
    onTitleChanged: ((String) -> Unit)?,
): CefDisplayHandlerAdapter = object : CefDisplayHandlerAdapter() {
    override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
        handleAddressChange(frame, url, onUrlChanged)
    }

    override fun onTitleChange(browser: CefBrowser, title: String) {
        onTitleChanged?.invoke(title)
    }
}

/**
 * Cancels popups and loads their target in the current browser instead.
 *
 * A `target="_blank"` link would otherwise open a second window the audience can see and the
 * operator cannot reach.
 */
internal fun popupCancellingHandler(): CefLifeSpanHandlerAdapter = object : CefLifeSpanHandlerAdapter() {
    override fun onBeforePopup(
        browser: CefBrowser,
        frame: CefFrame,
        targetUrl: String?,
        targetFrameName: String?,
    ): Boolean {
        handlePopupTarget(browser, targetUrl)
        return true // cancel the popup
    }
}

/** Rewrites the User-Agent on every request while [mobileMode] reports mobile emulation is on. */
internal fun mobileUserAgentHandler(mobileMode: () -> Boolean): CefRequestHandlerAdapter =
    object : CefRequestHandlerAdapter() {
        override fun getResourceRequestHandler(
            browser: CefBrowser?,
            frame: CefFrame?,
            request: CefRequest?,
            isNavigation: Boolean,
            isDownload: Boolean,
            requestInitiator: String?,
            disableDefaultHandling: BoolRef?,
        ): CefResourceRequestHandler = object : CefResourceRequestHandlerAdapter() {
            override fun onBeforeResourceLoad(
                browser: CefBrowser?,
                frame: CefFrame?,
                request: CefRequest?,
            ): Boolean {
                applyMobileUserAgent(mobileMode(), request)
                return false
            }
        }
    }
