package org.churchpresenter.app.churchpresenter.viewmodel

import companionsatellite.CompanionButtonUpdate
import companionsatellite.CompanionConnectionStatus
import companionsatellite.CompanionSatelliteClient
import io.mockk.every
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.verify
import io.sentry.SentryLevel
import org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import org.churchpresenter.app.churchpresenter.models.CompanionSurfaceSlot
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * ChurchPresenter acting as a Bitfocus Companion surface: one registration per enabled placement,
 * each with its own device identity so Companion can keep them on different pages.
 *
 * Three rules carry the weight here. Each placement must register under a *distinct* device id, or
 * two placements fight over the same "current page" in Companion. A placement whose grid shape was
 * edited must be torn down and re-registered, because the protocol has no "change my grid" message
 * — only a fresh ADD-DEVICE. And connection failures must leave a warning breadcrumb once per
 * episode, not once per retry, since the reconnect loop cycles CONNECTING/ERROR every couple of
 * seconds forever while a host is unreachable. (These are environmental, so they are breadcrumbs
 * for later crash context, not standalone Sentry issues — hence the WARNING-level discriminator.)
 *
 * `CompanionSatelliteClient` is replaced with `mockkConstructor`, so no TCP socket is ever opened
 * and no reconnect loop runs. Companion's own callbacks (status, buttons, brightness) are invoked
 * directly through the client the view model constructed — the view model wires them in a private
 * factory, so this is the only way to reach that logic without a live Companion instance.
 *
 * Note on timing: the first test to run pays ~1.5s for MockK to instrument the client class. That
 * is one-off JVM work for the whole class, not a wait — every test here is otherwise well under
 * the per-test budget in AGENT.md.
 */
class CompanionSatelliteViewModelTest {

    private val created = mutableListOf<CompanionSatelliteViewModel>()

    @BeforeTest
    fun stubClient() {
        mockkConstructor(CompanionSatelliteClient::class)
        every { anyConstructed<CompanionSatelliteClient>().connect(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().disconnect() } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().dispose() } returns Unit
        every { anyConstructed<CompanionSatelliteClient>().pressButton(any()) } returns Unit
        mockkObject(CrashReporter)
    }

    @AfterTest
    fun cleanUp() {
        created.forEach { runCatching { it.dispose() } }
        created.clear()
        unmockkObject(CrashReporter)
        unmockkConstructor(CompanionSatelliteClient::class)
    }

    private fun vm(): CompanionSatelliteViewModel = CompanionSatelliteViewModel().also { created.add(it) }

    private fun settings(
        host: String = "10.0.0.5",
        tab: Boolean = false,
        left: Boolean = false,
        right: Boolean = false,
        leftId: String = "",
        rightId: String = "",
        tabColumns: Int = 8,
        tabRows: Int = 4,
    ) = CompanionSatelliteSettings(
        id = "connection-1",
        host = host,
        deviceId = "device-1",
        leftSidebarDeviceId = leftId,
        rightSidebarDeviceId = rightId,
        showInTab = tab,
        showInLeftSidebar = left,
        showInRightSidebar = right,
        tabRows = tabRows,
        tabColumns = tabColumns,
    )

    private fun slot(placement: CompanionSurfacePlacement, connectionId: String = "connection-1") =
        CompanionSurfaceSlot(connectionId, placement)

    // ── Reaching the client the view model built ────────────────────────────────

    /** The live client for [slot], out of the view model's private registry. */
    private fun clientFor(vm: CompanionSatelliteViewModel, slot: CompanionSurfaceSlot): CompanionSatelliteClient {
        val field = CompanionSatelliteViewModel::class.java.getDeclaredField("clients").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val clients = field.get(vm) as Map<CompanionSurfaceSlot, CompanionSatelliteClient>
        return clients[slot] ?: error("no client registered for $slot")
    }

    /** One of the callbacks the view model handed to the client's constructor. */
    private fun <T> callback(client: CompanionSatelliteClient, name: String): T {
        val field = CompanionSatelliteClient::class.java.getDeclaredField(name).apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        return field.get(client) as T
    }

