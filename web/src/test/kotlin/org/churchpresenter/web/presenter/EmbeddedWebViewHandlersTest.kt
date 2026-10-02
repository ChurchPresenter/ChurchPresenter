package org.churchpresenter.web.presenter

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import me.friwi.jcefmaven.CefAppBuilder
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.network.CefRequest
import org.churchpresenter.sharedui.composables.outputCursorScript
import java.awt.Component
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What the embedded browser does with what Chromium tells it — addresses, titles, popups, requests,
 * finished loads — plus its snapshot capture and how the engine is configured before it is built.
 *
 * Chromium's own objects (a browser, a frame, a request) cannot be made without it, so they are
 * relaxed mocks, as in `WebNavControllerTest`.
 */
class EmbeddedWebViewHandlersTest {

    private val browser = mockk<CefBrowser>(relaxed = true)
    private fun frame(main: Boolean) = mockk<CefFrame> { every { isMain } returns main }
    private val tmp: File = Files.createTempDirectory("cef-config").toFile()

    @AfterTest
    fun cleanUp() {
        tmp.deleteRecursively()
    }

    // ── Address and title ───────────────────────────────────────────────────────────────────────

    @Test
    fun `the main frame's address and the title are reported, a sub-frame's address is not`() {
        val urls = mutableListOf<String>()
        val titles = mutableListOf<String>()
        val handler = PageDisplayHandler(onUrlChanged = { urls += it }, onTitleChanged = { titles += it })

        handler.onAddressChange(browser, frame(main = true), "https://a.org")
        handler.onAddressChange(browser, frame(main = false), "https://ads.example")
        handler.onTitleChange(browser, "A")

        assertEquals(listOf("https://a.org"), urls)
        assertEquals(listOf("A"), titles)
    }

    @Test
    fun `with nobody listening an address or a title goes nowhere`() {
        val handler = PageDisplayHandler(onUrlChanged = null, onTitleChanged = null)
        handler.onAddressChange(browser, frame(main = true), "https://a.org")
        handler.onTitleChange(browser, "A")
    }

    // ── Popups ──────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a popup opens in the same browser instead, and is always cancelled`() {
        assertTrue(PopupsInPlace.onBeforePopup(browser, frame(main = true), "https://b.org", null))
        assertTrue(PopupsInPlace.onBeforePopup(browser, frame(main = true), null, null))

        verify(exactly = 1) { browser.loadURL("https://b.org") }
    }

    // ── Requests ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `requests carry the mobile User-Agent only while mobile mode is on, and load normally`() {
        var mobile = false
        val handler = MobileUserAgentHandler { mobile }
        val request = mockk<CefRequest>(relaxed = true)
        val resources = handler.getResourceRequestHandler(browser, null, request, true, false, null, null)

        assertFalse(resources.onBeforeResourceLoad(browser, null, request), "false lets the load go ahead")
        verify(exactly = 0) { request.setHeaderByName(any(), any(), any()) }

        mobile = true
        resources.onBeforeResourceLoad(browser, null, request)
        verify { request.setHeaderByName("User-Agent", WebNavController.MOBILE_USER_AGENT, true) }
    }

    // ── Finished loads ──────────────────────────────────────────────────────────────────────────

    @Test
    fun `a finished main-frame load hides the pointer again while the output hides it`() {
        var hide = true
        val handler = CursorRestoringLoadHandler { hide }

        handler.onLoadEnd(browser, frame(main = true), 200)
        handler.onLoadEnd(browser, frame(main = false), 200)
        hide = false
        handler.onLoadEnd(browser, frame(main = true), 200)

        verify(exactly = 1) { browser.executeJavaScript(outputCursorScript(hide = true), "", 0) }
    }

    // ── Snapshots ───────────────────────────────────────────────────────────────────────────────

    /** A component on screen at (10, 20) while [showing] says so. */
    private class ScreenComponent(private val showing: Boolean, width: Int, height: Int) : Component() {
        init {
            setSize(width, height)
        }
        override fun isShowing() = showing
        override fun getLocationOnScreen() = Point(10, 20)
    }

    @Test
    fun `a snapshot captures exactly the component's area on screen`() {
        var asked: Rectangle? = null
        val image = captureSnapshot(ScreenComponent(showing = true, width = 4, height = 3)) {
            asked = it
            BufferedImage(it.width, it.height, BufferedImage.TYPE_INT_ARGB)
        }

        assertEquals(Rectangle(10, 20, 4, 3), asked)
        assertNotNull(image)
        assertEquals(4, image.width)
    }

    @Test
    fun `no snapshot is taken off screen, at no size, or when the capture fails`() {
        val never: (Rectangle) -> BufferedImage = { error("must not capture") }
        assertNull(captureSnapshot(ScreenComponent(showing = false, width = 4, height = 3), never))
        assertNull(captureSnapshot(ScreenComponent(showing = true, width = 0, height = 3), never))
        assertNull(captureSnapshot(ScreenComponent(showing = true, width = 4, height = 0), never))
        assertNull(captureSnapshot(ScreenComponent(showing = true, width = 4, height = 3), never))
    }

    // ── Configuring the engine ──────────────────────────────────────────────────────────────────

    @Test
    fun `the engine installs and caches under its root, with the GPU left on real hardware`() {
        val builder = CefAppBuilder()
        JcefInstall.configure(builder, tmp, dmiTexts = listOf("MacBookPro18,3", "Apple Inc."))

        assertTrue(File(tmp, "jcef").isDirectory)
        assertEquals(File(tmp, "webview-cache").absolutePath, builder.cefSettings.cache_path)
        assertFalse(builder.cefSettings.windowless_rendering_enabled)
        assertFalse("--disable-gpu" in builder.jcefArgs)
    }

    @Test
    fun `on a virtual machine the engine falls back to software rendering`() {
        val builder = CefAppBuilder()
        JcefInstall.configure(builder, tmp, dmiTexts = listOf("QEMU Standard PC"))

        assertTrue(builder.jcefArgs.containsAll(listOf("--disable-gpu", "--enable-unsafe-swiftshader")))
    }

    @Test
    fun `the DMI names are read where they exist, and skipped where they do not`() {
        File(tmp, "product_name").writeText("VirtualBox\n")

        assertEquals(listOf("VirtualBox"), JcefInstall.readDmiTexts(tmp))
        assertEquals(emptyList(), JcefInstall.readDmiTexts(File(tmp, "missing")))
    }
}
