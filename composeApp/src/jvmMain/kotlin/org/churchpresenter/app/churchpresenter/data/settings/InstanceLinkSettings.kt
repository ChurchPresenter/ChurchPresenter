package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * How this instance's Bible content tracks the primary while connected.
 * - [FULL_REPLICA]: download and use the primary's exact Bible file(s), both primary and secondary
 *   whenever the primary has one configured. Guarantees identical text; the follower can't use its
 *   own translation.
 * - [REFERENCE_ONLY]: keep this instance's own locally-configured Bible(s) exactly as set up (any
 *   language/translation, primary and secondary both) — nothing is downloaded. Only the verse
 *   *reference* is synced, via the canonical book/chapter/verse code both instances' Bible objects
 *   already resolve independently (see Bible.getCodeReference/getVerseDetailsByCode).
 */
@Serializable
enum class BibleSyncMode { FULL_REPLICA, REFERENCE_ONLY }

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
    val allowPushToSchedule: Boolean = false,
    /** See [BibleSyncMode]. */
    val bibleSyncMode: BibleSyncMode = BibleSyncMode.FULL_REPLICA,
    /** When false (default), backgrounds (images/videos behind Bible/Songs and their lower-third
     * variants) always stay whatever this instance has configured locally. When true, they're
     * replaced by the primary's backgrounds while connected. Off by default since backgrounds are
     * often venue-specific. */
    val mirrorBackgrounds: Boolean = false
)
