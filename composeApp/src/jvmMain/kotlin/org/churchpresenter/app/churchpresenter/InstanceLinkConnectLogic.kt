package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import org.churchpresenter.settings.InstanceLinkSettings
import org.churchpresenter.settings.utils.Constants

/**
 * Whether a Companion connection should be brought up on this pass.
 *
 * Three separate reasons, and the middle one is the only one that is a setting: something is
 * already live for it, the operator asked for it to auto-connect, or they have just edited it —
 * an edit is an explicit action, so it connects even with auto-connect off. A connection merely
 * seen for the first time at startup does none of these, which keeps startup opt-in.
 */
internal fun shouldConnectCompanion(
    hasLiveSlot: Boolean,
    autoConnect: Boolean,
    lastSeen: CompanionSatelliteSettings?,
    current: CompanionSatelliteSettings,
): Boolean = hasLiveSlot || autoConnect || (lastSeen != null && lastSeen != current)

/** Whether a connection needs a device id minted before it can be used — Companion rejects a blank one. */
internal fun needsGeneratedDeviceId(connection: CompanionSatelliteSettings): Boolean =
    connection.deviceId.isBlank()

/** [connections] with [id]'s device id replaced, leaving every other connection alone. */
internal fun withGeneratedDeviceId(
    connections: List<CompanionSatelliteSettings>,
    id: String,
    deviceId: String,
): List<CompanionSatelliteSettings> =
    connections.map { if (it.id == id) it.copy(deviceId = deviceId) else it }

/** Whether the instance link should dial out on its own: switched on, set to, and actually addressed. */
internal fun shouldAutoConnectInstanceLink(link: InstanceLinkSettings): Boolean =
    link.enabled && link.autoConnect && link.primaryHost.isNotBlank() && link.primaryPort > 0

/**
 * Whether a live link should be dropped. Switching the link off has to disconnect it now rather
 * than leave it running until the next launch.
 */
internal fun shouldDisconnectInstanceLink(link: InstanceLinkSettings): Boolean = !link.enabled

/** Whether the operator's connect/disconnect intent is a change worth persisting. */
internal fun instanceLinkEnabledChanged(current: InstanceLinkSettings, enabled: Boolean): Boolean =
    current.enabled != enabled

/**
 * Whether this instance is in a position to drive another one.
 *
 * Both halves matter: a link that is merely configured as Controller but not connected has nothing
 * to send to, and a connected Controlled follower must never send — it receives.
 */
internal fun isControllerConnected(status: InstanceLinkStatus, role: InstanceLinkRole): Boolean =
    status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLER

/** Whether the client awaiting approval is another instance following this one. */
internal fun isInstanceLinkFollowerClient(clientId: String, followers: Set<String>): Boolean =
    clientId.isNotBlank() && clientId in followers

/** The URL a follower streams a mirrored media item from, carrying the key when one is set. */
internal fun instanceLinkMediaStreamUrl(
    host: String,
    port: Int,
    apiKey: String,
    itemId: String,
): String {
    val keyParam = if (apiKey.isNotEmpty()) "?${Constants.QUERY_PARAM_API_KEY}=$apiKey" else ""
    return "http://$host:$port${Constants.ENDPOINT_MEDIA_STREAM}/$itemId$keyParam"
}

/** Whether the link is anything other than fully down, connecting included. */
internal fun isInstanceLinkActive(status: InstanceLinkStatus): Boolean =
    status != InstanceLinkStatus.DISCONNECTED
