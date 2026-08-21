package org.churchpresenter.app.churchpresenter.models

import companionsatellite.CompanionConnectionStatus
import org.churchpresenter.core.models.companion.CompanionSurfaceSlot

/** Live status for one Companion Satellite registration, keyed by [CompanionSurfaceSlot]. */
data class CompanionConnectionUiState(
    val slot: CompanionSurfaceSlot,
    val status: CompanionConnectionStatus = CompanionConnectionStatus.DISCONNECTED,
    val errorMessage: String = "",
    /** Last brightness percent Companion pushed for this surface (display only). */
    val brightness: Int = 100
)