    private fun reportStatus(vm: CompanionSatelliteViewModel, slot: CompanionSurfaceSlot, status: CompanionConnectionStatus, error: String? = null) =
        callback<(CompanionConnectionStatus, String?) -> Unit>(clientFor(vm, slot), "onStatusChanged")(status, error)

    private fun resetButtons(vm: CompanionSatelliteViewModel, slot: CompanionSurfaceSlot, count: Int) =
        callback<(Int) -> Unit>(clientFor(vm, slot), "onButtonsReset")(count)

    private fun updateButton(vm: CompanionSatelliteViewModel, slot: CompanionSurfaceSlot, update: CompanionButtonUpdate) =
        callback<(CompanionButtonUpdate) -> Unit>(clientFor(vm, slot), "onButtonUpdated")(update)

    private fun reportBrightness(vm: CompanionSatelliteViewModel, slot: CompanionSurfaceSlot, percent: Int) =
        callback<(Int) -> Unit>(clientFor(vm, slot), "onBrightnessChanged")(percent)

    // ── Device identities ───────────────────────────────────────────────────────

    @Test
    fun `the tab registers under the connection's own device id`() {
        val vm = vm()
        assertEquals("device-1", vm.deviceIdFor(settings(), CompanionSurfacePlacement.TAB))
    }

    @Test
    fun `each sidebar derives its own id so the placements do not share a page`() {
        val vm = vm()
        val s = settings()
        assertEquals("device-1-left_sidebar", vm.deviceIdFor(s, CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertEquals("device-1-right_sidebar", vm.deviceIdFor(s, CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `all three placements get different identities`() {
        val vm = vm()
        val s = settings()
        val ids = CompanionSurfacePlacement.entries.map { vm.deviceIdFor(s, it) }
        assertEquals(3, ids.toSet().size, "two placements sharing an id would fight over Companion's current page")
    }

    @Test
    fun `a sidebar id set by hand is used as given`() {
        val vm = vm()
        val s = settings(leftId = "stream-deck-left", rightId = "stream-deck-right")
        assertEquals("stream-deck-left", vm.deviceIdFor(s, CompanionSurfacePlacement.LEFT_SIDEBAR))
        assertEquals("stream-deck-right", vm.deviceIdFor(s, CompanionSurfacePlacement.RIGHT_SIDEBAR))
    }

    @Test
    fun `a blank sidebar id falls back to the derived one`() {
        val vm = vm()
        assertEquals(
            "device-1-left_sidebar",
            vm.deviceIdFor(settings(leftId = "   "), CompanionSurfacePlacement.LEFT_SIDEBAR),
            "an empty box in settings means 'derive it', not 'register with no id'"
        )
    }

    @Test
    fun `the tab ignores the sidebar overrides`() {
        val vm = vm()
        val s = settings(leftId = "stream-deck-left", rightId = "stream-deck-right")
        assertEquals("device-1", vm.deviceIdFor(s, CompanionSurfacePlacement.TAB), "the tab is the fixed anchor")
    }

    // ── Connecting ──────────────────────────────────────────────────────────────

    @Test
    fun `a connection with no host is not attempted`() {
        vm().connectAll(settings(host = "", tab = true))
        verify(exactly = 0) { anyConstructed<CompanionSatelliteClient>().connect(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `a connection with no placement enabled registers nothing`() {
        vm().connectAll(settings())
        verify(exactly = 0) { anyConstructed<CompanionSatelliteClient>().connect(any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `an enabled placement registers with its own grid shape`() {
        vm().connectAll(settings(tab = true, tabRows = 2, tabColumns = 6))
        verify(exactly = 1) {
            anyConstructed<CompanionSatelliteClient>().connect(
                host = "10.0.0.5", port = 16622, deviceId = "device-1",
                rows = 2, columns = 6, bitmapSize = 72,
                productName = "ChurchPresenter", reconnectDelayMs = 2000L,
            )
        }
    }

    @Test
    fun `every enabled placement is registered separately`() {
        vm().connectAll(settings(tab = true, left = true, right = true))
        listOf("device-1", "device-1-left_sidebar", "device-1-right_sidebar").forEach { id ->
            verify(exactly = 1) {
                anyConstructed<CompanionSatelliteClient>().connect(
                    host = any(), port = any(), deviceId = id, rows = any(), columns = any(),
                    bitmapSize = any(), productName = any(), reconnectDelayMs = any(),
                )
            }
        }
    }

    @Test
    fun `re-applying the same settings leaves a live placement alone`() {
        val vm = vm()
        val s = settings(tab = true)

        vm.connectAll(s)
        vm.connectAll(s)
        vm.connectAll(s)

        verify(exactly = 1) {
            anyConstructed<CompanionSatelliteClient>().connect(
                host = any(), port = any(), deviceId = "device-1", rows = any(), columns = any(),
                bitmapSize = any(), productName = any(), reconnectDelayMs = any(),
            )
        }
    }

    @Test
    fun `enabling a second placement does not disturb the first`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        vm.connectAll(settings(tab = true, left = true))

        verify(exactly = 1) {
            anyConstructed<CompanionSatelliteClient>().connect(
                host = any(), port = any(), deviceId = "device-1", rows = any(), columns = any(),
                bitmapSize = any(), productName = any(), reconnectDelayMs = any(),
            )
        }
        verify(exactly = 1) {
            anyConstructed<CompanionSatelliteClient>().connect(
                host = any(), port = any(), deviceId = "device-1-left_sidebar", rows = any(), columns = any(),
                bitmapSize = any(), productName = any(), reconnectDelayMs = any(),
            )
        }
    }

    @Test
    fun `editing the grid shape re-registers that placement`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, tabColumns = 8))

        vm.connectAll(settings(tab = true, tabColumns = 2))

        verify(exactly = 1) {
            anyConstructed<CompanionSatelliteClient>().connect(
                host = any(), port = any(), deviceId = "device-1", rows = any(), columns = 2,
                bitmapSize = any(), productName = any(), reconnectDelayMs = any(),
            )
        }
    }

    @Test
    fun `unchecking a placement tears its registration down`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.LEFT_SIDEBAR), 4)

        vm.connectAll(settings(tab = true)) // left sidebar unchecked

        verify(atLeast = 1) { anyConstructed<CompanionSatelliteClient>().dispose() }
        assertTrue(
            vm.buttonsFor(slot(CompanionSurfacePlacement.LEFT_SIDEBAR)).isEmpty(),
            "a hidden placement must not keep a stale grid around"
        )
    }

    @Test
    fun `disconnecting pauses every placement of that connection`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))

        vm.disconnectAll(settings(tab = true, left = true))

        verify(exactly = 2) { anyConstructed<CompanionSatelliteClient>().disconnect() }
    }

    // ── Pressing buttons ────────────────────────────────────────────────────────

    @Test
    fun `a press is forwarded to the placement it came from`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        vm.pressButton(slot(CompanionSurfacePlacement.TAB), 7)

        verify(exactly = 1) { anyConstructed<CompanionSatelliteClient>().pressButton(7) }
    }

