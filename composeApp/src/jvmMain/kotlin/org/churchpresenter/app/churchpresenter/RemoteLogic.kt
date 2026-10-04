package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.server.RemoteEventType
import org.churchpresenter.settings.OBSSettings
import org.churchpresenter.settings.ServerSettings
import org.churchpresenter.server.TunnelStatus

/**
 * The key remote callers must present, which is none at all when the operator has not switched key
 * checking on.
 *
 * Read in two places — the Q&A admin panel and the presentation remote — which is why it is one
 * function rather than the same expression written twice: two copies can disagree about whether a
 * key is required, and only one of the two screens would then refuse callers.
 */
internal fun activeApiKey(settings: ServerSettings): String =
    if (settings.apiKeyEnabled) settings.apiKey else ""

/** Whether the tunnel is up. */
internal fun isTunnelConnected(status: TunnelStatus): Boolean = status is TunnelStatus.Connected

/**
 * Whether the tunnel has just gone down, as opposed to being down all along.
 *
 * Edge-triggered on purpose: the URLs handed out over the tunnel are only worth clearing at the
 * moment it drops. Treating "is not connected" as the signal would clear them repeatedly, including
 * before one was ever established.
 */
internal fun tunnelJustDropped(previouslyConnected: Boolean, isConnected: Boolean): Boolean =
    previouslyConnected && !isConnected

/** Whether OBS should be connected to, as opposed to disconnected from. */
internal fun shouldConnectObs(settings: OBSSettings): Boolean = settings.enabled

/**
 * The kind of activity a no-approval remote action is reported as.
 *
 * These arrive as strings over the wire, so an unrecognised one has to become something rather than
 * nothing: the toast exists so the operator can see what a remote client just did and block them if
 * it was not wanted, and an action that produced no toast at all would be the one worth seeing.
 */
internal fun remoteActionType(actionType: String): RemoteEventType = when (actionType) {
    "present" -> RemoteEventType.PRESENT
    "upload" -> RemoteEventType.UPLOAD
    "clear" -> RemoteEventType.CLEAR
    else -> RemoteEventType.PRESENT
}

/**
 * Whether a queued remote request is covered by the decision the operator just made.
 *
 * Allow/block apply to a client, not to the one request on screen, so every other request already
 * queued from that client is settled the same way rather than asked about again. A blank id is the
 * unattributable case, and takes the whole queue with it: there is no client to ask about next.
 */
internal fun remoteEventTargetsClient(eventClientId: String, decidedClientId: String): Boolean =
    eventClientId == decidedClientId || decidedClientId.isBlank()

/**
 * The URL the on-screen Q&A QR code points at.
 *
 * The tunnel URL when there is one, so a phone on mobile data can reach it; the LAN address
 * otherwise.
 */
internal fun qaQrCodeUrl(tunnelUrl: String, serverUrl: String): String =
    "${tunnelUrl.ifEmpty { serverUrl }}/qa"

/**
 * Whether media going away is worth one last broadcast.
 *
 * Only on the edge: connected phones need one "nothing loaded" to drop their now-playing view, but
 * repeating it on every poll of an idle app would be a message every half-second forever.
 */
internal fun shouldBroadcastMediaCleared(isLoaded: Boolean, wasLoaded: Boolean): Boolean =
    !isLoaded && wasLoaded

/** How much of a remote question is shown in the approval prompt. */
internal const val MAX_REMOTE_EVENT_TITLE = 80

/** The question text put in front of the operator, cut to what the prompt can show. */
internal fun remoteEventTitle(text: String): String = text.take(MAX_REMOTE_EVENT_TITLE)

/** Whether a follower is allowed to push items into this instance's schedule. */
internal fun canPushToSchedule(link: InstanceLinkSettings): Boolean = link.allowPushToSchedule
