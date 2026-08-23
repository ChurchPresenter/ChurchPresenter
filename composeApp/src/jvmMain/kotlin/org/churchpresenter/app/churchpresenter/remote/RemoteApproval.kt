package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.companionserver.RemoteAccess
import org.churchpresenter.app.churchpresenter.dialogs.RemoteActivityNotification
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEvent
import org.churchpresenter.app.churchpresenter.dialogs.RemoteEventType

/**
 * What the desktop does with an approval-only remote request: refuse it, carry it out and tell the
 * operator afterwards, or put it in front of them first.
 *
 * Pairs with [remoteAccessDecision], which answers *whether* the device is trusted. This answers
 * what that verdict means for the request, and it is deliberately a **value** rather than a callback:
 * the caller applies it. That keeps the branch testable without a Compose state holder, an event
 * queue or a `CompletableDeferred` anywhere near the test.
 */
internal sealed interface RemoteApproval {
    /** The device is blocked. Refuse without telling the operator — a blocked phone must not be
     *  able to raise a prompt, or blocking it would achieve nothing. */
    data object Reject : RemoteApproval

    /** Trusted device: carry the request out, then show [notification] so the operator can see what
     *  happened and block the device if it was not them. */
    data class Approve(val notification: RemoteActivityNotification) : RemoteApproval

    /** Unknown device: ask, via [event], before anything happens. */
    data class Ask(val event: RemoteEvent) : RemoteApproval
}

/**
 * Decides the outcome above and builds the operator-facing description **once**.
 *
 * That single construction is the point. Each handler used to build a [RemoteActivityNotification]
 * in its approve branch and a [RemoteEvent] in its prompt branch from the same values, by hand, in
 * eight places — and the two classes carry identical fields, so nothing stopped the toast and the
 * approval dialog describing the same request differently. Here they cannot: one set of arguments
 * feeds whichever of the two the outcome calls for.
 */
internal fun remoteApproval(
    access: RemoteAccess,
    type: RemoteEventType,
    title: String,
    detail: String = "",
    clientId: String,
    clientLabel: String,
): RemoteApproval = when (access) {
    RemoteAccess.AUTO_REJECT -> RemoteApproval.Reject
    RemoteAccess.AUTO_APPROVE -> RemoteApproval.Approve(
        RemoteActivityNotification(type, title, detail, clientId, clientLabel)
    )
    RemoteAccess.PROMPT -> RemoteApproval.Ask(
        RemoteEvent(type, title, detail, clientId, clientLabel)
    )
}
