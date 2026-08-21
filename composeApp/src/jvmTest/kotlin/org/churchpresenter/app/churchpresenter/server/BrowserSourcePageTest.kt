package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.settings.ScreenAssignment
import org.churchpresenter.settings.utils.Constants
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BrowserSourcePageTest {

    private val output = ScreenAssignment()

    @Test
    fun `the page points its socket at the requested output index`() {
        val html = browserSourceOverlayPage(3, output, apiKeyEnabled = false, apiKey = "")
        assertContains(html, "/api${Constants.ENDPOINT_BROWSER_SOURCE}/3/ws")
    }

    @Test
    fun `no api key is appended when none is required`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "")
        assertFalse(html.contains(Constants.QUERY_PARAM_API_KEY))
    }

    @Test
    fun `an enabled api key is appended to the socket url`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = true, apiKey = "s3cret")
        assertContains(html, "${Constants.QUERY_PARAM_API_KEY}=s3cret")
    }

    @Test
    fun `an enabled but empty api key is not appended`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = true, apiKey = "")
        assertFalse(html.contains(Constants.QUERY_PARAM_API_KEY))
    }

    @Test
    fun `an output that demands a key gets one even when the global key is off`() {
        val guarded = output.copy(browserSourceApiKeyRequired = true)
        val html = browserSourceOverlayPage(0, guarded, apiKeyEnabled = false, apiKey = "abc")
        assertContains(html, "${Constants.QUERY_PARAM_API_KEY}=abc")
    }

    @Test
    fun `api keys with url-unsafe characters are encoded`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = true, apiKey = "a b&c")
        assertContains(html, "${Constants.QUERY_PARAM_API_KEY}=a+b%26c")
        assertFalse(html.contains("=a b&c"))
    }

    @Test
    fun `the body is transparent unless a background override asks otherwise`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "")
        assertContains(html, "transparent")
    }

    @Test
    fun `the black background override paints the body black`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "", bgOverride = "black")
        assertContains(html, "#000")
    }

    @Test
    fun `the background override is case insensitive`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "", bgOverride = "BLACK")
        assertContains(html, "#000")
    }

    @Test
    fun `an unrecognised background override falls back to transparent`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "", bgOverride = "chartreuse")
        assertContains(html, "transparent")
    }

    @Test
    fun `the page is a self-contained html document`() {
        val html = browserSourceOverlayPage(0, output, apiKeyEnabled = false, apiKey = "")
        assertTrue(html.trimStart().startsWith("<!DOCTYPE html>"))
        assertContains(html, "</html>")
        assertFalse(html.contains("http://") && html.contains("<script src="))
    }
}
