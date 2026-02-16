package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import java.util.UUID

class ScheduleViewModel {
    private val _scheduleItems: SnapshotStateList<ScheduleItem> = mutableStateListOf()
    val scheduleItems: List<ScheduleItem> get() = _scheduleItems

    fun addSong(songNumber: Int, title: String, songbook: String) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.SongItem(
            id = id,
            songNumber = songNumber,
            title = title,
            songbook = songbook
        )
        _scheduleItems.add(item)
    }

    fun addBibleVerse(bookName: String, chapter: Int, verseNumber: Int, verseText: String) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.BibleVerseItem(
            id = id,
            bookName = bookName,
            chapter = chapter,
            verseNumber = verseNumber,
            verseText = verseText
        )
        _scheduleItems.add(item)
    }

    fun removeItem(id: String) {
        _scheduleItems.removeAll { it.id == id }
    }

    fun moveItemUp(id: String) {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index - 1, item)
        }
    }

    fun moveItemDown(id: String) {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && index < _scheduleItems.size - 1) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index + 1, item)
        }
    }

    fun clearSchedule() {
        _scheduleItems.clear()
    }

    fun getSongItems(): List<ScheduleItem.SongItem> {
        return _scheduleItems.filterIsInstance<ScheduleItem.SongItem>()
    }

    fun getBibleVerseItems(): List<ScheduleItem.BibleVerseItem> {
        return _scheduleItems.filterIsInstance<ScheduleItem.BibleVerseItem>()
    }
}

