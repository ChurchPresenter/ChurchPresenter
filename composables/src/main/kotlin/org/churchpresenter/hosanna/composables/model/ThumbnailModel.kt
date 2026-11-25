package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

/**
 * The size of a thumbnail: [Small] (96×54dp), [Medium] (160×90dp) or [Large] (240×135dp).
 */
enum class ThumbnailSize { Small, Medium, Large }

/**
 * Whether a thumbnail's picture is [Ready], still [Loading], [Missing] from disk, or of a screen that is [Offline].
 */
enum class ThumbnailStatus { Ready, Loading, Missing, Offline }

/**
 * The thumbnail's texts about its file or screen.
 *
 * @property missing what a missing file says.
 * @property locate the button that finds a missing file.
 * @property offline the badge on an offline screen.
 */
@Immutable
class ThumbnailFileLabels(val missing: String, val locate: String, val offline: String)

/**
 * The texts of [org.churchpresenter.hosanna.composables.media.Thumbnail].
 *
 * @property goLive the go-live button's name and tooltip.
 * @property hide the hide button's name and tooltip.
 * @property showAgain the name and tooltip of the button that brings a hidden item back.
 * @property hiddenNote the second caption line of a hidden item.
 * @property live the LIVE badge.
 * @property file the texts about a missing file or offline screen.
 */
@Immutable
class ThumbnailLabels(
    val goLive: String,
    val hide: String,
    val showAgain: String,
    val hiddenNote: String,
    val live: String,
    val file: ThumbnailFileLabels,
)

internal fun canGoLive(status: ThumbnailStatus, hidden: Boolean): Boolean =
    !hidden && (status == ThumbnailStatus.Ready || status == ThumbnailStatus.Offline)

internal fun showsHoverActions(size: ThumbnailSize, status: ThumbnailStatus): Boolean =
    size != ThumbnailSize.Small && (status == ThumbnailStatus.Ready || status == ThumbnailStatus.Offline)
