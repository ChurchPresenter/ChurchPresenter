package org.churchpresenter.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.network.CefRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What the browser's callbacks do with what Chromium tells them.
 *
 * These handlers used to be `object :` expressions buried inside `EmbeddedWebView`, constructible
 * only once a real browser existed — so nothing about them was ever exercised. They are now named
 * factories, which is the whole reason this file can exist.
 *
 * The decisions themselves matter during a service: a popup that is not cancelled opens a window
 * the audience sees and the operator cannot reach, and an address change from a subframe (an advert,
 * an embedded video) must not overwrite the address bar with the advert's URL.
 */
class CefHandlerTest {

    private fun frame(isMain: Boolean) = mockk<CefFrame>().also { every { it.isMain } returns isMain }

    // ── displayHandler ──────────────────────────────────────────────────────────

    @Test
    fun `an address change on the main frame is reported`() {
        var seen: String? = null
        displayHandler(onUrlChanged = { seen = it }, onTitleChanged = null)
            .onAddressChange(mockk(relaxed = true), frame(isMain = true), "https://example.com")

        assertEquals("https://example.com", seen)
    }

    @Test
    fun `an address change on a subframe is ignored`() {
        var seen: String? = null
        displayHandler(onUrlChanged = { seen = it }, onTitleChanged = null)
            .onAddressChange(mockk(relaxed = true), frame(isMain = false), "https://ads.example")

        // An advert iframe navigating must not rewrite what the operator sees as the page address.
        assertEquals(null, seen)
    }

    @Test
    fun `a title change is reported`() {
        var seen: String? = null
        displayHandler(onUrlChanged = null, onTitleChanged = { seen = it })
            .onTitleChange(mockk(relaxed = true), "Sunday Notices")

        assertEquals("Sunday Notices", seen)
    }

    @Test
    fun `a title change with no listener is dropped`() {
        displayHandler(onUrlChanged = null, onTitleChanged = null)
            .onTitleChange(mockk(relaxed = true), "Sunday Notices")
    }

    // ── popupCancellingHandler ──────────────────────────────────────────────────

    @Test
    fun `a popup is cancelled and its target loaded in the current browser`() {
        val browser = mockk<CefBrowser>(relaxed = true)

        val cancelled = popupCancellingHandler()
            .onBeforePopup(browser, mockk(relaxed = true), "https://popup.example", null)

        assertTrue(cancelled, "the popup window itself must never open")
        verify { browser.loadURL("https://popup.example") }
    }

    @Test
    fun `a popup with no target yet is cancelled without navigating anywhere`() {
        val browser = mockk<CefBrowser>(relaxed = true)

        // `window.open()` with no URL: there is nothing to go to, and navigating to "" would blank
        // the page the audience is looking at.
        val cancelled = popupCancellingHandler().onBeforePopup(browser, mockk(relaxed = true), null, null)

        assertTrue(cancelled)
        verify(exactly = 0) { browser.loadURL(any()) }
    }

    @Test
    fun `a popup with a blank target is also cancelled without navigating`() {
        val browser = mockk<CefBrowser>(relaxed = true)

        val cancelled = popupCancellingHandler().onBeforePopup(browser, mockk(relaxed = true), "   ", null)

        assertTrue(cancelled)
        verify(exactly = 0) { browser.loadURL(any()) }
    }

    // ── mobileUserAgentHandler ──────────────────────────────────────────────────

    private fun resourceHandler(mobile: Boolean) =
        mobileUserAgentHandler { mobile }.getResourceRequestHandler(
            null, null, null, false, false, null, null,
        )

    @Test
    fun `the request handler hands back a resource handler`() {
        assertNotNull(resourceHandler(mobile = true))
    }

    @Test
    fun `with mobile emulation on the user agent is rewritten`() {
        val request = mockk<CefRequest>(relaxed = true)

        val handled = resourceHandler(mobile = true).onBeforeResourceLoad(null, null, request)

        assertTrue(!handled, "the request continues; only its headers changed")
        verify { request.setHeaderByName("User-Agent", any(), true) }
    }

    @Test
    fun `with mobile emulation off the request is left alone`() {
        val request = mockk<CefRequest>(relaxed = true)

        val handled = resourceHandler(mobile = false).onBeforeResourceLoad(null, null, request)

        assertTrue(!handled)
        verify(exactly = 0) { request.setHeaderByName("User-Agent", any(), any()) }
    }

    @Test
    fun `a request that is not there at all is survived`() {
        // CEF passes null for requests it has already handled; the header rewrite has to cope.
        resourceHandler(mobile = true).onBeforeResourceLoad(null, null, null)
    }
}
