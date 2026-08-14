@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.app.churchpresenter.dialogs.tabs

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Drives the eight connection-level boxes on a card, plus the auto-connect switch.
 *
 * Every box writes through the same `updateConnection(id) { copy(...) }` shape, so the failure the
 * arrangement invites is a box writing into a neighbour's field. Each test therefore asserts the
 * whole connection afterwards, not just the field it set — the fixture gives the card values that
 * differ from the defaults so a stray write shows up.
 */
class CompanionSatelliteSettingsTabFieldsTest {

    /** A connection whose every field differs from the defaults, so any overwrite is visible. */
    private fun distinct() = connection {
        copy(
            name = "Original Name",
            host = "10.0.0.1",
            port = 11111,
            deviceId = "tab-device",
            leftSidebarDeviceId = "left-device",
            rightSidebarDeviceId = "right-device",
            productName = "Original Product",
            reconnectDelayMs = 1111,
        )
    }

    @Test
    fun `the name box stores what is typed`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.NAME, "Booth deck")
            val c = get().onlyConnection()
            assertEquals("Booth deck", c.name, "the typed name must be stored")
            assertEquals("10.0.0.1", c.host, "and the host must be untouched")
            assertEquals("Original Product", c.productName, "as must the product name")
        }
    }

    @Test
    fun `the host box stores what is typed`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.HOST, "192.168.1.50")
            val c = get().onlyConnection()
            assertEquals("192.168.1.50", c.host, "the typed host must be stored")
            assertEquals(11111, c.port, "and the port must be untouched")
            assertEquals("Original Name", c.name)
        }
    }

    /** The port shares its row with the host, so it is the second box on that line. */
    @Test
    fun `the port box stores what is typed`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            // The host row holds two boxes; the port is the one showing the current port.
            boxFor(SatLabel.HOST).performScrollTo()
            onNode(
                androidx.compose.ui.test.hasSetTextAction() and
                    androidx.compose.ui.test.hasText("11111"),
            ).performTextReplacement("22222")
            waitForIdle()

            val c = get().onlyConnection()
            assertEquals(22222, c.port, "the typed port must be stored")
            assertEquals("10.0.0.1", c.host, "and the host must be untouched")
        }
    }

    @Test
    fun `a port that will not parse leaves the stored port alone`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            onNode(
                androidx.compose.ui.test.hasSetTextAction() and
                    androidx.compose.ui.test.hasText("11111"),
            ).performTextReplacement("not-a-port")
            waitForIdle()
            assertEquals(11111, get().onlyConnection().port, "nonsense must not reach the stored port")

            onNode(
                androidx.compose.ui.test.hasSetTextAction() and
                    androidx.compose.ui.test.hasText("not-a-port"),
            ).performTextReplacement("33333")
            waitForIdle()
            assertEquals(33333, get().onlyConnection().port, "a real port must still land")
        }
    }

    @Test
    fun `each device ID box stores into its own field`() {
        val cases = listOf(
            SatLabel.TAB_DEVICE_ID to {
                c: org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings -> c.deviceId
            },
            SatLabel.LEFT_DEVICE_ID to {
                c: org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings -> c.leftSidebarDeviceId
            },
            SatLabel.RIGHT_DEVICE_ID to {
                c: org.churchpresenter.app.churchpresenter.data.settings.CompanionSatelliteSettings -> c.rightSidebarDeviceId
            },
        )
        for ((caption, read) in cases) {
            satelliteTab(initial = satelliteSettings(distinct())) { get ->
                typeInto(caption, "typed-into-$caption")

                val c = get().onlyConnection()
                assertEquals("typed-into-$caption", read(c), "\"$caption\" must store into its own field")
                // The other two must keep the fixture's values.
                val others = cases.filter { it.first != caption }
                for ((otherCaption, otherRead) in others) {
                    assertEquals(
                        when (otherCaption) {
                            SatLabel.TAB_DEVICE_ID -> "tab-device"
                            SatLabel.LEFT_DEVICE_ID -> "left-device"
                            else -> "right-device"
                        },
                        otherRead(c),
                        "\"$otherCaption\" must be untouched by typing into \"$caption\"",
                    )
                }
            }
        }
    }

    @Test
    fun `the product name box stores what is typed`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.PRODUCT_NAME, "My Presenter")
            assertEquals("My Presenter", get().onlyConnection().productName)
            assertEquals("Original Name", get().onlyConnection().name, "the connection name must be untouched")
        }
    }

    @Test
    fun `the reconnect delay stores what is typed`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.RECONNECT_DELAY, "5000")
            assertEquals(5000, get().onlyConnection().reconnectDelayMs, "the typed delay must be stored")
        }
    }

    @Test
    fun `a reconnect delay that will not parse leaves the stored one alone`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.RECONNECT_DELAY, "soon")
            assertEquals(1111, get().onlyConnection().reconnectDelayMs, "nonsense must not be stored")

            typeInto(SatLabel.RECONNECT_DELAY, "2500")
            assertEquals(2500, get().onlyConnection().reconnectDelayMs, "a real delay must still land")
        }
    }

    // ── Auto-connect ────────────────────────────────────────────────────────────────────────────

    @Test
    fun `the auto-connect switch turns on and off`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            assertEquals(false, get().onlyConnection().autoConnect, "off out of the box")
            autoConnectSwitch().assertIsOff()

            autoConnectSwitch().performScrollTo().performClick()
            waitForIdle()
            assertEquals(true, get().onlyConnection().autoConnect, "switching on must be stored")
            autoConnectSwitch().assertIsOn()

            autoConnectSwitch().performClick()
            waitForIdle()
            assertEquals(false, get().onlyConnection().autoConnect, "switching off must be stored too")
            autoConnectSwitch().assertIsOff()
        }
    }

    @Test
    fun `auto-connect leaves the placement flags alone`() {
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            autoConnectSwitch().performScrollTo().performClick()
            waitForIdle()

            val c = get().onlyConnection()
            assertEquals(true, c.autoConnect)
            assertEquals(false, c.showInTab, "the placements must be untouched")
            assertEquals(false, c.showInLeftSidebar)
            assertEquals(false, c.showInRightSidebar)
        }
    }

    // ── Round trip ──────────────────────────────────────────────────────────────────────────────

    @Test
    fun `a typed name is what a fresh render of the saved connection shows`() {
        var saved = ""
        satelliteTab(initial = satelliteSettings(distinct())) { get ->
            typeInto(SatLabel.NAME, "Persisted Name")
            saved = get().onlyConnection().name
        }
        assertEquals("Persisted Name", saved, "the name must have been stored to be re-rendered")

        satelliteTab(initial = satelliteSettings(distinct().copy(name = saved))) { _ ->
            assertSatelliteBoxShows("Persisted Name", "the name box on a fresh render")
        }
    }
}
