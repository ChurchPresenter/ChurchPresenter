package org.churchpresenter.app.churchpresenter.server

import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.utils.Constants

/**
 * Infer a [ScheduleItem] from the flat [RemoteItemDto] by detecting which fields are present.
 * Returns null if the dto doesn't match any known item type.
 */
fun RemoteItemDto.toScheduleItem(): ScheduleItem? {
    val safeId = id.ifBlank { java.util.UUID.randomUUID().toString() }
    return when {
        // Song — must have songNumber
        songNumber != null ->
            ScheduleItem.SongItem(
                id         = safeId,
                songNumber = songNumber,
                title      = title ?: "",
                songbook   = songbook ?: ""
            )
        // Bible verse — must have bookName + chapter + verseNumber
        bookName != null && chapter != null && verseNumber != null ->
            ScheduleItem.BibleVerseItem(
                id          = safeId,
                bookName    = bookName,
                chapter     = chapter,
                verseNumber = verseNumber,
                verseText   = verseText ?: "",
                verseRange  = verseRange ?: ""
            )
        // Picture folder — must have folderPath
        folderPath != null ->
            ScheduleItem.PictureItem(
                id         = safeId,
                folderPath = folderPath,
                folderName = folderName ?: folderPath,
                imageCount = imageCount ?: 0
            )
        // Presentation — must have filePath
        filePath != null ->
            ScheduleItem.PresentationItem(
                id         = safeId,
                filePath   = filePath,
                fileName   = fileName ?: filePath,
                slideCount = slideCount ?: 0,
                fileType   = fileType ?: ""
            )
        // Media — must have mediaUrl
        mediaUrl != null ->
            ScheduleItem.MediaItem(
                id         = safeId,
                mediaUrl   = mediaUrl,
                mediaTitle = mediaTitle ?: mediaUrl,
                mediaType  = mediaType ?: "local"
            )
        // Dictionary (Strong's) — must have strongsNumber
        strongsNumber != null ->
            ScheduleItem.DictionaryItem(
                id              = safeId,
                number          = strongsNumber,
                word            = title ?: "",
                transliteration = transliteration ?: "",
                definition      = definition ?: ""
            )
        // Announcement / timer — must have announcementText (may be "")
        announcementText != null -> toAnnouncementItem(safeId, announcementText)
        // Website — must have url
        url != null ->
            ScheduleItem.WebsiteItem(
                id    = safeId,
                url   = url,
                title = websiteTitle ?: url
            )
        else -> null
    }
}

/** The announcement/timer branch of [toScheduleItem], where most of the defaulting lives. */
private fun RemoteItemDto.toAnnouncementItem(safeId: String, text: String): ScheduleItem.AnnouncementItem =
    ScheduleItem.AnnouncementItem(
        id                = safeId,
        text              = text,
        textColor         = textColor ?: "#FFFFFF",
        backgroundColor   = backgroundColor ?: "#000000",
        fontSize          = fontSize ?: 48,
        animationType     = animationType ?: "SLIDE_FROM_BOTTOM",
        animationDuration = animationDuration ?: 500,
        isTimer           = isTimer ?: false,
        timerHours        = timerHours ?: 0,
        timerMinutes      = timerMinutes ?: 0,
        timerSeconds      = timerSeconds ?: 0,
        timerTextColor    = timerTextColor ?: (textColor ?: "#FFFFFF"),
        timerExpiredText  = timerExpiredText ?: "",
        timerMode         = timerMode ?: Constants.TIMER_MODE_DURATION,
        targetHour        = targetHour ?: 0,
        targetMinute      = targetMinute ?: 0,
        targetSecond      = targetSecond ?: 0,
        liveClockFormat   = liveClockFormat ?: "HH:mm:ss"
    )

/**
 * The companion API's wire form of a schedule item.
 *
 * Pure by construction — the value depends only on [ScheduleItem], never on server state. The
 * side effects that used to sit inside this `when` (cataloguing picture folders, kicking off a
 * background presentation render, recording local media paths) live in
 * `CompanionServer.registerScheduleItemResources` instead, so this mapping stays directly
 * testable and the two concerns can't drift into each other.
 */
internal fun ScheduleItem.toDto(): ScheduleItemDto = when (this) {
    is ScheduleItem.SongItem -> ScheduleItemDto(
        id = id, type = "song", displayText = displayText,
        songNumber = songNumber, title = title, songbook = songbook
    )
    is ScheduleItem.BibleVerseItem -> ScheduleItemDto(
        id = id, type = "bible", displayText = displayText,
        bookName = bookName, chapter = chapter, verseNumber = verseNumber,
        verseRange = verseRange.ifEmpty { null },
        text = verseText
    )
    is ScheduleItem.LabelItem -> ScheduleItemDto(
        id = id, type = "label", displayText = displayText,
        text = text, textColor = textColor, backgroundColor = backgroundColor
    )
    is ScheduleItem.PictureItem -> ScheduleItemDto(
        id = id, type = "picture", displayText = displayText,
        folderPath = folderPath, folderName = folderName, imageCount = imageCount
    )
    is ScheduleItem.PresentationItem -> ScheduleItemDto(
        id = id, type = "presentation", displayText = displayText,
        filePath = filePath, fileName = fileName,
        slideCount = slideCount, fileType = fileType
    )
    is ScheduleItem.MediaItem -> ScheduleItemDto(
        id = id, type = "media", displayText = displayText,
        mediaUrl = mediaUrl, mediaTitle = mediaTitle, mediaType = mediaType
    )
    is ScheduleItem.LowerThirdItem -> ScheduleItemDto(
        id = id, type = "lower_third", displayText = displayText,
        presetId = presetId, presetLabel = presetLabel
    )
    is ScheduleItem.AnnouncementItem -> ScheduleItemDto(
        id = id, type = "announcement", displayText = displayText,
        text = text, textColor = textColor, backgroundColor = backgroundColor,
        fontSize = fontSize, animationType = animationType,
        animationDuration = animationDuration, isTimer = isTimer,
        timerMode = timerMode, timerHours = timerHours,
        timerMinutes = timerMinutes, timerSeconds = timerSeconds,
        timerExpiredText = timerExpiredText, targetHour = targetHour,
        targetMinute = targetMinute, liveClockFormat = liveClockFormat
    )
    is ScheduleItem.WebsiteItem -> ScheduleItemDto(
        id = id, type = "website", displayText = displayText,
        url = url, title = title
    )
    is ScheduleItem.SceneItem -> ScheduleItemDto(
        id = id, type = "scene", displayText = displayText
    )
    is ScheduleItem.DictionaryItem -> ScheduleItemDto(
        id = id, type = "dictionary", displayText = displayText,
        text = "$word ($transliteration): $definition"
    )
}
