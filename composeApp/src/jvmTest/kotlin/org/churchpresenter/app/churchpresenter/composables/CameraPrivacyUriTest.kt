package org.churchpresenter.app.churchpresenter.composables

import org.churchpresenter.app.churchpresenter.utils.UrlOpener
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

/**
 * Where the "open camera permissions" button leads, per platform.
 *
 * These are OS routing schemes rather than web links, which is the whole reason [UrlOpener] refuses
 * to hand them to AWT: `Desktop.browse` passes `x-apple.systempreferences:` straight to Safari,
 * which opens a blank tab, asks whether the *website* may open System Settings, and returns
 * normally — so nothing looks like a failure and the shell fallback that would have worked is never
 * reached. The pairing is asserted here so a future edit cannot quietly make one a web URL.
 */
class CameraPrivacyUriTest {

    @Test
    fun `a Mac is sent to the Camera pane of the privacy settings`() {
        assertEquals(MAC_CAMERA_PRIVACY_URI, cameraPrivacyUri("Mac OS X"))
    }

    @Test
    fun `darwin is the same platform under its other name`() {
        assertEquals(MAC_CAMERA_PRIVACY_URI, cameraPrivacyUri("Darwin"))
    }

    @Test
    fun `Windows is sent to its webcam privacy page`() {
        assertEquals(WINDOWS_CAMERA_PRIVACY_URI, cameraPrivacyUri("Windows 11"))
    }

    @Test
    fun `the platform is matched whatever case it is reported in`() {
        assertEquals(MAC_CAMERA_PRIVACY_URI, cameraPrivacyUri("MAC OS X"))
        assertEquals(WINDOWS_CAMERA_PRIVACY_URI, cameraPrivacyUri("WINDOWS 10"))
    }

    @Test
    fun `Linux has no such page, so the button is not offered`() {
        assertNull(cameraPrivacyUri("Linux"))
    }

    @Test
    fun `an unrecognised platform offers nothing rather than guessing`() {
        assertNull(cameraPrivacyUri("Plan 9"))
        assertNull(cameraPrivacyUri(""))
    }

    @Test
    fun `neither privacy uri is a web link, so neither is offered to AWT`() {
        assertFalse(UrlOpener.isWebUrl(MAC_CAMERA_PRIVACY_URI))
        assertFalse(UrlOpener.isWebUrl(WINDOWS_CAMERA_PRIVACY_URI))
    }

    @Test
    fun `each platform's uri names its own settings scheme`() {
        assertEquals("x-apple.systempreferences", MAC_CAMERA_PRIVACY_URI.substringBefore(':'))
        assertEquals("ms-settings", WINDOWS_CAMERA_PRIVACY_URI.substringBefore(':'))
    }
}
