package org.churchpresenter.app.churchpresenter.utils

import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Opening a link on a desktop where AWT will not do it.
 *
 * `Desktop.getDesktop().browse` is only supported where AWT finds a freedesktop.org helper, so on a
 * Linux box without one it throws `UnsupportedOperationException: The BROWSE action is not supported
 * on the current platform!` — which is how the Planning Center consent page failed to open, taking
 * the connect flow down with it. Every decision here is a parameter so the fallback can be checked
 * without a display; only the one call that genuinely needs a desktop stays behind them.
 */
class UrlOpenerTest {

    private val url = "https://example.invalid/consent"

    @Test
    fun `AWT is used when it says it can browse`() {
        val browsed = mutableListOf<URI>()
        val execed = mutableListOf<List<String>>()

        val opened = UrlOpener.open(
            url,
            osName = "Linux",
            browseSupported = { true },
            browse = { browsed += it },
            exec = { execed += it; true },
        )

        assertTrue(opened)
        assertEquals(listOf(URI(url)), browsed)
        assertTrue(execed.isEmpty(), "the shell is the fallback, not the first choice")
    }

    @Test
    fun `a desktop that cannot browse falls back to the shell`() {
        val execed = mutableListOf<List<String>>()

        val opened = UrlOpener.open(
            url,
            osName = "Linux",
            browseSupported = { false },
            browse = { error("must not be called when unsupported") },
            exec = { execed += it; true },
        )

        assertTrue(opened)
        assertEquals(listOf(listOf("xdg-open", url)), execed)
    }

    @Test
    fun `AWT throwing is not the end of it`() {
        // isSupported answering true and browse throwing anyway is the exact reported shape.
        val execed = mutableListOf<List<String>>()

        val opened = UrlOpener.open(
            url,
            osName = "Linux",
            browseSupported = { true },
            browse = { throw UnsupportedOperationException("The BROWSE action is not supported") },
            exec = { execed += it; true },
        )

        assertTrue(opened, "a throwing AWT must still leave the operator a browser")
        assertEquals(listOf(listOf("xdg-open", url)), execed)
    }

    @Test
    fun `nothing working is answered, not thrown`() {
        // Every caller is a button in a dialog; a link that will not open is a disappointment.
        val opened = UrlOpener.open(
            url,
            osName = "Linux",
            browseSupported = { false },
            browse = {},
            exec = { false },
        )

        assertFalse(opened)
    }

    @Test
    fun `each platform gets a command that exists there`() {
        assertEquals(listOf(listOf("open", url)), UrlOpener.fallbackCommands("Mac OS X", url))
        assertEquals(
            listOf(listOf("rundll32", "url.dll,FileProtocolHandler", url)),
            UrlOpener.fallbackCommands("Windows 11", url),
        )
        assertEquals(listOf("xdg-open", url), UrlOpener.fallbackCommands("Linux", url).first())
    }

    @Test
    fun `a blank url is not opened at all`() {
        assertFalse(UrlOpener.open("   ", browseSupported = { true }, browse = { error("no") }, exec = { false }))
    }
}
