@file:OptIn(ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.server.CalendarSyncStatus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The phone-enrollment QR: shown with its instructions until it is closed -- or, because it carries
 * the calendar's key and this screen is often on a projector, until two minutes have passed -- and,
 * when the relay could not be asked, why there is no QR.
 */
class CalendarEnrollQrContentTest {

    private val enrollment = CalendarEnrollment(
        relayUrl = "https://relay.example",
        instanceId = "inst",
        deviceId = "phone-1",
        deviceToken = "token",
        instanceKey = "key",
    )

    @Test
    fun `the QR says how to use it, and Close puts it away`() = runComposeUiTest {
        var closed = 0
        setContent { MaterialTheme { CalendarEnrollQrContent(enrollment) { closed++ } } }
        waitForIdle()

        onNodeWithText("Scan this code with the ChurchPresenter app", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun `the QR closes itself after two minutes`() = runComposeUiTest {
        mainClock.autoAdvance = false
        var closed = 0
        setContent { MaterialTheme { CalendarEnrollQrContent(enrollment) { closed++ } } }
        mainClock.advanceTimeByFrame()

        mainClock.advanceTimeBy(119_000)
        assertEquals(0, closed, "still up just before")
        mainClock.advanceTimeBy(2_000)
        assertEquals(1, closed)
    }

    @Test
    fun `with no invite it says the relay could not be reached, and why`() = runComposeUiTest {
        var closed = 0
        val failed = CalendarSyncStatus.Failed("no route to host")
        setContent { MaterialTheme { CalendarInviteFailedContent(failed) { closed++ } } }
        waitForIdle()

        onNodeWithText("Could not create an invite", substring = true).assertExists()
        onNodeWithText("no route to host", substring = true).assertExists()
        onNodeWithText("Close").performClick()
        waitForIdle()
        assertEquals(1, closed)
    }

    @Test
    fun `the code is read out in groups of three`() {
        assertEquals("Code 482 913 1", enrollCodeText("4829131", "Code %s"))
    }

    @Test
    fun `the invite dialog says why there is no QR`() {
        runComposeUiTest {
            setContent {
                MaterialTheme {
                    CalendarInviteFailedContent(
                        status = CalendarSyncStatus.Failed("connection refused"),
                        onDismiss = {},
                    )
                }
            }
            onAllNodesWithText("connection refused", substring = true)[0]
                .assertExists("the reason is the sync card's own words for it")
        }
    }
}
