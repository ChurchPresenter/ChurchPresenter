package org.churchpresenter.canvas

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Telling Windows' camera privacy switch apart from a camera that cannot be opened. ffmpeg fails
 * the same way for both -- "Unable to BindToObject", then "could not find video device" -- with
 * the switch off and for a DeckLink capture driver with no card fitted, so the switch is read.
 */
class WindowsCameraPrivacyTest {

    private fun regAnswer(value: String) = """

        HKEY_CURRENT_USER\Software\Microsoft\Windows\CurrentVersion\CapabilityAccessManager\ConsentStore\webcam\NonPackaged
            Value    REG_SZ    $value

    """.trimIndent()

    @Test
    fun `a Deny answer is a block and an Allow answer is not`() {
        assertTrue(consentDenied(regAnswer("Deny")))
        assertFalse(consentDenied(regAnswer("Allow")))
        assertFalse(consentDenied("ERROR: The system was unable to find the specified registry key or value."))
    }

    @Test
    fun `any one of the three switches set to Deny blocks the camera`() {
        CAMERA_CONSENT_KEYS.forEach { denied ->
            assertTrue(
                windowsCameraBlocked { key -> regAnswer(if (key == denied) "Deny" else "Allow") },
                "$denied set to Deny must block",
            )
        }
    }

    @Test
    fun `switches that allow, or cannot be read, do not block`() {
        assertFalse(windowsCameraBlocked { regAnswer("Allow") })
        assertFalse(windowsCameraBlocked { null })
    }

    @Test
    fun `a device not found is reported as the privacy block only while the switch is off`() {
        assertEquals(
            CameraFailure.PERMISSION_DENIED,
            refineForWindowsPrivacy(CameraFailure.DEVICE_NOT_FOUND, DSHOW_SCHEME) { true },
        )
        assertEquals(
            CameraFailure.DEVICE_NOT_FOUND,
            refineForWindowsPrivacy(CameraFailure.DEVICE_NOT_FOUND, DSHOW_SCHEME) { false },
            "a cardless capture driver with access on is not a privacy problem",
        )
    }

    @Test
    fun `the switch is not consulted for other failures or other platforms`() {
        var asked = false
        val blocked = { asked = true; true }
        assertEquals(
            CameraFailure.DEVICE_BUSY,
            refineForWindowsPrivacy(CameraFailure.DEVICE_BUSY, DSHOW_SCHEME, blocked),
        )
        assertEquals(
            CameraFailure.DEVICE_NOT_FOUND,
            refineForWindowsPrivacy(CameraFailure.DEVICE_NOT_FOUND, "v4l2", blocked),
        )
        assertFalse(asked, "the registry is only read for a DirectShow device that was not found")
    }
}
