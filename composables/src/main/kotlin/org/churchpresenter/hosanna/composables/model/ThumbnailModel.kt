package org.churchpresenter.hosanna.composables.model

import androidx.compose.runtime.Immutable

enum class ThumbnailSize { Small, Medium, Large }

enum class ThumbnailStatus { Ready, Loading, Missing, Offline }

@Immutable
class ThumbnailFileLabels(val missing: String, val locate: String, val offline: String)

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
