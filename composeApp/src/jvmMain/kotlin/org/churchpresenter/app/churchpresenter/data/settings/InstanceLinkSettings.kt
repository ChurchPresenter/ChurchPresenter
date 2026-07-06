package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import java.util.UUID

/** Configuration for this instance following another ChurchPresenter instance's CompanionServer
 * over the network — mirrors its live content and (optionally) contributes schedule items back. */
@Serializable
data class InstanceLinkSettings(
    val enabled: Boolean = false,
    val primaryHost: String = "",
    val primaryPort: Int = 0,
    val apiKey: String = "",
    /** Stable id this instance identifies itself as to the primary's approval/allow-list UI. */
    val deviceId: String = UUID.randomUUID().toString(),
    val autoConnect: Boolean = false,
    /** Delay before retrying after a dropped connection. */
    val reconnectDelayMs: Int = 2000,
    /** Whether this instance may add items to the primary's schedule (still gated by the primary
     * operator's existing approval dialog — this only controls whether the request is ever sent). */
    val allowPushToSchedule: Boolean = false
)
