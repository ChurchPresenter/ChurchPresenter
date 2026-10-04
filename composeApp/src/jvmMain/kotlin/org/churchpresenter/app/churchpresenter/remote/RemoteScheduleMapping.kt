package org.churchpresenter.app.churchpresenter.remote

import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.schedule.ScheduleViewModel
import org.churchpresenter.server.InstanceLinkLogSide
import org.churchpresenter.server.InstanceLinkLogger
import org.churchpresenter.server.ScheduleItemDto

/** Follows the primary's schedule: maps its `schedule_updated` broadcast and logs what survived. */
internal fun ScheduleViewModel.applyRemoteSchedule(dtos: List<ScheduleItemDto>) {
    val mapped = dtos.mapNotNull { it.toScheduleItem() }
    followRemoteSchedule(mapped)
    InstanceLinkLogger.log(
        InstanceLinkLogSide.FOLLOWER, "schedule_sync_result",
        mapOf("receivedCount" to dtos.size, "mappedCount" to mapped.size)
    )
}

/**
 * Inverse of `CompanionServer.updateSchedule`'s
 * ScheduleItem → ScheduleItemDto mapping. Some fields the DTO doesn't carry (extra announcement
 * formatting/timer options, lower-third pause settings, scene id, dictionary detail beyond the
 * word) fall back to defaults — a known limitation of mirroring through the flat companion DTO
 * shape rather than the full sealed [ScheduleItem].
 */
internal fun ScheduleItemDto.toScheduleItem(): ScheduleItem? = when (type) {
    "song" -> ScheduleItem.SongItem(
        id = id,
        songNumber = songNumber ?: 0,
        title = title ?: "",
        songbook = songbook ?: ""
    )
    "bible" -> ScheduleItem.BibleVerseItem(
        id = id, bookName = bookName ?: "", chapter = chapter ?: 0, verseNumber = verseNumber ?: 0,
        verseText = text ?: "", verseRange = verseRange ?: ""
    )
    "label" -> ScheduleItem.LabelItem(
        id = id,
        text = text ?: "",
        textColor = textColor ?: "#FFFFFF",
        backgroundColor = backgroundColor ?: "#2196F3"
    )
    "picture" -> ScheduleItem.PictureItem(
        id = id,
        folderPath = folderPath ?: "",
        folderName = folderName ?: "",
        imageCount = imageCount ?: 0
    )
    "presentation" -> ScheduleItem.PresentationItem(
        id = id,
        filePath = filePath ?: "",
        fileName = fileName ?: "",
        slideCount = slideCount ?: 0,
        fileType = fileType ?: ""
    )
    "media" -> ScheduleItem.MediaItem(
        id = id,
        mediaUrl = mediaUrl ?: "",
        mediaTitle = mediaTitle ?: "",
        mediaType = mediaType ?: ""
    )
    "lower_third" -> ScheduleItem.LowerThirdItem(
        id = id,
        presetId = presetId ?: "",
        presetLabel = presetLabel ?: "",
        pauseAtFrame = false,
        pauseDurationMs = 2000L
    )
    "announcement" -> ScheduleItem.AnnouncementItem(
        id = id,
        text = text ?: "",
        textColor = textColor ?: "#FFFFFF",
        backgroundColor = backgroundColor ?: "#000000"
    )
    "website" -> ScheduleItem.WebsiteItem(id = id, url = url ?: "", title = title ?: url ?: "")
    "scene" -> ScheduleItem.SceneItem(id = id, sceneId = "", sceneName = displayText)
    "dictionary" -> ScheduleItem.DictionaryItem(
        id = id,
        number = "",
        word = displayText,
        transliteration = "",
        definition = ""
    )
    else -> null
}
