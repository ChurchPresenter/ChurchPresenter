package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable

/**
 * Per-church OAuth connection state for the Planning Center Online integration. ChurchPresenter
 * uses a single shared OAuth app (per PCO's own multi-tenant guidance — see `BuildConfig
 * .PLANNING_CENTER_CLIENT_ID`/`PLANNING_CENTER_CLIENT_SECRET`, injected at build time, never
 * stored here); each church just authorizes that one app and its resulting tokens are stored in
 * this settings block.
 */
@Serializable
data class PlanningCenterSettings(
    val accessToken: String = "",
    val refreshToken: String = "",
    val tokenExpiresAtEpochMs: Long = 0L,
    val connectedPersonName: String = "",
    val defaultServiceTypeId: String = "",
    val importSongbookName: String = "Planning Center"
)
