@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebTabEngineUnavailableTest {

    @Test
    fun `WebTab with no engine shows the generic unavailable message`() =
        webTab(cefInitialized = false, cefMacOsUnsupported = false) { _, _ ->
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_BODY).assertExists()
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_MACOS_TITLE).assertDoesNotExist()
    }

    @Test
    fun `WebTab on an unsupported macOS shows the macOS-specific message`() =
        webTab(cefInitialized = false, cefMacOsUnsupported = true) { _, _ ->
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_MACOS_TITLE).assertExists()
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_MACOS_BODY).assertExists()
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertDoesNotExist()
    }

    @Test
    fun `WebEngineUnavailable defaults to the real CefManager state`() = runComposeUiTest {
        setContent { MaterialTheme { WebEngineUnavailable() } }
        onNodeWithText(WebLabel.ENGINE_UNAVAILABLE_TITLE).assertExists()
    }

    @Test
    fun `normaliseUrl leaves a fully-qualified http or https URL untouched`() {
        assertEquals("https://example.com", normaliseUrl("https://example.com"))
        assertEquals("http://example.com", normaliseUrl("http://example.com"))
    }

    @Test
    fun `normaliseUrl prepends https to a bare host`() {
        assertEquals("https://example.com", normaliseUrl("example.com"))
    }

    @Test
    fun `normaliseUrl trims surrounding whitespace before checking the scheme`() {
        assertEquals("https://example.com", normaliseUrl("  example.com  "))
    }

    @Test
    fun `normaliseUrl leaves blank input blank`() {
        assertEquals("", normaliseUrl(""))
        assertEquals("", normaliseUrl("   "))
    }

    @Test
    fun `commonPrefixLength finds the shared prefix of two strings`() {
        assertEquals(3, commonPrefixLength("cat", "catalog"))
        assertEquals(0, commonPrefixLength("cat", "dog"))
        assertEquals(0, commonPrefixLength("", "anything"))
    }

    @Test
    fun `commonPrefixLength is bounded by the shorter string`() {
        assertEquals(3, commonPrefixLength("cats", "cat"))
    }
}
