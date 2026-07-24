package org.churchpresenter.app.churchpresenter.utils

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [MacMenuBarActivationFix] is a Compose + AWT startup side-effect (a delayed window activation),
 * so its body needs a real window scope and desktop and is out of reach for a unit test. The one
 * decision it makes — whether this is macOS at all — is pulled into [isMacOs] and pinned here, so a
 * platform-string change can't silently run (or skip) the fix on the wrong OS.
 */
class MacWindowActivationTest {

    @Test
    fun `macOS names are recognised regardless of case`() {
        for (name in listOf("Mac OS X", "macOS", "MACOS 14.5", "Mac OS X 10.15.7")) {
            assertTrue(isMacOs(name), "\"$name\" should be treated as macOS")
        }
    }

    @Test
    fun `other platforms are not macOS`() {
        for (name in listOf("Windows 11", "Linux", "FreeBSD", "")) {
            assertFalse(isMacOs(name), "\"$name\" should not be treated as macOS")
        }
    }
}
