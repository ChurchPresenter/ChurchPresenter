package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.CompanionSatelliteSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * [resolveSelectedConnectionId] backs both the left and right Companion Surface sidebars — two
 * near-identical blocks in [MainDesktop] that pick which connection's chip is active. It handles
 * both the initial pick (called with a null id) and keeping the selection valid as the connection
 * list changes underneath it (a connection gets renamed, disabled, or its `showInLeftSidebar` flag
 * flips off).
 *
 * The bug this guards against: without re-resolving on every list change, a removed connection
 * would leave the panel showing nothing — `find { it.id == staleId }` returns null — instead of
 * falling back to whatever is still configured.
 */
class MainDesktopSidebarSelectionTest {

    private fun connection(id: String) = CompanionSatelliteSettings(id = id, host = "192.168.1.10")

    @Test
    fun `with no prior selection, the first connection is picked`() {
        val connections = listOf(connection("a"), connection("b"))
        assertEquals("a", resolveSelectedConnectionId(null, connections))
    }

    @Test
    fun `with no connections at all, there is nothing to select`() {
        assertNull(resolveSelectedConnectionId(null, emptyList()))
        assertNull(resolveSelectedConnectionId("stale-id", emptyList()))
    }

    @Test
    fun `a still-valid selection is left untouched`() {
        val connections = listOf(connection("a"), connection("b"), connection("c"))
        assertEquals("b", resolveSelectedConnectionId("b", connections))
    }

    @Test
    fun `a selection whose connection was removed falls back to the first remaining one`() {
        val connections = listOf(connection("a"), connection("c"))
        assertEquals("a", resolveSelectedConnectionId("b", connections))
    }

    @Test
    fun `the list shrinking to empty leaves nothing selected rather than a dangling id`() {
        assertNull(resolveSelectedConnectionId("a", emptyList()))
    }

    @Test
    fun `a newly added connection never steals the selection from a still-valid one`() {
        val connections = listOf(connection("a"), connection("b"))
        assertEquals("a", resolveSelectedConnectionId("a", connections))
    }
}
