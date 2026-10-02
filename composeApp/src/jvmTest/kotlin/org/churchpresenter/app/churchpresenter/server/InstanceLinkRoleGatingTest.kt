package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.settings.InstanceLinkRole
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The three decisions that separate the two Instance Link roles: whether this instance mirrors the
 * primary's output, sources content from it, and replaces its backgrounds with it. Where it plays a
 * schedule item's media from is `:media`'s `FollowerMediaUrlTest`.
 *
 * These used to be conditions inlined in `main.kt`'s root composable, unreachable from any test, and
 * they were wrong: everything that drives the *presenter* was gated on the connection alone, so a
 * Controller — which is defined as staying independent — mirrored the primary anyway. That is a
 * feedback loop rather than a cosmetic slip. A Controller going live sets its own presenter *and*
 * sends the command; the primary then broadcasts the resulting state back, and the Controller
 * overwrote what it had just put up with the primary's version of it. The primary's connect snapshot
 * replays its live state to every client, so it happened at connect time too, before the operator
 * touched anything.
 *
 * Hence the shape of this suite: every predicate is asserted over the **whole** status × role
 * product rather than at the one or two points a bug was found, because the property that matters is
 * the negative one — a Controller never follows, under any status.
 */
class InstanceLinkRoleGatingTest {


    @Test
    fun `only a Controlled instance mirrors the primary's output`() {
        assertTrue(shouldMirrorRemoteOutput(InstanceLinkRole.CONTROLLED))
        assertFalse(shouldMirrorRemoteOutput(InstanceLinkRole.CONTROLLER))
    }

    @Test
    fun `remote content is sourced only while a Controlled instance is connected`() {
        for (status in InstanceLinkStatus.entries) {
            for (role in InstanceLinkRole.entries) {
                val expected = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED
                assertEquals(
                    expected,
                    shouldUseRemoteContent(status, role),
                    "shouldUseRemoteContent($status, $role)"
                )
            }
        }
    }

    @Test
    fun `a Controller never sources remote content, whatever the connection is doing`() {
        for (status in InstanceLinkStatus.entries) {
            assertFalse(
                shouldUseRemoteContent(status, InstanceLinkRole.CONTROLLER),
                "a Controller must keep its own content while $status"
            )
        }
    }

    @Test
    fun `backgrounds are mirrored only on an explicit opt-in`() {
        assertTrue(
            shouldMirrorRemoteBackgrounds(
                InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED, mirrorBackgrounds = true
            )
        )
        // Off by default — backgrounds are usually venue-specific, so a follower keeps its own.
        assertFalse(
            shouldMirrorRemoteBackgrounds(
                InstanceLinkStatus.CONNECTED, InstanceLinkRole.CONTROLLED, mirrorBackgrounds = false
            )
        )
    }

    @Test
    fun `the backgrounds opt-in cannot override the role or the connection`() {
        for (status in InstanceLinkStatus.entries) {
            for (role in InstanceLinkRole.entries) {
                val expected = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED
                assertEquals(
                    expected,
                    shouldMirrorRemoteBackgrounds(status, role, mirrorBackgrounds = true),
                    "shouldMirrorRemoteBackgrounds($status, $role, opted in)"
                )
            }
        }
    }
}
