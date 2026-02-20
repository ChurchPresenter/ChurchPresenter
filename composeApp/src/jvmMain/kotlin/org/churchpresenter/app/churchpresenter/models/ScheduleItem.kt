package org.churchpresenter.app.churchpresenter.models

import kotlinx.serialization.Serializable

@Serializable
sealed class ScheduleItem {
    abstract val id: String
    abstract val displayText: String

    @Serializable
    data class SongItem(
        override val id: String,
        val songNumber: Int,
        val title: String,
        val songbook: String,
        override val displayText: String = "$songNumber - $title"
    ) : ScheduleItem()

    @Serializable
    data class BibleVerseItem(
        override val id: String,
        val bookName: String,
        val chapter: Int,
        val verseNumber: Int,
        val verseText: String,
        override val displayText: String = "$bookName $chapter:$verseNumber"
    ) : ScheduleItem()

    @Serializable
    data class LabelItem(
        override val id: String,
        val text: String,
        val textColor: String,
        val backgroundColor: String,
        override val displayText: String = text
    ) : ScheduleItem()

    @Serializable
    data class PictureItem(
        override val id: String,
        val folderPath: String,
        val folderName: String,
        val imageCount: Int,
        override val displayText: String = "$folderName ($imageCount images)"
    ) : ScheduleItem()

    @Serializable
    data class PresentationItem(
        override val id: String,
        val filePath: String,
        val fileName: String,
        val slideCount: Int,
        val fileType: String, // "ppt", "pptx", "key", "pdf"
        override val displayText: String = "$fileName ($slideCount slides)"
    ) : ScheduleItem()
}

