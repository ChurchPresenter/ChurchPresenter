package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MainDesktopMirrorGateTest {

    @Test
    fun `a connected follower mirrors the primary`() {
        assertTrue(shouldMirrorFromPrimary(InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED))
    }

    @Test
    fun `a controller drives the primary instead of following it`() {
        assertFalse(
            shouldMirrorFromPrimary(InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLER),
            "mirroring here would replace the operator's own library with the far end's",
        )
    }

    @Test
    fun `nothing is mirrored until the link is actually connected`() {
        listOf(
            InstanceLinkStatus.DISCONNECTED,
            InstanceLinkStatus.CONNECTING,
            InstanceLinkStatus.ERROR,
        ).forEach { status ->
            assertFalse(
                shouldMirrorFromPrimary(status, InstanceLinkRole.CONTROLLED),
                "$status is not a live link",
            )
        }
    }

    @Test
    fun `a controller does not mirror in any connection state`() {
        InstanceLinkStatus.entries.forEach { status ->
            assertFalse(shouldMirrorFromPrimary(status, InstanceLinkRole.CONTROLLER))
        }
    }

    @Test
    fun `only one of the four combinations mirrors`() {
        val mirroring = InstanceLinkStatus.entries.flatMap { status ->
            InstanceLinkRole.entries.map { role -> Triple(status, role, shouldMirrorFromPrimary(status, role)) }
        }.filter { it.third }

        assertTrue(mirroring.size == 1, "expected exactly one mirroring combination, got $mirroring")
        assertTrue(mirroring.single().first == InstanceLinkStatus.CONNECTED)
        assertTrue(mirroring.single().second == InstanceLinkRole.CONTROLLED)
    }
}
