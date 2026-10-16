@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.serverui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.churchpresenter.calendar.sync.JoinedComputer
import org.churchpresenter.calendar.sync.PairedDevice
import org.churchpresenter.calendar.sync.SyncOutcome
import org.churchpresenter.server.CalendarSyncStatus
import org.churchpresenter.settings.AppSettings
import org.churchpresenter.settings.CalendarSyncSettings
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

class CalendarSyncCardPartsTest {

    private val now: Instant = Instant.parse("2026-09-20T10:30:00Z")

    private val main = CalendarSyncSettings(
        enabled = true,
        relayUrl = "https://relay.example",
        instanceId = "inst-1",
        desktopToken = "tok",
        instanceKey = "k".repeat(43),
    )

    private val follower = main.copy(
        desktopToken = "",
        role = CalendarSyncSettings.ROLE_FOLLOWER,
        computerId = "me",
        computerToken = "ctok",
        mainName = "Sanctuary",
    )

    /** Every callback the card takes, recorded by name. */
    private class Calls {
        val seen = ArrayList<String>()
        fun of(name: String): () -> Unit = { seen += name }
        fun with(name: String): (String) -> Unit = { seen += "$name:$it" }
    }

    private fun ComposeUiTest.card(
        settings: CalendarSyncSettings,
        status: CalendarSyncStatus,
        calls: Calls,
        computers: List<JoinedComputer> = emptyList(),
        devices: List<PairedDevice> = emptyList(),
        multiComputer: Boolean = true,
    ) {
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(calendarSync = settings),
                    onSettingsChange = {},
                    status = status,
                    devices = devices,
                    labelFor = { "" },
                    onSyncNow = calls.of("sync"),
                    onUnpair = calls.of("unpair"),
                    onRevoke = calls.with("revoke"),
                    onInvite = calls.of("invite"),
                    zone = ZoneId.of("UTC"),
                    locale = Locale.US,
                    now = now,
                    computerActions = if (multiComputer) {
                        ComputerActions(
                            computers = computers,
                            onAdd = calls.of("add"),
                            onRemove = calls.with("remove"),
                            onMakeMain = calls.with("main"),
                            onJoin = calls.of("join"),
                            onTakeOver = calls.of("takeOver"),
                        )
                    } else {
                        null
                    },
                )
            }
        }
    }

    @Test
    fun `a failure reads in plain words, with the relay's own one click away and a retry`() = runComposeUiTest {
        val calls = Calls()
        card(main, CalendarSyncStatus.Failed("relay rejected the request (500)"), calls)
        onNodeWithText("Offline").assertExists()
        onNodeWithText("Can't reach the sync service right now").assertExists()
        onNodeWithText("relay rejected the request (500)").assertDoesNotExist()
        onNodeWithText("Details").performClick()
        onNodeWithText("relay rejected the request (500)").assertExists()
        onNodeWithText("Hide details").performClick()
        onNodeWithText("relay rejected the request (500)").assertDoesNotExist()
        onNodeWithText("Retry").performClick()
        waitForIdle()
        assertEquals(listOf("sync"), calls.seen)
    }

    @Test
    fun `the pill says off, unregistered, syncing and synced`() = runComposeUiTest {
        var settings by mutableStateOf(CalendarSyncSettings())
        var status by mutableStateOf<CalendarSyncStatus>(CalendarSyncStatus.Off)
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(calendarSync = settings),
                    onSettingsChange = {},
                    status = status,
                    devices = emptyList(),
                    labelFor = { "" },
                    onSyncNow = {},
                    onUnpair = {},
                    onRevoke = {},
                    zone = ZoneId.of("UTC"),
                    locale = Locale.US,
                    now = now,
                )
            }
        }
        onNodeWithText("Off").assertExists()
        settings = CalendarSyncSettings(enabled = true)
        status = CalendarSyncStatus.Unpaired
        waitForIdle()
        onNodeWithText("Not registered").assertExists()
        onNodeWithText("Not registered yet", substring = true).assertExists()
        settings = main
        status = CalendarSyncStatus.Syncing
        waitForIdle()
        // In the pill, and in place of Sync now while the round runs.
        onAllNodesWithText("Syncing…").assertCountEquals(2)
        status = CalendarSyncStatus.Synced(now.toString(), SyncOutcome(1, 0, 2, 0))
        waitForIdle()
        onNodeWithText("Synced", substring = true).assertExists()
        onNodeWithText("2 rows could not be matched", substring = true).assertExists()
    }

    @Test
    fun `the timed-out, refused and second-desktop states each get a banner with a retry`() = runComposeUiTest {
        var status by mutableStateOf<CalendarSyncStatus>(CalendarSyncStatus.TimedOut)
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(calendarSync = main),
                    onSettingsChange = {},
                    status = status,
                    devices = emptyList(),
                    labelFor = { "" },
                    onSyncNow = {},
                    onUnpair = {},
                    onRevoke = {},
                    now = now,
                )
            }
        }
        onNodeWithText("did not answer in time", substring = true).assertExists()
        status = CalendarSyncStatus.Unauthorized
        waitForIdle()
        onNodeWithText("no longer accepts this computer", substring = true).assertExists()
        status = CalendarSyncStatus.OtherDesktop("other")
        waitForIdle()
        onNodeWithText("Another computer is syncing", substring = true).assertExists()
        onNodeWithText("Retry").assertExists()
    }

    @Test
    fun `phones are listed with a dashed note when there are none, and invite and sync reach the card`() =
        runComposeUiTest {
            val calls = Calls()
            card(main, CalendarSyncStatus.Synced(now.toString(), SyncOutcome(1, 0, 0, 0)), calls)
            onNodeWithText("No phones yet", substring = true).assertExists()
            onNodeWithText("Invite a phone…").performClick()
            onNodeWithText("Sync now").performClick()
            waitForIdle()
            assertEquals(listOf("invite", "sync"), calls.seen)
        }

    @Test
    fun `while a round runs, Sync now says so and waits`() = runComposeUiTest {
        card(main, CalendarSyncStatus.Syncing, Calls(), devices = listOf(PairedDevice("p", "Phone", "", "")))
        onNodeWithText("Sync now").assertDoesNotExist()
        onNodeWithText("Phone").assertExists()
    }

    @Test
    fun `each computer shows its role and standing, and its buttons reach the card`() = runComposeUiTest {
        val calls = Calls()
        card(
            main,
            CalendarSyncStatus.Synced(now.toString(), SyncOutcome(1, 0, 0, 0)),
            calls,
            computers = listOf(
                JoinedComputer("c1", CalendarSyncSettings.ROLE_FOLLOWER, "Office iMac", now.toString(), now.toString()),
                JoinedComputer("c2", CalendarSyncSettings.ROLE_VIEWER, "", now.toString(), ""),
            ),
        )
        onNodeWithText("Office iMac").assertExists()
        onNodeWithText("Follower").assertExists()
        onNodeWithText("c2").assertExists()
        onNodeWithText("View only").assertExists()
        onNodeWithText("Not joined yet").assertExists()
        onNodeWithText("Last seen", substring = true).assertExists()
        // Only a follower that has joined can be made the main computer.
        onNodeWithText("Make main").performClick()
        onAllNodesWithText("Remove")[1].performClick()
        onNodeWithText("Add a computer…").performClick()
        onNodeWithText("Join another…").performClick()
        waitForIdle()
        assertEquals(listOf("main:c1", "remove:c2", "add", "join"), calls.seen)
    }

    @Test
    fun `with no computers the list says how to add one, and before registering only joining is offered`() =
        runComposeUiTest {
            var settings by mutableStateOf(main)
            setContent {
                MaterialTheme {
                    CalendarSyncCardContent(
                        settings = AppSettings(calendarSync = settings),
                        onSettingsChange = {},
                        status = CalendarSyncStatus.Unpaired,
                        devices = emptyList(),
                        labelFor = { "" },
                        onSyncNow = {},
                        onUnpair = {},
                        onRevoke = {},
                        now = now,
                        computerActions = ComputerActions(emptyList(), {}, {}, {}, {}, {}),
                    )
                }
            }
            onNodeWithText("No other computers", substring = true).assertExists()
            settings = CalendarSyncSettings(enabled = true)
            waitForIdle()
            onNodeWithText("Add a computer…").assertDoesNotExist()
            onNodeWithText("Join another…").assertExists()
            onNodeWithText("Connection details").assertDoesNotExist()
        }

    @Test
    fun `starting over is greyed out within the week and works after it`() = runComposeUiTest {
        val calls = Calls()
        card(main.copy(rotatedAt = now.minusSeconds(DAY).toString()), CalendarSyncStatus.Off, calls)
        onNodeWithText("Start over with a new key…").assertIsNotEnabled()
        onNodeWithText("available again on", substring = true).assertExists()
    }

    @Test
    fun `a follower names the main computer, syncs, leaves, and is offered a takeover after a silent week`() =
        runComposeUiTest {
            val calls = Calls()
            val seen = now.minusSeconds(DAY * 8).toString()
            card(
                follower,
                CalendarSyncStatus.Synced(now.toString(), SyncOutcome(1, 0, 0, 0, mainLastSeen = seen)),
                calls,
            )
            onNodeWithText("Sanctuary").assertExists()
            onNodeWithText("Follows Sanctuary", substring = true).assertExists()
            onNodeWithText("Sanctuary last synced", substring = true).assertExists()
            onNodeWithText("hasn't synced for a week", substring = true).assertExists()
            onNodeWithText("Sync now").performClick()
            onNodeWithText("Make this the main computer").performClick()
            onNodeWithText("Leave").performClick()
            waitForIdle()
            assertEquals(listOf("sync", "takeOver", "unpair"), calls.seen)
        }

    @Test
    fun `a viewer that heard from the main computer recently is offered no takeover`() = runComposeUiTest {
        card(
            follower.copy(role = CalendarSyncSettings.ROLE_VIEWER, mainName = ""),
            CalendarSyncStatus.Synced(now.toString(), SyncOutcome(1, 0, 0, 0, mainLastSeen = now.toString())),
            Calls(),
        )
        onNodeWithText("Views the main computer's calendar — read only.").assertExists()
        onNodeWithText("Make this the main computer").assertDoesNotExist()
    }

    @Test
    fun `before its first round a joined computer names no last sync`() = runComposeUiTest {
        card(follower, CalendarSyncStatus.Syncing, Calls())
        onNodeWithText("Follows Sanctuary", substring = true).assertExists()
        onNodeWithText("last synced", substring = true).assertDoesNotExist()
        onNodeWithText("Make this the main computer").assertDoesNotExist()
    }

    @Test
    fun `the description names other computers only where they can join`() = runComposeUiTest {
        var dev by mutableStateOf(false)
        setContent {
            MaterialTheme {
                CalendarSyncCardContent(
                    settings = AppSettings(calendarSync = main),
                    onSettingsChange = {},
                    status = CalendarSyncStatus.Off,
                    devices = emptyList(),
                    labelFor = { "" },
                    onSyncNow = {},
                    onUnpair = {},
                    onRevoke = {},
                    now = now,
                    computerActions = if (dev) ComputerActions(emptyList(), {}, {}, {}, {}, {}) else null,
                )
            }
        }
        onNodeWithText("Calendar sync").assertExists()
        onNodeWithText("Plan services from the mobile app", substring = true).assertExists()
        dev = true
        waitForIdle()
        onNodeWithText("Plan services from phones, tablets and other computers", substring = true).assertExists()
    }

    @Test
    fun `out of dev mode a joined computer shows only the status`() = runComposeUiTest {
        card(follower, CalendarSyncStatus.Syncing, Calls(), multiComputer = false)
        onNodeWithText("Leave").assertDoesNotExist()
        onNodeWithText("Join another…").assertDoesNotExist()
    }

    private companion object {
        const val DAY = 86_400L
    }
}
