@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.screenshot

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import org.churchpresenter.app.churchpresenter.dialogs.CalendarEnrollQrContent
import org.churchpresenter.app.churchpresenter.dialogs.tabs.CalendarSyncCardContent
import org.churchpresenter.app.churchpresenter.server.CalendarEnrollment
import org.churchpresenter.app.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import org.churchpresenter.theme.ChurchPresenterTheme
import org.churchpresenter.theme.ThemeMode
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The "Calendar on phones" card of the Server tab, in each state it reports, and the QR the
 * desktop shows a phone after Allow.
 *
 * Shot on its own: the Server tab's images are taken with no sync service, so the card never
 * appears there, and its states are the point of it -- a reviewer wants to see what "another
 * computer is syncing this calendar" reads like, not that the card exists.
 */
class CalendarSyncCardScreenshotTest {

    private companion object {
        const val SECTION = "calendarSyncCard"
        val ZONE: ZoneId = ZoneId.of("UTC")
        val LOCALE: Locale = Locale.US
        const val AT = "2026-09-20T10:30:00Z"
    }

    private val paired = AppSettings(
        calendarSync = CalendarSyncSettings(
            enabled = true,
            instanceId = "3f7c1a9e-2b4d-4e6f-8a1b-2c3d4e5f6a7b",
            desktopToken = "tok",
            instanceKey = "k".repeat(43),
        ),
    )

    private fun outcome(phoneChanges: Int = 0, unresolvedRows: Int = 0, droppedRows: Int = 0) = SyncOutcome(
        cursor = 12,
        phoneChanges = phoneChanges,
        unresolvedRows = unresolvedRows,
        droppedRows = droppedRows,
    )

    private val devices = listOf(
        PairedDevice("phone-1", "Anna's iPhone", AT, AT),
        PairedDevice("phone-2", "", AT, ""),
    )

    private fun card(
        name: String,
        settings: AppSettings = paired,
        status: CalendarSyncStatus,
        devices: List<PairedDevice> = emptyList(),
        drive: androidx.compose.ui.test.ComposeUiTest.() -> Unit = {},
    ) = captureComponent(SECTION, name, drive = drive) {
        Box(Modifier.width(720.dp)) {
            CalendarSyncCardContent(
                settings = settings,
                onSettingsChange = {},
                status = status,
                devices = devices,
                labelFor = { id -> if (id == "phone-1") "Anna" else "" },
                onSyncNow = {},
                onUnpair = {},
                onRevoke = {},
                zone = ZONE,
                locale = LOCALE,
            )
        }
    }

    @Test
    fun `switched off`() = card("off", settings = AppSettings(), status = CalendarSyncStatus.Off)

    @Test
    fun `on but not yet registered`() = card(
        "unpaired",
        settings = AppSettings(calendarSync = CalendarSyncSettings(enabled = true)),
        status = CalendarSyncStatus.Unpaired,
    )

    @Test
    fun `syncing`() = card("syncing", status = CalendarSyncStatus.Syncing)

    @Test
    fun `synced with two phones enrolled`() = card(
        "synced",
        status = CalendarSyncStatus.Synced(AT, outcome(phoneChanges = 3)),
        devices = devices,
    )

    @Test
    fun `synced with rows this library could not match`() = card(
        "synced_unresolved",
        status = CalendarSyncStatus.Synced(AT, outcome(unresolvedRows = 2, droppedRows = 1)),
    )

    @Test
    fun `the relay could not be reached`() = card("failed", status = CalendarSyncStatus.Failed("connection refused"))

    @Test
    fun `the startup round timed out`() = card("timed_out", status = CalendarSyncStatus.TimedOut)

    @Test
    fun `the relay no longer accepts this computer`() = card("unauthorized", status = CalendarSyncStatus.Unauthorized)

    @Test
    fun `another computer is syncing the same calendar`() =
        card("other_desktop", status = CalendarSyncStatus.OtherDesktop("install-B"))

    // The two below assert behavior and take no picture: pressed or not, the card looks the same.

    @Test
    fun `the buttons reach their callbacks`() {
        var syncNow = 0
        var unpair = 0
        val revoked = ArrayList<String>()
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                    CalendarSyncCardContent(
                        settings = paired,
                        onSettingsChange = {},
                        status = CalendarSyncStatus.Synced(AT, SyncOutcome(1, 0, 0, 0)),
                        devices = devices,
                        labelFor = { "" },
                        onSyncNow = { syncNow++ },
                        onUnpair = { unpair++ },
                        onRevoke = { revoked += it },
                        zone = ZONE,
                        locale = LOCALE,
                    )
                }
            }
            onAllNodesWithText("Sync now")[0].performClick()
            onAllNodesWithText("Start over with a new key")[0].performClick()
            onAllNodesWithText("Revoke")[1].performClick()
            waitForIdle()
        }
        assertEquals(1, syncNow)
        assertEquals(1, unpair)
        assertEquals(listOf("phone-2"), revoked)
    }

    @Test
    fun `flipping the switch hands the change back`() {
        val seen = ArrayList<Boolean>()
        runComposeUiTest {
            setContent {
                ChurchPresenterTheme(themeMode = ThemeMode.LIGHT) {
                    CalendarSyncCardContent(
                        settings = AppSettings(),
                        onSettingsChange = { change -> seen += change(AppSettings()).calendarSync.enabled },
                        status = CalendarSyncStatus.Off,
                        devices = emptyList(),
                        labelFor = { "" },
                        onSyncNow = {},
                        onUnpair = {},
                        onRevoke = {},
                        zone = ZONE,
                        locale = LOCALE,
                    )
                }
            }
            onAllNodes(isToggleable())[0].performClick()
            waitForIdle()
        }
        assertEquals(listOf(true), seen)
    }

    /** The QR after Allow: the deep link as a code, with the instruction under it. */
    @Test
    fun `the enrollment QR`() = captureComponent(SECTION, "enroll_qr") {
        Box(Modifier.size(400.dp, 540.dp)) {
            CalendarEnrollQrContent(
                enrollment = CalendarEnrollment(
                    relayUrl = "https://sync.churchpresenter.org",
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
