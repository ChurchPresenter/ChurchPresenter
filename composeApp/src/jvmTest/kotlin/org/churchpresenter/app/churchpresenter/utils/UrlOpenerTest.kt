package org.churchpresenter.app.churchpresenter.utils

import org.churchpresenter.app.churchpresenter.composables.MAC_CAMERA_PRIVACY_URI
import org.churchpresenter.app.churchpresenter.composables.WINDOWS_CAMERA_PRIVACY_URI
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opening a link when AWT will not. Both the desktop call and the shell fallback are parameters, so
 * every branch is reachable without a display.
 */
class UrlOpenerTest {

    private val url = "https://example.org/consent"

    // ── Which command each platform falls back to ─────────────────────────────

    @Test
    fun `macOS opens with open`() {
        assertEquals(listOf(listOf("open", url)), UrlOpener.fallbackCommands("Mac OS X", url))
    }

    @Test
    fun `darwin is macOS too`() {
        assertEquals(listOf(listOf("open", url)), UrlOpener.fallbackCommands("Darwin", url))
    }

    @Test
    fun `Windows goes through the protocol handler`() {
        assertEquals(
            listOf(listOf("rundll32", "url.dll,FileProtocolHandler", url)),
            UrlOpener.fallbackCommands("Windows 11", url),
        )
    }

    @Test
    fun `anything else tries xdg-open first`() {
        assertEquals(listOf("xdg-open", url), UrlOpener.fallbackCommands("Linux", url).first())
    }

    @Test
    fun `the platform is matched whatever case it is reported in`() {
        assertEquals(
            UrlOpener.fallbackCommands("Mac OS X", url),
            UrlOpener.fallbackCommands("MAC OS X", url),
        )
    }

    @Test
    fun `an unknown platform is treated as a unix desktop rather than given up on`() {
        val commands = UrlOpener.fallbackCommands("SunOS", url)
        assertTrue(commands.isNotEmpty())
        assertEquals("xdg-open", commands.first().first())
    }

    @Test
    fun `every command carries the url it was asked to open`() {
        for (os in listOf("Mac OS X", "Windows 11", "Linux")) {
            for (command in UrlOpener.fallbackCommands(os, url)) {
                assertTrue(url in command, "$os: $command must name the url")
            }
        }
    }

    // ── Opening ───────────────────────────────────────────────────────────────

    @Test
    fun `a blank url opens nothing`() {
        var tried = false
        val opened = UrlOpener.open(
            url = "",
            browseSupported = { tried = true; true },
            browse = { tried = true },
            exec = { tried = true; true },
        )
        assertFalse(opened)
        assertFalse(tried, "nothing may be launched for a blank url")
    }

    @Test
    fun `a supported desktop is used and nothing is shelled out`() {
        var browsed: URI? = null
        var execCalls = 0
        val opened = UrlOpener.open(
            url = url,
            browseSupported = { true },
            browse = { browsed = it },
            exec = { execCalls++; true },
        )
        assertTrue(opened)
        assertEquals(URI(url), browsed)
        assertEquals(0, execCalls, "the shell is the fallback, not the first move")
    }

    @Test
    fun `an unsupported desktop falls through to the shell`() {
        var ran: List<String>? = null
        val opened = UrlOpener.open(
            url = url,
            osName = "Mac OS X",
            browseSupported = { false },
            browse = { error("must not be called") },
            exec = { ran = it; true },
        )
        assertTrue(opened)
        assertEquals(listOf("open", url), ran)
    }

    @Test
    fun `a desktop that throws falls through rather than escaping`() {
        var ran: List<String>? = null
        val opened = UrlOpener.open(
            url = url,
            osName = "Windows 11",
            browseSupported = { true },
            browse = { throw UnsupportedOperationException("The BROWSE action is not supported") },
            exec = { ran = it; true },
        )
        assertTrue(opened, "a throwing desktop is a reason to try the shell, not to give up")
        assertEquals(listOf("rundll32", "url.dll,FileProtocolHandler", url), ran)
    }

    @Test
    fun `a support check that throws is treated as unsupported`() {
        val opened = UrlOpener.open(
            url = url,
            osName = "Mac OS X",
            browseSupported = { error("headless") },
            browse = { error("must not be called") },
            exec = { true },
        )
        assertTrue(opened)
    }

    @Test
    fun `each fallback is tried until one takes it`() {
        val tried = mutableListOf<List<String>>()
        val opened = UrlOpener.open(
            url = url,
            osName = "Linux",
            browseSupported = { false },
            browse = { error("must not be called") },
            exec = { tried += it; false },
        )
        assertFalse(opened, "nothing accepted the url")
        assertEquals(
            UrlOpener.fallbackCommands("Linux", url),
            tried,
            "every command must have been offered the url",
        )
    }

    @Test
    fun `nothing after the command that succeeds is run`() {
        val tried = mutableListOf<List<String>>()
        UrlOpener.open(
            url = url,
            osName = "Linux",
            browseSupported = { false },
            browse = { error("must not be called") },
            exec = { tried += it; true },
        )
        assertEquals(1, tried.size, "the first command took it: $tried")
    }

    @Test
    fun `a url that cannot be parsed still reaches the shell`() {
        var ran: List<String>? = null
        val bad = "not a uri at all"
        val opened = UrlOpener.open(
            url = bad,
            osName = "Mac OS X",
            browseSupported = { true },
            browse = { URI(bad) },
            exec = { ran = it; true },
        )
        assertTrue(opened)
        assertEquals(listOf("open", bad), ran)
    }

    // ── What is not a browser link ────────────────────────────────────────────

    @Test
    fun `an OS settings uri never reaches AWT`() {
        // Measured on macOS 26: `Desktop.browse` hands this to Safari, which opens a blank tab and
        // asks "Do you want to allow this website to open System Settings?", then returns normally
        // — so the button that exists for a blocked camera opened a webpage and nothing else.
        val execed = mutableListOf<List<String>>()

        val opened = UrlOpener.open(
            MAC_CAMERA_PRIVACY_URI,
            osName = "Mac OS X",
            browseSupported = { true },
            browse = { error("a settings uri is not a browser link") },
            exec = { execed += it; true },
        )

        assertTrue(opened)
        assertEquals(listOf(listOf("open", MAC_CAMERA_PRIVACY_URI)), execed)
    }

    @Test
    fun `the windows settings uri takes the same route`() {
        val execed = mutableListOf<List<String>>()

        val opened = UrlOpener.open(
            WINDOWS_CAMERA_PRIVACY_URI,
            osName = "Windows 11",
            browseSupported = { true },
            browse = { error("a settings uri is not a browser link") },
            exec = { execed += it; true },
        )

        assertTrue(opened)
        assertEquals(
            listOf(listOf("rundll32", "url.dll,FileProtocolHandler", WINDOWS_CAMERA_PRIVACY_URI)),
            execed,
        )
    }

    @Test
    fun `only http and https are the browser's`() {
        assertTrue(UrlOpener.isWebUrl("https://churchpresenter.org/wiki"))
        assertTrue(UrlOpener.isWebUrl("http://192.168.1.4:8080"))
        assertTrue(UrlOpener.isWebUrl("HTTPS://churchpresenter.org"), "the scheme is case-insensitive")
        assertFalse(UrlOpener.isWebUrl(MAC_CAMERA_PRIVACY_URI))
        assertFalse(UrlOpener.isWebUrl(WINDOWS_CAMERA_PRIVACY_URI))
        assertFalse(UrlOpener.isWebUrl("churchpresenter.org"), "no scheme is not a web url")
    }
}
