package org.churchpresenter.signage.tab

import org.churchpresenter.signage.core.Orientation

/** What Save does to a screen's touchscreen PIN. */
internal sealed interface PinChange {
    data object Keep : PinChange

    data class Set(val pin: String) : PinChange

    data object Remove : PinChange
}

/** A screen's settings as [ScreenSettingsDialog] opens on them. */
internal data class ScreenSettingsInput(
    val name: String,
    val orientation: Orientation,
    /** Whether a touchscreen PIN is set; the PIN itself is never shown. */
    val hasPin: Boolean = false,
    /** Whether holding the screen's corner switches to a touch page. */
    val gesture: Boolean = true,
    /** The playlist the corner switches to; the first of [touchPlaylists] when null. */
    val gesturePlaylistId: String? = null,
    /** The playlists that have a touch page, which the corner can switch to. */
    val touchPlaylists: List<PlaylistSummary> = emptyList(),
    /** Whether the screen is connected and its player can restart and shut down its machine. */
    val canPower: Boolean = false,
    /** Whether the screen is connected and its TV answers over HDMI-CEC, so it can be turned on and off. */
    val canTv: Boolean = false,
)

/** What Save changes. */
internal data class ScreenSettingsEdit(
    val name: String,
    val orientation: Orientation,
    val pin: PinChange,
    val gesture: Boolean,
    val gesturePlaylistId: String?,
)
