package org.churchpresenter.app.churchpresenter.data.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.app.churchpresenter.models.CompanionSurfacePlacement
import java.util.UUID

/** One configured connection to a Bitfocus Companion instance. Multiple can be configured. */
@Serializable
data class CompanionSatelliteSettings(
    /** Stable identity for this connection entry — independent of [deviceId], survives edits. */
    val id: String = UUID.randomUUID().toString(),
    /** Display label shown when more than one connection is configured. */
    val name: String = "Companion",
    val host: String = "",
    val port: Int = 16622,
    /** Stable id this app registers as with Companion for the TAB placement; generated once and
     * persisted. Left/Right Sidebar each need their own distinct device identity too — Companion
     * ties "current page" to one device id, so two placements sharing an id would fight over the
     * same page — but default to being derived from this one ([deviceIdFor]) rather than requiring
     * every user to fill in three ids for the common single-placement case. */
    val deviceId: String = UUID.randomUUID().toString(),
    /** Overrides the auto-derived "$deviceId-left_sidebar"/"$deviceId-right_sidebar" id when
     * non-blank — lets a user match an id Companion already has permissions/pages configured for,
     * instead of ChurchPresenter's suffix always winning. Blank (the default) keeps the automatic
     * derivation. */
    val leftSidebarDeviceId: String = "",
    val rightSidebarDeviceId: String = "",
    val autoConnect: Boolean = false,
    /** Shown as `PRODUCT_NAME` in Companion's surface list — lets multiple instances be told apart. */
    val productName: String = "ChurchPresenter",
    /** Delay before retrying after a dropped connection. */
    val reconnectDelayMs: Int = 2000,
    /** Which UI locations show this connection's button grid — any combination, including all three. */
    val showInTab: Boolean = true,
    val showInLeftSidebar: Boolean = false,
    val showInRightSidebar: Boolean = false,
    /** Grid shape registered with Companion for each placement — independent per placement since
     * each is already its own separate device registration (e.g. a compact grid in a sidebar vs. a
     * full grid in the tab). Which page a placement starts on, and which sub-rectangle of a larger
     * page it shows, are NOT configured here — Companion already has its own equivalent per-surface
     * settings (Settings → Surfaces → this device) that take effect reliably, whereas driving them
     * from ChurchPresenter's side turned out not to be respected consistently in practice. */
    val tabRows: Int = 4,
    val tabColumns: Int = 8,
    val tabBitmapSize: Int = 72,
    val leftSidebarRows: Int = 4,
    val leftSidebarColumns: Int = 8,
    val leftSidebarBitmapSize: Int = 72,
    val rightSidebarRows: Int = 4,
    val rightSidebarColumns: Int = 8,
    val rightSidebarBitmapSize: Int = 72,
    /** Caps each button's on-screen size (dp) for this placement so a small grid doesn't stretch to
     * fill a large panel; 0 = unlimited (buttons grow to fill the available space, the prior
     * behavior). Purely a display cap — never sent to Companion, unlike [tabBitmapSize] etc. */
    val tabMaxButtonSizeDp: Int = 0,
    val leftSidebarMaxButtonSizeDp: Int = 0,
    val rightSidebarMaxButtonSizeDp: Int = 0
) {
    fun isEnabled(placement: CompanionSurfacePlacement): Boolean = when (placement) {
        CompanionSurfacePlacement.TAB -> showInTab
        CompanionSurfacePlacement.LEFT_SIDEBAR -> showInLeftSidebar
        CompanionSurfacePlacement.RIGHT_SIDEBAR -> showInRightSidebar
    }

    fun rowsFor(placement: CompanionSurfacePlacement): Int = when (placement) {
        CompanionSurfacePlacement.TAB -> tabRows
        CompanionSurfacePlacement.LEFT_SIDEBAR -> leftSidebarRows
        CompanionSurfacePlacement.RIGHT_SIDEBAR -> rightSidebarRows
    }

    fun columnsFor(placement: CompanionSurfacePlacement): Int = when (placement) {
        CompanionSurfacePlacement.TAB -> tabColumns
        CompanionSurfacePlacement.LEFT_SIDEBAR -> leftSidebarColumns
        CompanionSurfacePlacement.RIGHT_SIDEBAR -> rightSidebarColumns
    }

    fun bitmapSizeFor(placement: CompanionSurfacePlacement): Int = when (placement) {
        CompanionSurfacePlacement.TAB -> tabBitmapSize
        CompanionSurfacePlacement.LEFT_SIDEBAR -> leftSidebarBitmapSize
        CompanionSurfacePlacement.RIGHT_SIDEBAR -> rightSidebarBitmapSize
    }

    fun maxButtonSizeDpFor(placement: CompanionSurfacePlacement): Int = when (placement) {
        CompanionSurfacePlacement.TAB -> tabMaxButtonSizeDp
        CompanionSurfacePlacement.LEFT_SIDEBAR -> leftSidebarMaxButtonSizeDp
        CompanionSurfacePlacement.RIGHT_SIDEBAR -> rightSidebarMaxButtonSizeDp
    }
}
