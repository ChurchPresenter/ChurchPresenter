@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.presenter

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.v2.runComposeUiTest
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.cef.CefClient
import org.cef.browser.CefBrowser
import org.churchpresenter.sharedui.composables.LocalOutputCursorHidden
import org.churchpresenter.sharedui.composables.outputCursorScript
import java.awt.Component
import java.awt.Container
import java.awt.Point
import java.awt.Rectangle
import java.awt.image.BufferedImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * The embedded browser's life once the engine is up: making the browser, handing it out, wiring the
 * handlers, following the address, taking snapshots, and letting it all go again.
 *
 * The client and browser are relaxed mocks — neither can exist without Chromium — and the browser's
 * component stands in for its native canvas, drawn by a panel that only records it instead of the
 * `SwingPanel` a headless test cannot host.
 */
class EmbeddedBrowserTest {

    /** The browser's component: on screen at the origin, inside a parent it can be detached from. */
    private class BrowserComponent : Component() {
        init {
            setSize(8, 6)
        }
        override fun isShowing() = true
        override fun getLocationOnScreen() = Point(0, 0)
    }

    private val component = BrowserComponent().also { Container().add(it) }
    private val browser = mockk<CefBrowser>(relaxed = true) { every { uiComponent } returns component }
    private val client = mockk<CefClient>(relaxed = true) {
        every { createBrowser(any(), any(), any()) } returns browser
    }
    private var drawn: Component? = null

    /** Shows the browser for [url] while [shown], with [hideCursor] as the output's pointer setting. */
    private fun ComposeUiTest.show(
        url: () -> String,
        shown: () -> Boolean = { true },
        hideCursor: Boolean = false,
        navController: WebNavController? = null,
        onBrowserCreated: ((CefBrowser) -> Unit)? = null,
        onSnapshot: ((ImageBitmap) -> Unit)? = null,
        capture: ((Rectangle) -> BufferedImage)? = null,
        createClient: () -> CefClient? = { client },
    ) {
        setContent {
            if (shown()) {
                CompositionLocalProvider(LocalOutputCursorHidden provides hideCursor) {
                    EmbeddedBrowser(
                        url = url(),
                        modifier = Modifier,
                        onUrlChanged = null,
                        onTitleChanged = null,
                        onSnapshot = onSnapshot,
                        navController = navController,
                        onBrowserCreated = onBrowserCreated,
                        createClient = createClient,
                        capture = capture,
                        panel = { component, _ -> drawn = component },
                    )
                }
            }
        }
        waitForIdle()
    }

    @Test
    fun `with no client to be had nothing is drawn`() = runComposeUiTest {
        show(url = { "https://a.org" }, createClient = { null })

        assertNull(drawn)
    }

    @Test
    fun `a browser that cannot be created draws nothing rather than failing composition`() = runComposeUiTest {
        every { client.createBrowser(any(), any(), any()) } throws NullPointerException("N_CefHandle")
        show(url = { "https://a.org" })

        assertNull(drawn)
    }

    @Test
    fun `the browser is created for the address, handed out, drawn and wired`() = runComposeUiTest {
        val nav = WebNavController()
        var created: CefBrowser? = null
        show(url = { "https://a.org" }, navController = nav, onBrowserCreated = { created = it })

        verify { client.createBrowser("https://a.org", false, false) }
        assertSame(component, drawn)
        assertSame(browser, nav.browser)
        assertSame(browser, created)
        verify { browser.executeJavaScript(outputCursorScript(false), "", 0) }
        verify { client.addDisplayHandler(any<PageDisplayHandler>()) }
        verify { client.addLifeSpanHandler(PopupsInPlace) }
        verify { client.addRequestHandler(any<MobileUserAgentHandler>()) }
        verify { client.addLoadHandler(any<CursorRestoringLoadHandler>()) }
    }

    @Test
    fun `on an output that hides the pointer the page hides it too`() = runComposeUiTest {
        show(url = { "https://a.org" }, hideCursor = true)

        verify { browser.executeJavaScript(outputCursorScript(true), "", 0) }
    }

    @Test
    fun `a new address loads into the same browser, and the first one is not loaded twice`() = runComposeUiTest {
        var url by mutableStateOf("https://a.org")
        show(url = { url })
        verify(exactly = 0) { browser.loadURL(any()) }

        url = "https://b.org"
        waitForIdle()

        verify(exactly = 1) { client.createBrowser(any(), any(), any()) }
        verify { browser.loadURL("https://b.org") }
    }

    @Test
    fun `leaving lets go of the browser, its handlers and its component`() = runComposeUiTest {
        val nav = WebNavController()
        var shown by mutableStateOf(true)
        show(url = { "https://a.org" }, shown = { shown }, navController = nav)

        shown = false
        waitForIdle()

        assertNull(nav.browser)
        assertFalse(component.isVisible)
        assertNull(component.parent, "detached, so the native canvas cannot linger over Compose")
        verify { client.removeDisplayHandler() }
        verify { client.removeLifeSpanHandler() }
        verify { client.removeRequestHandler() }
        verify { client.removeLoadHandler() }
        verify { browser.close(true) }
        verify { client.dispose() }
    }

    @Test
    fun `an engine already shut down does not make leaving fail`() = runComposeUiTest {
        every { browser.close(any()) } throws IllegalStateException("CefApp was terminated")
        every { client.dispose() } throws IllegalStateException("CefApp was terminated")
        every { browser.uiComponent } returns component andThenThrows IllegalStateException("gone")
        var shown by mutableStateOf(true)
        show(url = { "https://a.org" }, shown = { shown })

        shown = false
        waitForIdle()

        assertTrue(component.isVisible, "the component could not be reached, so it was left as it was")
    }

    @Test
    fun `snapshots of the browser's area arrive while something wants them`() = runComposeUiTest {
        val snapshots = mutableListOf<ImageBitmap>()
        show(
            url = { "https://a.org" },
            onSnapshot = { snapshots += it },
            capture = { BufferedImage(it.width, it.height, BufferedImage.TYPE_INT_ARGB) },
        )

        // The capture runs on a Swing timer; wait for the frame itself rather than for time to pass.
        waitUntil("a snapshot to arrive", timeoutMillis = 2_000) { snapshots.isNotEmpty() }
        assertEquals(8, snapshots.first().width)
    }
}
