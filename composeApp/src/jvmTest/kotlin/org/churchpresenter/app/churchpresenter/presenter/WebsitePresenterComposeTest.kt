@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.presenter

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.network.CefRequest
import org.churchpresenter.app.churchpresenter.utils.Constants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WebsitePresenterComposeTest {

    @Test
    fun `a main-frame address change is forwarded`() {
        val frame = mockk<CefFrame> { every { isMain } returns true }
        var forwarded: String? = null

        handleAddressChange(frame, "https://example.com/page", { forwarded = it })

        assertEquals("https://example.com/page", forwarded)
    }

    @Test
    fun `an iframe address change is not forwarded`() {
        val frame = mockk<CefFrame> { every { isMain } returns false }
        var forwarded: String? = null

        handleAddressChange(frame, "https://example.com/ad-frame", { forwarded = it })

        assertNull(forwarded)
    }

    @Test
    fun `a popup with a real target url is loaded in the current browser`() {
        val browser = mockk<CefBrowser>(relaxed = true)

        handlePopupTarget(browser, "https://example.com/popup")

        verify { browser.loadURL("https://example.com/popup") }
    }

    @Test
    fun `a popup with a blank or null target url is not loaded`() {
        val browser = mockk<CefBrowser>(relaxed = true)

        handlePopupTarget(browser, null)
        handlePopupTarget(browser, "")
        handlePopupTarget(browser, "   ")

        verify(exactly = 0) { browser.loadURL(any()) }
    }

    @Test
    fun `mobile emulation on sets the mobile user agent header`() {
        val request = mockk<CefRequest>(relaxed = true)

        applyMobileUserAgent(mobileModeEnabled = true, request = request)

        verify { request.setHeaderByName("User-Agent", WebNavController.MOBILE_USER_AGENT, true) }
    }

    @Test
    fun `mobile emulation off leaves the request untouched`() {
        val request = mockk<CefRequest>(relaxed = true)

        applyMobileUserAgent(mobileModeEnabled = false, request = request)

        verify(exactly = 0) { request.setHeaderByName(any(), any(), any()) }
    }

    @Test
    fun `a null request is a safe no-op even with mobile emulation on`() {
        applyMobileUserAgent(mobileModeEnabled = true, request = null)
    }

    @Test
    fun `EmbeddedWebView with a blank url renders nothing and does not crash`() = runComposeUiTest {
        setContent { EmbeddedWebView(url = "") }
    }

    @Test
    fun `EmbeddedWebView with no CefManager engine renders nothing and does not crash`() = runComposeUiTest {
        // CefManager.init() is never called in tests, so CefManager.initialized stays false and
        // this always takes the early-return path — proving that path is safe on its own.
        setContent { EmbeddedWebView(url = "https://example.com") }
    }

    @Test
    fun `rememberWebNavController does not crash and yields a usable controller`() = runComposeUiTest {
        lateinit var controller: WebNavController
        setContent { controller = rememberWebNavController() }
        controller.goBack()
    }

    @Test
    fun `WebsitePresenter in key mode is a plain white output`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp, 200.dp)) {
                WebsitePresenter(url = "https://example.com",
                    outputRole = Constants.OUTPUT_ROLE_KEY,
                    modifier = Modifier.testTag("ws"))
            }
        }
        val pixels = onNodeWithTag("ws").captureToImage().toPixelMap()
        assertColorAt(pixels, 100, 100, Color.White)
    }

    @Test
    fun `WebsitePresenter in normal mode with no audio device does not crash`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp, 200.dp)) {
                WebsitePresenter(url = "https://example.com", audioDeviceId = "")
            }
        }
    }

    @Test
    fun `WebsitePresenter in normal mode with an audio device does not crash`() = runComposeUiTest {
        setContent {
            Box(Modifier.size(200.dp, 200.dp)) {
                WebsitePresenter(url = "https://example.com", audioDeviceId = "some-sink")
            }
        }
    }

    @Test
    fun `the audio routing loop runs once the initial delay elapses`() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent {
            Box(Modifier.size(200.dp, 200.dp)) {
                WebsitePresenter(url = "https://example.com", audioDeviceId = "some-sink")
            }
        }
        mainClock.advanceTimeBy(2_100)
    }
}
