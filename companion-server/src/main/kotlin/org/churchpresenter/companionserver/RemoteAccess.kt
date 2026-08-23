package org.churchpresenter.companionserver

/** What should happen to a remote request before it is applied. */
enum class RemoteAccess {
    /** The device is trusted — run the request without interrupting the operator. */
    AUTO_APPROVE,

    /** The device is blocked — refuse without interrupting the operator. */
    AUTO_REJECT,

    /** Unknown device — the operator decides. */
    PROMPT,
}

/**
 * Decides whether a remote request runs, is refused, or goes to the operator.
 *
 * This is what stands between any phone on the church WiFi and the live output, and it was written
 * out seven times across `main.kt`'s request collectors — in two different spellings, since some
 * copies split the two block checks and others fused them. Same outcome each way, but a rule that
 * important is worth having in one place with tests on it.
 *
 * Two properties carry the weight:
 *  - **A block beats an allow.** A device on both lists is refused. Inverting these two checks would
 *    hand a blocked phone the schedule.
 *  - **A blank device id is never auto-anything.** Clients that never identified themselves all
 *    share the empty id, so auto-approving one would approve every anonymous client at once, and
 *    auto-rejecting one would make their requests vanish without the operator ever seeing them.
 *    Either way the operator is asked.
 *
 * Takes plain collections rather than `RemoteClientManager` on purpose: that class reads and writes
 * `~/.churchpresenter/remote_clients.json` in its constructor, so a test that built one would touch
 * the developer's real home directory.
 */
fun remoteAccessDecision(
    clientId: String,
    permanentlyAllowed: Set<String>,
    permanentlyBlocked: Set<String>,
    sessionAllowed: Collection<String>,
    sessionBlocked: Collection<String>,
): RemoteAccess {
    if (clientId.isBlank()) return RemoteAccess.PROMPT
    if (clientId in permanentlyBlocked || clientId in sessionBlocked) return RemoteAccess.AUTO_REJECT
    if (clientId in permanentlyAllowed || clientId in sessionAllowed) return RemoteAccess.AUTO_APPROVE
    return RemoteAccess.PROMPT
}
