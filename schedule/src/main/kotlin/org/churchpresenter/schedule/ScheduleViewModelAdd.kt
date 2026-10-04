package org.churchpresenter.schedule

import java.io.File
import java.util.UUID
import org.churchpresenter.core.models.schedule.ScheduleItem
import org.churchpresenter.core.models.text.TextBackdrop
import org.churchpresenter.core.models.text.TextOutline

fun ScheduleViewModel.addSong(songNumber: Int, title: String, songbook: String, songId: String = "") {
    addOrPush(
        ScheduleItem.SongItem(
            id = UUID.randomUUID().toString(),
            songNumber = songNumber,
            title = title,
            songbook = songbook,
            songId = songId
        )
    )
}

fun ScheduleViewModel.addBibleVerse(
    bookName: String,
    chapter: Int,
    verseNumber: Int,
    verseText: String,
    verseRange: String = "",
    bookId: Int = 0
) {
    addOrPush(ScheduleItem.BibleVerseItem(
        id = UUID.randomUUID().toString(),
        bookName = bookName,
        chapter = chapter,
        verseNumber = verseNumber,
        verseText = verseText,
        verseRange = verseRange,
        bookId = bookId
    ))
}

fun ScheduleViewModel.addLabel(text: String, textColor: String, backgroundColor: String) {
    addOrPush(
        ScheduleItem.LabelItem(
            id = UUID.randomUUID().toString(),
            text = text,
            textColor = textColor,
            backgroundColor = backgroundColor
        )
    )
}

fun ScheduleViewModel.addPicture(folderPath: String, folderName: String, imageCount: Int) {
    addOrPush(
        ScheduleItem.PictureItem(
            id = UUID.randomUUID().toString(),
            folderPath = folderPath,
            folderName = folderName,
            imageCount = imageCount
        )
    )
}

/** One picture, [image], as its own row: presented, it opens its folder on this picture. */
fun ScheduleViewModel.addSinglePicture(image: File, folderImageCount: Int) {
    addOrPush(
        ScheduleItem.PictureItem(
            id = UUID.randomUUID().toString(),
            folderPath = image.parentFile?.absolutePath ?: image.absolutePath,
            folderName = image.parentFile?.name ?: image.name,
            imageCount = folderImageCount,
            displayText = image.name,
            imagePath = image.absolutePath,
        )
    )
}

fun ScheduleViewModel.addPresentation(filePath: String, fileName: String, slideCount: Int, fileType: String) {
    addOrPush(
        ScheduleItem.PresentationItem(
            id = UUID.randomUUID().toString(),
            filePath = filePath,
            fileName = fileName,
            slideCount = slideCount,
            fileType = fileType
        )
    )
}

fun ScheduleViewModel.addMedia(mediaUrl: String, mediaTitle: String, mediaType: String, subtitleUrl: String = "") {
    addOrPush(
        ScheduleItem.MediaItem(
            id = UUID.randomUUID().toString(),
            mediaUrl = mediaUrl,
            mediaTitle = mediaTitle,
            mediaType = mediaType,
            subtitleUrl = subtitleUrl
        )
    )
}

fun ScheduleViewModel.addLowerThird(
    presetId: String,
    presetLabel: String,
    pauseAtFrame: Boolean,
    pauseDurationMs: Long,
) {
    addOrPush(
        ScheduleItem.LowerThirdItem(
            id = UUID.randomUUID().toString(),
            presetId = presetId,
            presetLabel = presetLabel,
            pauseAtFrame = pauseAtFrame,
            pauseDurationMs = pauseDurationMs
        )
    )
}

fun ScheduleViewModel.addAnnouncement(
    text: String,
    textColor: String = "#FFFFFF",
    backgroundColor: String = "#000000",
    fontSize: Int = 48,
    fontType: String = "Arial",
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    shadow: Boolean = false,
    shadowColor: String = "#000000",
    shadowSize: Int = 100,
    shadowOpacity: Int = 78,
    horizontalAlignment: String = "center",
    position: String = "center",
    animationType: String = "SLIDE_FROM_BOTTOM",
    animationDuration: Int = 500,
    loopCount: Int = 0,
    isTimer: Boolean = false,
    timerHours: Int = 0,
    timerMinutes: Int = 0,
    timerSeconds: Int = 0,
    timerTextColor: String = "#FFFFFF",
    timerExpiredText: String = "",
    timerMode: String = "duration",
    targetHour: Int = 0,
    targetMinute: Int = 0,
    targetSecond: Int = 0,
    liveClockFormat: String = "HH:mm:ss",
    backdrop: TextBackdrop = TextBackdrop(),
    outline: TextOutline = TextOutline(),
) {
    addOrPush(
        ScheduleItem.AnnouncementItem(
            id = UUID.randomUUID().toString(),
            text = text,
            textColor = textColor,
            backgroundColor = backgroundColor,
            fontSize = fontSize,
            fontType = fontType,
            bold = bold,
            italic = italic,
            underline = underline,
            shadow = shadow,
            shadowColor = shadowColor,
            shadowSize = shadowSize,
            shadowOpacity = shadowOpacity,
            horizontalAlignment = horizontalAlignment,
            position = position,
            animationType = animationType,
            animationDuration = animationDuration,
            loopCount = loopCount,
            isTimer = isTimer,
            timerHours = timerHours,
            timerMinutes = timerMinutes,
            timerSeconds = timerSeconds,
            timerTextColor = timerTextColor,
            timerExpiredText = timerExpiredText,
            timerMode = timerMode,
            targetHour = targetHour,
            targetMinute = targetMinute,
            targetSecond = targetSecond,
            liveClockFormat = liveClockFormat,
            backdrop = backdrop,
            outline = outline,
        )
    )
}

fun ScheduleViewModel.addDictionary(number: String, word: String, transliteration: String, definition: String) {
    addOrPush(
        ScheduleItem.DictionaryItem(
            id = UUID.randomUUID().toString(),
            number = number,
            word = word,
            transliteration = transliteration,
            definition = definition
        )
    )
}
