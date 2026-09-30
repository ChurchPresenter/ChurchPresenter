package org.churchpresenter.app.churchpresenter

import org.churchpresenter.settings.CompanionSatelliteSettings
import org.churchpresenter.settings.InstanceLinkRole
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.InstanceLinkStatus
import java.io.File

private const val MILLIS_PER_SECOND = 1000
private const val HEX_RADIX = 16

/*
 * The main screen's Instance Link and Companion decisions: when to mirror the primary, what the status
 * row offers, and the ids remote clients see. Pure.
 */

/**
 * Whether this instance should mirror the primary's content over Instance Link.
 *
 * Both halves are load-bearing, and the role half is the one that is easy to lose: a **Controller**
 * is also connected, but it drives the primary rather than following it, so it must keep browsing
 * its own local songs, bibles and schedule. Mirroring in that role would replace the operator's own
 * library with the far end's, mid-service, on the machine that is meant to be in charge.
 *
 * Shared by the songs, bible and schedule mirrors so all three follow — and stop following —
 * together; they previously spelled this out three times over.
 */
internal fun shouldMirrorFromPrimary(
    status: InstanceLinkStatus,
    role: InstanceLinkRole,
): Boolean = status == InstanceLinkStatus.CONNECTED && role == InstanceLinkRole.CONTROLLED

internal fun retrySecondsLeft(nextRetryAtMs: Long?, nowMs: Long): Long? =
    nextRetryAtMs?.let { ((it - nowMs) / MILLIS_PER_SECOND).coerceAtLeast(0) }

/** Whether the link status is one worth offering a disconnect for — anything but fully detached. */
internal fun canDisconnectInstanceLink(status: InstanceLinkStatus): Boolean =
    status != InstanceLinkStatus.DISCONNECTED

/**
 * Whether a section index arriving from elsewhere — a phone, a linked instance — should move the
 * Songs tab's own selection.
 *
 * Only while songs are what is live, and only when it is actually a different section: following an
 * index the tab already sits on would write the selection back over itself on every emission.
 */
internal fun shouldFollowRemoteSection(
    presentingMode: Presenting,
    selectedSectionIndex: Int,
    incomingSectionIndex: Int,
): Boolean = presentingMode == Presenting.LYRICS && selectedSectionIndex != incomingSectionIndex

internal fun stableFileId(file: File): String = file.absolutePath.hashCode().toUInt().toString(HEX_RADIX)

internal fun resolveSelectedConnectionId(currentId: String?, connections: List<CompanionSatelliteSettings>): String? =
    if (connections.any { it.id == currentId }) currentId else connections.firstOrNull()?.id
