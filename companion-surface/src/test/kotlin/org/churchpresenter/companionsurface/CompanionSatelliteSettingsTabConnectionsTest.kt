@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package org.churchpresenter.companionsurface

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Drives the list itself: adding connections, removing them, and keeping several apart.
 *
 * Each card edits its connection by **id**, not by position — `updateConnection(id) { … }` rebuilds
 * the list mapping over it — so the failure worth guarding against is an edit landing on the wrong
 * card once more than one exists. Every test with two connections therefore asserts both.
 */
class CompanionSatelliteSettingsTabConnectionsTest {

    /**
     * **The last connection cannot be removed.** `canRemove = connections.size > 1`, so a card only
     * offers Remove while there is another to fall back on — the list can never be emptied from the
     * UI. Pinned because it is the reason the button comes and goes rather than merely disabling.
     */
    @Test
    fun `a fresh tab holds one connection, which offers no Remove`() = satelliteTab { get ->
        assertEquals(1, get().companionSatelliteConnections.size)
        assertEquals("Companion", get().onlyConnection().name)
        removeButtons().assertCountEquals(0)
    }

    @Test
    fun `Remove appears as soon as there is a second connection`() = satelliteTab { _ ->
        removeButtons().assertCountEquals(0)

        onNodeWithText(SatLabel.ADD).performScrollTo().performClick()
        waitForIdle()

        removeButtons().assertCountEquals(2)
    }

    @Test
    fun `Add appends a connection with its own id`() = satelliteTab { get ->
        val firstId = get().onlyConnection().id

        onNodeWithText(SatLabel.ADD).performScrollTo().performClick()
        waitForIdle()

        val connections = get().companionSatelliteConnections
        assertEquals(2, connections.size, "the add button must append")
        assertTrue(connections[0].id == firstId, "the first connection must keep its id")
        assertTrue(connections[1].id != firstId, "the new one must get an id of its own")
        removeButtons().assertCountEquals(2)
    }

    @Test
    fun `Add can be used more than once`() = satelliteTab { get ->
        repeat(3) {
            onNodeWithText(SatLabel.ADD).performScrollTo().performClick()
            waitForIdle()
        }
        val ids = get().companionSatelliteConnections.map { it.id }
        assertEquals(4, ids.size, "three additions on top of the default one")
        assertEquals(ids.size, ids.toSet().size, "every connection must have a distinct id")
    }

    /** The device IDs are generated too, so two cards never share a Companion surface by accident. */
    @Test
    fun `an added connection gets its own device id`() = satelliteTab { get ->
        onNodeWithText(SatLabel.ADD).performScrollTo().performClick()
        waitForIdle()

        val connections = get().companionSatelliteConnections
        assertTrue(
            connections[0].deviceId != connections[1].deviceId,
            "two connections sharing a device id would fight over the same surface",
        )
    }

    // ── Editing the right card ──────────────────────────────────────────────────────────────────

    @Test
    fun `editing the first card leaves the second alone`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First", host = "10.0.0.1") },
            connection { copy(name = "Second", host = "10.0.0.2") },
        )
        satelliteTab(initial = fixture) { get ->
            typeInto(SatLabel.NAME, "First Renamed", ordinal = 0)

            val connections = get().companionSatelliteConnections
            assertEquals("First Renamed", connections[0].name, "the first card must take the edit")
            assertEquals("Second", connections[1].name, "the second must be untouched")
            assertEquals("10.0.0.2", connections[1].host)
        }
    }

    @Test
    fun `editing the second card leaves the first alone`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First", host = "10.0.0.1") },
            connection { copy(name = "Second", host = "10.0.0.2") },
        )
        satelliteTab(initial = fixture) { get ->
            typeInto(SatLabel.HOST, "192.168.9.9", ordinal = 1)

            val connections = get().companionSatelliteConnections
            assertEquals("192.168.9.9", connections[1].host, "the second card must take the edit")
            assertEquals("10.0.0.1", connections[0].host, "the first must be untouched")
            assertEquals("First", connections[0].name)
        }
    }

    /** Placements are per connection too, so ticking one card's must not tick the other's. */
    @Test
    fun `ticking a placement on one card leaves the other card alone`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First") },
            connection { copy(name = "Second") },
        )
        satelliteTab(initial = fixture) { get ->
            placementCheckbox(Placement.TAB, card = 1).performScrollTo().performClick()
            waitForIdle()

            val connections = get().companionSatelliteConnections
            assertEquals(true, connections[1].showInTab, "the second card must take the tick")
            assertEquals(false, connections[0].showInTab, "the first must be untouched")
        }
    }

    // ── Removing ────────────────────────────────────────────────────────────────────────────────

    @Test
    fun `Remove deletes the card it belongs to`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First") },
            connection { copy(name = "Second") },
        )
        satelliteTab(initial = fixture) { get ->
            removeButtons()[0].performScrollTo().performClick()
            waitForIdle()

            val connections = get().companionSatelliteConnections
            assertEquals(1, connections.size, "one connection must be gone")
            assertEquals("Second", connections.single().name, "and it must be the first one")
            removeButtons().assertCountEquals(0)
        }
    }

    /** Removing down to one leaves that one unremovable, so the list cannot be emptied. */
    @Test
    fun `the list cannot be emptied from the UI`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First") },
            connection { copy(name = "Second") },
        )
        satelliteTab(initial = fixture) { get ->
            removeButtons()[0].performScrollTo().performClick()
            waitForIdle()
            assertEquals(1, get().companionSatelliteConnections.size)

            removeButtons().assertCountEquals(0)
            onNodeWithText(SatLabel.ADD).assertExists("only Add is left once one connection remains")
            assertTrue(
                get().companionSatelliteConnections.isNotEmpty(),
                "there is no way to reach an empty list from here",
            )
        }
    }

    @Test
    fun `removing the second card leaves the first intact`() {
        val fixture = satelliteSettings(
            connection { copy(name = "First", host = "10.0.0.1") },
            connection { copy(name = "Second", host = "10.0.0.2") },
        )
        satelliteTab(initial = fixture) { get ->
            removeButtons()[1].performScrollTo().performClick()
            waitForIdle()

            val remaining = get().companionSatelliteConnections.single()
            assertEquals("First", remaining.name)
            assertEquals("10.0.0.1", remaining.host, "the survivor must keep its own values")
        }
    }
}
