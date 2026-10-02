package org.churchpresenter.web.presenter

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLifeSpanHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.handler.CefResourceRequestHandler
import org.cef.handler.CefResourceRequestHandlerAdapter
import org.cef.misc.BoolRef
import org.cef.network.CefRequest
import org.churchpresenter.sharedui.composables.outputCursorScript
import java.awt.Component
import java.awt.Rectangle
import java.awt.image.BufferedImage

/** Reports the main frame's address and the page's title as they change; see [handleAddressChange]. */
internal class PageDisplayHandler(
    private val onUrlChanged: ((String) -> Unit)?,
    private val onTitleChanged: ((String) -> Unit)?,
) : CefDisplayHandlerAdapter() {
    override fun onAddressChange(browser: CefBrowser, frame: CefFrame, url: String) {
        handleAddressChange(frame, url, onUrlChanged)
    }

    override fun onTitleChange(browser: CefBrowser, title: String) {
        onTitleChanged?.invoke(title)
    }
}

/** Opens what would have been a popup (a `target="_blank"` link) in the same browser, and cancels the popup. */
internal object PopupsInPlace : CefLifeSpanHandlerAdapter() {
    override fun onBeforePopup(
        browser: CefBrowser,
        frame: CefFrame,
        targetUrl: String?,
        targetFrameName: String?,
    ): Boolean {
        handlePopupTarget(browser, targetUrl)
        return true
    }
}

/** Tags every request with the mobile User-Agent while [mobileMode] says so; see [applyMobileUserAgent]. */
internal class MobileUserAgentHandler(private val mobileMode: () -> Boolean) : CefRequestHandlerAdapter() {
    private val resources = object : CefResourceRequestHandlerAdapter() {
        override fun onBeforeResourceLoad(browser: CefBrowser?, frame: CefFrame?, request: CefRequest?): Boolean {
            applyMobileUserAgent(mobileMode(), request)
            return false
        }
    }

    override fun getResourceRequestHandler(
        browser: CefBrowser?,
        frame: CefFrame?,
        request: CefRequest?,
        isNavigation: Boolean,
        isDownload: Boolean,
        requestInitiator: String?,
        disableDefaultHandling: BoolRef?,
    ): CefResourceRequestHandler = resources
}

/**
 * Puts the hidden-pointer style back on every main-frame load while [hideCursor] says so: a new page
 * starts without it.
 */
internal class CursorRestoringLoadHandler(private val hideCursor: () -> Boolean) : CefLoadHandlerAdapter() {
    override fun onLoadEnd(browser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
        if (frame.isMain && hideCursor()) {
            browser.executeJavaScript(outputCursorScript(hide = true), "", 0)
        }
    }
}

/**
 * A picture of [comp] where it sits on screen, taken by [capture] — or null while it is not on screen
 * with a real size, or when the capture fails (the window closing under it, say).
 */
internal fun captureSnapshot(comp: Component, capture: (Rectangle) -> BufferedImage): ImageBitmap? {
    if (comp.width <= 0 || comp.height <= 0 || !comp.isShowing) return null
    return runCatching {
        val loc = comp.locationOnScreen
        capture(Rectangle(loc.x, loc.y, comp.width, comp.height)).toComposeImageBitmap()
    }.getOrNull()
}
