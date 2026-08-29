package org.churchpresenter.settings

import kotlinx.serialization.Serializable
import org.churchpresenter.core.models.songs.SongBackground

/**
 * One background kept to hand in the preview panel's tray.
 *
 * The tray is a live control, not a setting: picking one overrides what is on screen for the rest
 * of the service and writes nothing. This class is only the *saved* half — the handful of
 * backgrounds an operator wants one click away, in the order the tray shows them.
 *
 * It carries a [SongBackground] pair rather than a [BackgroundConfig] so that a quick background is
 * the same kind of thing a song carries, edited in the same panel: gradient, dim and blur included,
 * and one background for the full screen with another for the lower-third band.
 *
 * [label] is what the tile is called; blank falls back to the background's own name.
 */
@Serializable
data class QuickBackground(
    val id: String = "",
    val label: String = "",
    val background: SongBackground = SongBackground(),
    val lowerThirdBackground: SongBackground = SongBackground(),
)
