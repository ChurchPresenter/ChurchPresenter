package org.churchpresenter.web

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WebNavControllerTest {

    @Test
    fun `with no browser attached, navigation is a safe no-op`() {
        val controller = WebNavController()
        controller.goBack()
        controller.goForward()
        assertFalse(controller.canGoBack())
        assertFalse(controller.canGoForward())
    }

    @Test
    fun `setMobileEmulation toggles mobileMode with no browser attached`() {
        val controller = WebNavController()
        assertFalse(controller.mobileMode)

        controller.setMobileEmulation(true)
        assertTrue(controller.mobileMode)

        controller.setMobileEmulation(false)
        assertFalse(controller.mobileMode)
    }

    @Test
    fun `mobileMode can be set directly`() {
        val controller = WebNavController()
        controller.mobileMode = true
        assertTrue(controller.mobileMode)
    }

    @Test
    fun `the mobile user agent identifies as an iPhone Safari`() {
        assertTrue(WebNavController.MOBILE_USER_AGENT.contains("iPhone"))
        assertTrue(WebNavController.MOBILE_USER_AGENT.contains("Safari"))
    }

    @Test
    fun `with a browser attached, navigation delegates to it`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        every { browser.canGoBack() } returns true
        every { browser.canGoForward() } returns true
        val controller = WebNavController().apply { this.browser = browser }

        assertTrue(controller.canGoBack())
        assertTrue(controller.canGoForward())
        controller.goBack()
        controller.goForward()

        verify { browser.goBack() }
        verify { browser.goForward() }
    }

    @Test
    fun `setMobileEmulation reloads an attached browser`() {
        val browser = mockk<CefBrowser>(relaxed = true)
        val controller = WebNavController().apply { this.browser = browser }

        controller.setMobileEmulation(true)

        assertTrue(controller.mobileMode)
        verify { browser.reload() }
    }
}
