package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import org.churchpresenter.companionserver.RemoteAccess

/**
 * What an approval-only remote request turns into: a refusal, a toast after the fact, or a prompt.
 *
 * Pairs with [RemoteAccessDecisionTest], which covers *whether* a device is trusted. This covers what
 * that verdict means for the request. The two were written out by hand together in eight handlers in
 * `main.kt`, all at 0% coverage.
 *
 * The property worth having a test for is the last one here: `RemoteActivityNotification` and
 * `RemoteEvent` carry **identical fields**, and each handler used to build one in its approve branch
 * and the other in its prompt branch, separately, from the same values. Nothing stopped the toast and
 * the approval dialog describing the same request differently — a drift no compiler and no type
 * checker would catch, and one an operator would only notice mid-service.
 */
class RemoteApprovalTest {

    private fun approval(access: RemoteAccess) = remoteApproval(
        access,
        type = RemoteEventType.QA_APPROVE,
        title = "Is the car park full?",
        detail = "from row 3",
        clientId = "phone-7",
        clientLabel = "Sam's phone",
    )

    // ── One outcome per verdict ─────────────────────────────────────────────────

    @Test
    fun `a blocked device is refused without raising a prompt`() {
        assertEquals(RemoteApproval.Reject, approval(RemoteAccess.AUTO_REJECT))
    }

    @Test
    fun `a trusted device is carried out and reported afterwards`() {
        val approve = assertIs<RemoteApproval.Approve>(approval(RemoteAccess.AUTO_APPROVE))

        assertEquals(RemoteEventType.QA_APPROVE, approve.notification.type)
        assertEquals("Is the car park full?", approve.notification.title)
        assertEquals("from row 3", approve.notification.detail)
        assertEquals("phone-7", approve.notification.clientId)
        assertEquals("Sam's phone", approve.notification.clientLabel)
    }

    @Test
    fun `an unknown device is asked about first`() {
        val ask = assertIs<RemoteApproval.Ask>(approval(RemoteAccess.PROMPT))

        assertEquals(RemoteEventType.QA_APPROVE, ask.event.type)
        assertEquals("Is the car park full?", ask.event.title)
        assertEquals("from row 3", ask.event.detail)
        assertEquals("phone-7", ask.event.clientId)
        assertEquals("Sam's phone", ask.event.clientLabel)
    }

    @Test
    fun `every verdict produces an outcome`() {
        val outcomes = RemoteAccess.entries.map { approval(it) }

        assertEquals(RemoteAccess.entries.size, outcomes.size)
        assertEquals(outcomes.size, outcomes.distinct().size, "two verdicts must not collapse: $outcomes")
    }

    // ── The drift this exists to prevent ────────────────────────────────────────

    @Test
    fun `the toast and the prompt describe the request identically`() {
        val notification = assertIs<RemoteApproval.Approve>(approval(RemoteAccess.AUTO_APPROVE)).notification
        val event = assertIs<RemoteApproval.Ask>(approval(RemoteAccess.PROMPT)).event

        assertEquals(
            listOf(
                notification.type,
                notification.title,
                notification.detail,
                notification.clientId,
                notification.clientLabel,
            ),
            listOf(event.type, event.title, event.detail, event.clientId, event.clientLabel),
            "the operator must see the same request whether it was auto-approved or asked about",
        )
    }

    @Test
    fun `a request with no detail leaves the detail empty rather than inventing one`() {
        val bare = remoteApproval(
            RemoteAccess.PROMPT,
            type = RemoteEventType.PRESENTATION_CONNECT,
            title = "",
            clientId = "tablet-1",
            clientLabel = "",
        )

        val ask = assertIs<RemoteApproval.Ask>(bare)
        assertEquals("", ask.event.detail)
        assertEquals("", ask.event.title)
        assertEquals("tablet-1", ask.event.clientId)
    }

    @Test
    fun `the description follows its arguments rather than being fixed`() {
        val a = assertIs<RemoteApproval.Ask>(approval(RemoteAccess.PROMPT)).event
        val b = assertIs<RemoteApproval.Ask>(
            remoteApproval(
                RemoteAccess.PROMPT,
                type = RemoteEventType.QA_DENY,
                title = "another question",
                clientId = "phone-9",
                clientLabel = "Alex's phone",
            )
        ).event

        assertNotEquals(a, b)
        assertEquals(RemoteEventType.QA_DENY, b.type)
    }
}
