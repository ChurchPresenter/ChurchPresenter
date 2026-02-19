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
}

