package org.churchpresenter.companionserver

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Whether a remote request runs, is refused, or goes to the operator.
 *
 * This is the gate between any phone on the church WiFi and the live output. It was written out
 * seven times across `main.kt`'s request collectors — in two spellings, since some copies split the
 * block checks and others fused them — with no tests on any of them.
 *
 * The two properties worth pinning are both about what happens when the lists disagree or the device
 * is unknown, which is exactly where seven hand-written copies were free to drift.
 *
 * Plain sets, no fixtures: the function takes collections rather than `RemoteClientManager`
 * precisely so a test never touches `~/.churchpresenter/remote_clients.json`.
 */
class RemoteAccessDecisionTest {

    private fun decide(
        clientId: String,
        allowed: Set<String> = emptySet(),
        blocked: Set<String> = emptySet(),
        sessionAllowed: List<String> = emptyList(),
        sessionBlocked: List<String> = emptyList(),
    ) = remoteAccessDecision(clientId, allowed, blocked, sessionAllowed, sessionBlocked)

    // ── Blocks beat allows ──────────────────────────────────────────────────────

    @Test
    fun `a device on both lists is refused`() {
        // The ordering is the security property. Swapping these two checks would hand a device the
        // operator had explicitly blocked the run of the schedule.
        assertEquals(
            RemoteAccess.AUTO_REJECT,
            decide("phone-1", allowed = setOf("phone-1"), blocked = setOf("phone-1")),
        )
    }

    @Test
    fun `blocking for the session overrides a permanent allow`() {
        // How an operator shuts up a device mid-service without editing its permanent entry.
        assertEquals(
            RemoteAccess.AUTO_REJECT,
            decide("phone-1", allowed = setOf("phone-1"), sessionBlocked = listOf("phone-1")),
        )
    }

    // ── The ordinary cases ──────────────────────────────────────────────────────

    @Test
    fun `a permanently allowed device runs without interrupting the operator`() {
        assertEquals(RemoteAccess.AUTO_APPROVE, decide("phone-1", allowed = setOf("phone-1")))
    }

    @Test
    fun `a device allowed for the session runs too`() {
        assertEquals(RemoteAccess.AUTO_APPROVE, decide("phone-1", sessionAllowed = listOf("phone-1")))
    }

    @Test
    fun `a permanently blocked device is refused without interrupting the operator`() {
        assertEquals(RemoteAccess.AUTO_REJECT, decide("phone-1", blocked = setOf("phone-1")))
    }

    @Test
    fun `an unknown device is put to the operator`() {
        assertEquals(RemoteAccess.PROMPT, decide("phone-1"))
    }

    // ── The anonymous client ────────────────────────────────────────────────────

    @Test
    fun `a device that never identified itself is never auto-approved`() {
        // Every client that did not send an id shares the blank one, so auto-approving on a blank id
        // would approve all of them at once off a single earlier decision.
        assertEquals(
            RemoteAccess.PROMPT,
            decide("", allowed = setOf(""), sessionAllowed = listOf("")),
        )
    }

    @Test
    fun `a device that never identified itself is not silently refused either`() {
        // The other half: a blank id must not vanish into an auto-reject, or an anonymous request
        // disappears without the operator ever being given the chance to allow it.
        assertEquals(
            RemoteAccess.PROMPT,
            decide("", blocked = setOf(""), sessionBlocked = listOf("")),
        )
    }
}