    @Test
    fun `a press for a placement that is not connected does nothing`() {
        val vm = vm()
        vm.pressButton(slot(CompanionSurfacePlacement.TAB), 3)
        verify(exactly = 0) { anyConstructed<CompanionSatelliteClient>().pressButton(any()) }
    }

    // ── Button grids ────────────────────────────────────────────────────────────

    @Test
    fun `a placement's grid starts empty and is the same list each time`() {
        val vm = vm()
        val grid = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))
        assertTrue(grid.isEmpty())
        assertSame(grid, vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)), "the UI observes one list per placement")
    }

    @Test
    fun `each placement has its own grid`() {
        val vm = vm()
        assertNotSame(
            vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)),
            vm.buttonsFor(slot(CompanionSurfacePlacement.LEFT_SIDEBAR)),
        )
    }

    @Test
    fun `companion sizing the surface fills the grid with blank buttons`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 6)

        val grid = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))
        assertEquals(6, grid.size)
        assertEquals(listOf(0, 1, 2, 3, 4, 5), grid.map { it.index })
        assertTrue(grid.all { it.text.isEmpty() && it.bitmap == null })
    }

    @Test
    fun `re-sizing the surface replaces the grid rather than growing it`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 6)
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 2)

        assertEquals(2, vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)).size)
    }

    @Test
    fun `a button update lands on that button`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 4)

        updateButton(
            vm, slot(CompanionSurfacePlacement.TAB),
            CompanionButtonUpdate(index = 2, text = "Go Live", color = "#FF0000", textColor = "#FFFFFF", pressed = true),
        )

        val button = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))[2]
        assertEquals(2, button.index)
        assertEquals("Go Live", button.text)
        assertEquals("#FF0000", button.color)
        assertEquals("#FFFFFF", button.textColor)
        assertTrue(button.pressed)
    }

    @Test
    fun `updating one button leaves the rest alone`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 3)

        updateButton(vm, slot(CompanionSurfacePlacement.TAB), CompanionButtonUpdate(index = 1, text = "Middle"))

        val grid = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))
        assertEquals(listOf("", "Middle", ""), grid.map { it.text })
    }

    @Test
    fun `an update for a button outside the grid is dropped`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 2)

        updateButton(vm, slot(CompanionSurfacePlacement.TAB), CompanionButtonUpdate(index = 9, text = "Ghost"))
        updateButton(vm, slot(CompanionSurfacePlacement.TAB), CompanionButtonUpdate(index = -1, text = "Ghost"))

        val grid = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))
        assertEquals(2, grid.size, "a larger page on Companion's side must not grow this surface")
        assertTrue(grid.none { it.text == "Ghost" })
    }

    @Test
    fun `an update arriving before the surface is sized is dropped`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        updateButton(vm, slot(CompanionSurfacePlacement.TAB), CompanionButtonUpdate(index = 0, text = "Early"))

        assertTrue(vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)).isEmpty())
    }

    @Test
    fun `two placements keep their own buttons`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 2)
        resetButtons(vm, slot(CompanionSurfacePlacement.LEFT_SIDEBAR), 2)

        updateButton(vm, slot(CompanionSurfacePlacement.TAB), CompanionButtonUpdate(index = 0, text = "Tab button"))

        assertEquals("Tab button", vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))[0].text)
        assertEquals("", vm.buttonsFor(slot(CompanionSurfacePlacement.LEFT_SIDEBAR))[0].text, "each surface is its own page")
    }

    // ── Connection status ───────────────────────────────────────────────────────

    @Test
    fun `a placement has no status until its client reports one`() {
        val vm = vm()
        assertNull(vm.connectionStates[slot(CompanionSurfacePlacement.TAB)])
    }

    @Test
    fun `a reported status is shown against that placement`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)

        val state = vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB))
        assertEquals(CompanionConnectionStatus.CONNECTED, state.status)
        assertEquals("", state.errorMessage)
        assertEquals(slot(CompanionSurfacePlacement.TAB), state.slot)
    }

    @Test
    fun `an error carries its message`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.ERROR, "Connection refused")

        val state = vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB))
        assertEquals(CompanionConnectionStatus.ERROR, state.status)
        assertEquals("Connection refused", state.errorMessage)
    }

    @Test
    fun `recovering clears the previous error message`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.ERROR, "Connection refused")

        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)

        assertEquals(
            "",
            vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB)).errorMessage,
            "a stale error beside a connected chip reads as still broken"
        )
    }

    @Test
    fun `each placement reports its own status`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))

        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)
        reportStatus(vm, slot(CompanionSurfacePlacement.LEFT_SIDEBAR), CompanionConnectionStatus.ERROR, "no route to host")

        assertEquals(CompanionConnectionStatus.CONNECTED, vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB)).status)
        assertEquals(CompanionConnectionStatus.ERROR, vm.connectionStates.getValue(slot(CompanionSurfacePlacement.LEFT_SIDEBAR)).status)
    }

    @Test
    fun `brightness from companion is recorded without disturbing the status`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)

        reportBrightness(vm, slot(CompanionSurfacePlacement.TAB), 40)

        val state = vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB))
        assertEquals(40, state.brightness)
        assertEquals(CompanionConnectionStatus.CONNECTED, state.status)
    }

    // ── Failure reporting ───────────────────────────────────────────────────────

    @Test
    fun `an unreachable host is reported once, not once per retry`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        val tab = slot(CompanionSurfacePlacement.TAB)

        // The client's reconnect loop cycles this pair every couple of seconds, indefinitely.
        repeat(5) {
            reportStatus(vm, tab, CompanionConnectionStatus.CONNECTING)
            reportStatus(vm, tab, CompanionConnectionStatus.ERROR, "Connection refused")
        }

        verify(exactly = 1) { CrashReporter.breadcrumb(any(), any(), SentryLevel.WARNING) }
    }

    @Test
    fun `a failure after a genuine recovery is reported again`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        val tab = slot(CompanionSurfacePlacement.TAB)

        reportStatus(vm, tab, CompanionConnectionStatus.ERROR, "Connection refused")
        reportStatus(vm, tab, CompanionConnectionStatus.CONNECTED)
        reportStatus(vm, tab, CompanionConnectionStatus.ERROR, "Connection refused")

        verify(exactly = 2) { CrashReporter.breadcrumb(any(), any(), SentryLevel.WARNING) }
    }

    @Test
    fun `connecting on its own is never reported as a failure`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))

        repeat(3) { reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTING) }

        verify(exactly = 0) { CrashReporter.breadcrumb(any(), any(), SentryLevel.WARNING) }
    }

    // ── Tearing down ────────────────────────────────────────────────────────────

    @Test
    fun `disabling one placement leaves the others connected`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 2)
        resetButtons(vm, slot(CompanionSurfacePlacement.LEFT_SIDEBAR), 2)
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)
        reportStatus(vm, slot(CompanionSurfacePlacement.LEFT_SIDEBAR), CompanionConnectionStatus.CONNECTED)

        vm.disableSlot("connection-1", CompanionSurfacePlacement.LEFT_SIDEBAR)

        assertNull(vm.connectionStates[slot(CompanionSurfacePlacement.LEFT_SIDEBAR)])
        assertTrue(vm.buttonsFor(slot(CompanionSurfacePlacement.LEFT_SIDEBAR)).isEmpty())
        assertEquals(2, vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)).size, "the tab was not touched")
        assertEquals(
            CompanionConnectionStatus.CONNECTED,
            vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB)).status,
        )
    }

    @Test
    fun `a disabled placement gets a fresh grid if it comes back`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        resetButtons(vm, slot(CompanionSurfacePlacement.TAB), 4)
        val original = vm.buttonsFor(slot(CompanionSurfacePlacement.TAB))

        vm.disableSlot("connection-1", CompanionSurfacePlacement.TAB)

        assertNotSame(original, vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)), "the old grid must not be reused")
    }

    @Test
    fun `removing a connection tears down all of its placements`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true, right = true))
        CompanionSurfacePlacement.entries.forEach { reportStatus(vm, slot(it), CompanionConnectionStatus.CONNECTED) }

        vm.removeConnection("connection-1")

        assertTrue(vm.connectionStates.isEmpty())
        verify(exactly = 3) { anyConstructed<CompanionSatelliteClient>().dispose() }
    }

    @Test
    fun `removing one connection leaves another one alone`() {
        val vm = vm()
        vm.connectAll(settings(tab = true))
        val second = settings(tab = true).copy(id = "connection-2", deviceId = "device-2")
        vm.connectAll(second)
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB, "connection-2"), CompanionConnectionStatus.CONNECTED)

        vm.removeConnection("connection-1")

        assertNull(vm.connectionStates[slot(CompanionSurfacePlacement.TAB)])
        assertEquals(
            CompanionConnectionStatus.CONNECTED,
            vm.connectionStates.getValue(slot(CompanionSurfacePlacement.TAB, "connection-2")).status,
            "two Companion instances are configured independently"
        )
    }

    @Test
    fun `disposing shuts every connection down`() {
        val vm = vm()
        vm.connectAll(settings(tab = true, left = true))
        reportStatus(vm, slot(CompanionSurfacePlacement.TAB), CompanionConnectionStatus.CONNECTED)

        vm.dispose()

        assertTrue(vm.connectionStates.isEmpty())
        assertTrue(vm.buttonsFor(slot(CompanionSurfacePlacement.TAB)).isEmpty())
        verify(exactly = 2) { anyConstructed<CompanionSatelliteClient>().dispose() }
    }
}
