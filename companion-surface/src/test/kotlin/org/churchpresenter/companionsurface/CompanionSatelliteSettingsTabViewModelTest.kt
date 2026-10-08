@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.companionsurface

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import org.churchpresenter.companionsurface.CompanionSatelliteViewModel
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The half of a connection card that only exists when a `CompanionSatelliteViewModel` is supplied:
 * the Connect button, its enablement rule, and the status line beside it.
 *
 * Every other class here passes `viewModel = null`, which is how the tab is rendered in tests that
 * only care about settings — and it means none of this block runs. With a view model supplied but
 * nothing connected, the card falls back to a default state, so the disconnected path and the
 * button's enablement can be driven with no socket involved at all.
 *
 * **Connect is pressed exactly once**, in the test that covers the device-ID fallback, because that
 * branch cannot be reached any other way. It points the connection at a closed port on loopback, so
 * the attempt fails immediately rather than hanging, and the view model is torn down afterwards.
 */
class CompanionSatelliteSettingsTabViewModelTest {

    private val viewModels = mutableListOf<CompanionSatelliteViewModel>()

    @AfterTest
    fun tearDown() {
        viewModels.forEach { vm -> runCatching { vm.dispose() } }
        viewModels.clear()
    }

    private fun viewModel(): CompanionSatelliteViewModel =
        CompanionSatelliteViewModel().also { viewModels += it }

    // ── The block only exists with a view model ─────────────────────────────────────────────────

    @Test
    fun `without a view model the card offers no Connect button`() {
        satelliteTab(initial = satelliteSettings(connection { copy(host = "10.0.0.1", showInTab = true) })) { _ ->
            onNodeWithText(SatLabel.CONNECT).assertDoesNotExist()
            onNodeWithText(SatLabel.DISCONNECTED).assertDoesNotExist()
        }
    }

    @Test
    fun `with a view model the card offers Connect and a status`() {
        val fixture = satelliteSettings(connection { copy(host = "10.0.0.1", showInTab = true) })
        satelliteTab(initial = fixture, viewModel = viewModel()) { _ ->
            onNodeWithText(SatLabel.CONNECT).assertExists("an unconnected card must offer Connect")
            onNodeWithText(SatLabel.DISCONNECT).assertDoesNotExist()
            onNodeWithText(SatLabel.DISCONNECTED).assertExists("and say it is disconnected")
        }
    }

    // ── When Connect is usable ──────────────────────────────────────────────────────────────────

    /**
     * `enabled = host.isNotBlank() && primary != null` — a connection needs somewhere to dial *and*
     * a placement to draw on. All four combinations are covered, because either half alone would
     * leave the button offering something that cannot work.
     */
    @Test
    fun `Connect is enabled only with both a host and a placement`() {
        val cases = listOf(
            Triple("10.0.0.1", true, true),
            Triple("10.0.0.1", false, false),
            Triple("", true, false),
            Triple("", false, false),
        )
        for ((host, placed, expectEnabled) in cases) {
            val fixture = satelliteSettings(connection { copy(host = host, showInTab = placed) })
            satelliteTab(initial = fixture, viewModel = viewModel()) { _ ->
                val button = onNodeWithText(SatLabel.CONNECT).performScrollTo()
                if (expectEnabled) {
                    // host="$host" placed=$placed must be dialable.
                    button.assertIsEnabled()
                } else {
                    button.assertIsNotEnabled()
                }
            }
        }
    }

    /** Any placement will do — the primary is simply the first one switched on. */
    @Test
    fun `a sidebar-only connection is dialable too`() {
        val fixture = satelliteSettings(
            connection { copy(host = "10.0.0.1", showInTab = false, showInRightSidebar = true) },
        )
        satelliteTab(initial = fixture, viewModel = viewModel()) { _ ->
            onNodeWithText(SatLabel.CONNECT).performScrollTo().assertIsEnabled()
        }
    }

    // ── The device-ID fallback ──────────────────────────────────────────────────────────────────

    /**
     * Companion rejects a registration with no DEVICEID, so pressing Connect with the field cleared
     * generates one and stores it rather than failing on the wire. This is the one place the button
     * is actually pressed; the connection points at a closed loopback port so the attempt fails at
     * once instead of hanging.
     */
    @Test
    fun `Connect generates a device ID when the field has been cleared`() {
        val port = java.net.ServerSocket(0).use { it.localPort } // closed again immediately
        val fixture = satelliteSettings(
            connection { copy(host = "127.0.0.1", port = port, deviceId = "", showInTab = true) },
        )
        satelliteTab(initial = fixture, viewModel = viewModel()) { get ->
            assertEquals("", get().onlyConnection().deviceId, "fixture: the device ID starts cleared")

            onNodeWithText(SatLabel.CONNECT).performScrollTo().performClick()
            waitForIdle()

            val generated = get().onlyConnection().deviceId
            assertTrue(generated.isNotBlank(), "pressing Connect must fill in a device ID")
            assertTrue(
                runCatching { java.util.UUID.fromString(generated) }.isSuccess,
                "and it must be a UUID, as a brand-new connection gets, was \"$generated\"",
            )
        }
    }

    /**
     * A connection that already has a device ID keeps it — Connect must not churn the field.
     *
     * The attempt is asserted as well as the unchanged ID. "The device ID did not change" holds just
     * as well against a button wired to nothing, so on its own this would pass either way; the slot
     * the view model registers is what proves the click did something.
     */
    @Test
    fun `Connect leaves an existing device ID alone`() {
        val port = java.net.ServerSocket(0).use { it.localPort }
        val vm = viewModel()
        val fixture = satelliteSettings(
            connection { copy(host = "127.0.0.1", port = port, deviceId = "my-own-device", showInTab = true) },
        )
        satelliteTab(initial = fixture, viewModel = vm) { get ->
            assertTrue(vm.connectionStates.isEmpty(), "fixture: nothing is registered before the click")

            onNodeWithText(SatLabel.CONNECT).performScrollTo().performClick()
            waitForIdle()

            assertTrue(
                vm.connectionStates.isNotEmpty(),
                "the click must register an attempt, or this test proves nothing about the device ID",
            )
            assertEquals(
                "my-own-device",
                get().onlyConnection().deviceId,
                "an operator's own device ID must survive pressing Connect",
            )
        }
    }

    // ── Several cards ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `each card gets its own Connect button and enablement`() {
        val fixture = satelliteSettings(
            connection { copy(name = "Dialable", host = "10.0.0.1", showInTab = true) },
            connection { copy(name = "Not dialable", host = "", showInTab = true) },
        )
        satelliteTab(initial = fixture, viewModel = viewModel()) { _ ->
            val buttons = onAllNodes(androidx.compose.ui.test.hasText(SatLabel.CONNECT))
            buttons[0].performScrollTo().assertIsEnabled()
            buttons[1].performScrollTo().assertIsNotEnabled()
        }
    }
}
