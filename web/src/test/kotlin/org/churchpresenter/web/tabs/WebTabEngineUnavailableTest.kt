@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.web.tabs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WebTabEngineUnavailableTest {

    @Test
    fun `WebTab with no engine shows the generic unavailable message`() = runComposeUiTest {
        setContent { MaterialTheme { WebTab(cefInitialized = false, cefMacOsUnsupported = false) } }
        onNodeWithText(Label.ENGINE_UNAVAILABLE_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_BODY).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_MACOS_TITLE).assertDoesNotExist()
    }

    @Test
    fun `WebTab on an unsupported macOS shows the macOS-specific message`() = runComposeUiTest {
        setContent { MaterialTheme { WebTab(cefInitialized = false, cefMacOsUnsupported = true) } }
        onNodeWithText(Label.ENGINE_UNAVAILABLE_MACOS_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_MACOS_BODY).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_TITLE).assertDoesNotExist()
    }

    @Test
    fun `WebTab blocked by a machine policy says so instead of naming a redistributable`() = runComposeUiTest {
        setContent { MaterialTheme { WebTab(cefInitialized = false, cefBlockedByPolicy = true) } }
        // Twelve reports across five churches, on managed Windows installs where Application
        // Control blocks the downloaded jcef.dll. Installing the Visual C++ redistributable — what
        // the generic message asks for — does nothing about it.
        onNodeWithText(Label.ENGINE_UNAVAILABLE_POLICY_BODY).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_POLICY_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_TITLE).assertDoesNotExist()
    }

    @Test
    fun `an unsupported macOS outranks a policy block, since neither can be acted on together`() = runComposeUiTest {
        setContent {
            MaterialTheme { WebTab(cefInitialized = false, cefMacOsUnsupported = true, cefBlockedByPolicy = true) }
        }
        onNodeWithText(Label.ENGINE_UNAVAILABLE_MACOS_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_POLICY_TITLE).assertDoesNotExist()
    }

    @Test
    fun `a Linux system without one of the engine's libraries is told which one to install`() = runComposeUiTest {
        // Sentry CHURCH-PRESENTER-DESKTOP-9P: a Linux machine without libnspr4 was told to install
        // a Windows runtime.
        setContent {
            MaterialTheme {
                WebEngineUnavailable(
                    macOsUnsupported = false,
                    blockedByPolicy = false,
                    windowsUnsupported = false,
                    missingLibrary = "libnspr4.so",
                )
            }
        }

        onNodeWithText(Label.ENGINE_UNAVAILABLE_LIBRARY_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_LIBRARY_BODY).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_BODY).assertDoesNotExist()
    }

    @Test
    fun `an unsupported Windows names Windows 10 rather than a redistributable`() = runComposeUiTest {
        // Sentry CHURCH-PRESENTER-DESKTOP-75: Chromium no longer loads on Windows 8.1, and the generic
        // message sent the operator after a Visual C++ runtime that could not help.
        setContent {
            MaterialTheme {
                WebEngineUnavailable(macOsUnsupported = false, blockedByPolicy = false, windowsUnsupported = true)
            }
        }

        onNodeWithText(Label.ENGINE_UNAVAILABLE_WINDOWS_TITLE).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_WINDOWS_BODY).assertExists()
        onNodeWithText(Label.ENGINE_UNAVAILABLE_BODY).assertDoesNotExist()
    }

    @Test
    fun `WebEngineUnavailable defaults to the real CefManager state`() = runComposeUiTest {
        setContent { MaterialTheme { WebEngineUnavailable() } }
        onNodeWithText(Label.ENGINE_UNAVAILABLE_TITLE).assertExists()
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

/** The English strings these cases look for, from `:strings`' default `values/strings.xml`. */
private object Label {
    const val ENGINE_UNAVAILABLE_TITLE = "Web browser unavailable"
    const val ENGINE_UNAVAILABLE_BODY =
        "The browser engine could not start. Install the Microsoft Visual C++ Redistributable (x64) and restart " +
            "the app."
    const val ENGINE_UNAVAILABLE_MACOS_TITLE = "Web browser requires a newer macOS"
    const val ENGINE_UNAVAILABLE_MACOS_BODY =
        "ChurchPresenter's browser engine no longer supports this version of macOS. Update to macOS 12 " +
            "(Monterey) or later to use the Web tab and browser sources."
    const val ENGINE_UNAVAILABLE_WINDOWS_TITLE = "Web browser requires Windows 10 or later"
    const val ENGINE_UNAVAILABLE_WINDOWS_BODY =
        "ChurchPresenter's browser engine no longer supports this version of Windows. Update to Windows 10 " +
            "or later to use the Web tab and browser sources."
    const val ENGINE_UNAVAILABLE_LIBRARY_TITLE = "Web browser needs a system library"
    const val ENGINE_UNAVAILABLE_LIBRARY_BODY =
        "This computer is missing libnspr4.so, which the browser engine needs. Install the package that " +
            "provides it with your Linux distribution's package manager, then restart ChurchPresenter."
    const val ENGINE_UNAVAILABLE_POLICY_TITLE = "Web browser blocked by a policy on this computer"
    const val ENGINE_UNAVAILABLE_POLICY_BODY =
        "A software policy on this computer is blocking the browser engine ChurchPresenter downloads. Ask " +
            "whoever manages the computer to allow ChurchPresenter, or use a computer without that " +
            "restriction — the Web tab and browser sources need it."
}
