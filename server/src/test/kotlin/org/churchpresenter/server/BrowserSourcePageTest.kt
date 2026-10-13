package org.churchpresenter.server

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSourcePageTest {

    private val output = ScreenAssignment()
    private val guarded = output.copy(browserSourceApiKeyRequired = true)

    @Test
    fun `the page points its socket at the requested output index`() {
        val html = browserSourceOverlayPage(3, output, apiKey = "")
        assertContains(html, "/api${Constants.ENDPOINT_BROWSER_SOURCE}/3/ws")
    }

    @Test
    fun `no api key is appended when none is required`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "")
        assertFalse(html.contains(Constants.QUERY_PARAM_API_KEY))
    }

    @Test
    fun `an output that does not demand a key never carries the server's key`() {
        // The page is served without a key on such an output, so a key in it would be readable by
        // anyone who can load the page.
        val html = browserSourceOverlayPage(0, output, apiKey = "s3cret")
        assertFalse(html.contains("s3cret"))
    }

    @Test
    fun `an output that demands a key but has none set appends nothing`() {
        val html = browserSourceOverlayPage(0, guarded, apiKey = "")
        assertFalse(html.contains(Constants.QUERY_PARAM_API_KEY))
    }

    @Test
    fun `an output that demands a key gets it`() {
        val html = browserSourceOverlayPage(0, guarded, apiKey = "abc")
        assertContains(html, "${Constants.QUERY_PARAM_API_KEY}=abc")
    }

    @Test
    fun `api keys with url-unsafe characters are encoded`() {
        val html = browserSourceOverlayPage(0, guarded, apiKey = "a b&c")
        assertContains(html, "${Constants.QUERY_PARAM_API_KEY}=a+b%26c")
        assertFalse(html.contains("=a b&c"))
    }

    @Test
    fun `the body is transparent unless a background override asks otherwise`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "")
        assertContains(html, "transparent")
    }

    @Test
    fun `the black background override paints the body black`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "", bgOverride = "black")
        assertContains(html, "#000")
    }

    @Test
    fun `the background override is case insensitive`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "", bgOverride = "BLACK")
        assertContains(html, "#000")
    }

    @Test
    fun `an unrecognised background override falls back to transparent`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "", bgOverride = "chartreuse")
        assertContains(html, "transparent")
    }

    // ── The transpose buttons (issue #649) ──────────────────────────────────

    @Test
    fun `every page carries the transpose buttons, hidden until the server offers them`() {
        val html = browserSourceOverlayPage(2, output, apiKey = "")
        assertContains(html, """<div id="transpose"""")
        assertContains(html, "#transpose{position:fixed;left:50%;bottom:16px;transform:translateX(-50%);display:none;")
        assertContains(html, "tpBox.classList.toggle('shown',!!state.controls)")
    }

    @Test
    fun `the page asks for approval only once the server offers the buttons`() {
        val html = browserSourceOverlayPage(2, output, apiKey = "")
        assertContains(html, "if(state.controls&&!tpAsked&&!tpApproved)tpAuth();")
        // Nothing calls it on load: an OBS page on an ordinary profile never asks the desktop.
        assertFalse(html.lines().any { it.trim() == "tpAuth();" })
    }

    @Test
    fun `the buttons talk to this output's own routes, with the device's id`() {
        val html = browserSourceOverlayPage(2, output, apiKey = "")
        assertContains(html, "/api${Constants.ENDPOINT_BROWSER_SOURCE}/2/auth")
        assertContains(html, "/api${Constants.ENDPOINT_BROWSER_SOURCE}/2/transpose")
        assertContains(html, "'${Constants.HEADER_DEVICE_ID}':deviceId")
    }

    @Test
    fun `a text message is read as transpose state, never drawn as a frame`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "")
        assertContains(html, "ws.onmessage=onMessage;")
        assertContains(html, "if(typeof event.data==='string'){")
    }

    @Test
    fun `the api key reaches the buttons' requests too, escaped for the script`() {
        val html = browserSourceOverlayPage(0, guarded, apiKey = "it's</script>")
        assertContains(html, "tpHeaders['${Constants.HEADER_API_KEY}']='it\\'s\\x3c/script>'")
        assertFalse(html.contains("it's</script>"))
    }

    @Test
    fun `no key is put in the script when none is required`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "unused")
        assertContains(html, "if(false)tpHeaders")
        assertFalse(html.contains("'unused'"))
    }

    @Test
    fun `the page is a self-contained html document`() {
        val html = browserSourceOverlayPage(0, output, apiKey = "")
        assertTrue(html.trimStart().startsWith("<!DOCTYPE html>"))
        assertContains(html, "</html>")
        assertFalse(html.contains("http://") && html.contains("<script src="))
    }
}
