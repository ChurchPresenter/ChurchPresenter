@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.CalendarEnrollQrContent
import org.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.sharedui.screenshot.captureComponent
import kotlin.test.Test

class CalendarEnrollQrScreenshotTest {

    private companion object {
        const val SECTION = "calendarEnrollQr"
    }

    /** The QR after Allow: the deep link as a code, with the instruction under it. */
    @Test
    fun `the enrollment QR`() = captureComponent(SECTION, "enroll_qr") {
        Box(Modifier.size(400.dp, 540.dp)) {
            CalendarEnrollQrContent(
                enrollment = CalendarEnrollment(
                    relayUrl = "https://relay.example",
                    instanceId = "3f7c1a9e-2b4d-4e6f-8a1b-2c3d4e5f6a7b",
                    deviceId = "9a1b2c3d-4e5f-4a6b-8c7d-0e1f2a3b4c5d",
                    deviceToken = "d".repeat(43),
                    instanceKey = "k".repeat(43),
                ),
                onDismiss = {},
            )
        }
    }
}
