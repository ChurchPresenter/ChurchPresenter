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

    fun addLabel(text: String, textColor: String, backgroundColor: String) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.LabelItem(
            id = id,
            text = text,
            textColor = textColor,
            backgroundColor = backgroundColor
        )
        _scheduleItems.add(item)
    }

    fun addPicture(folderPath: String, folderName: String, imageCount: Int) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.PictureItem(
            id = id,
            folderPath = folderPath,
            folderName = folderName,
            imageCount = imageCount
        )
        _scheduleItems.add(item)
    }

    fun addPresentation(filePath: String, fileName: String, slideCount: Int, fileType: String) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.PresentationItem(
            id = id,
            filePath = filePath,
            fileName = fileName,
            slideCount = slideCount,
            fileType = fileType
        )
        _scheduleItems.add(item)
    }

    fun updateLabel(id: String, text: String, textColor: String, backgroundColor: String) {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && _scheduleItems[index] is ScheduleItem.LabelItem) {
            val updatedItem = ScheduleItem.LabelItem(
                id = id,
                text = text,
                textColor = textColor,
                backgroundColor = backgroundColor
            )
            _scheduleItems[index] = updatedItem
        }
    }

    fun removeItem(id: String) {
        _scheduleItems.removeAll { it.id == id }
    }

    fun moveItemUp(id: String): Int {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index - 1, item)
            return index - 1
        }
        return index
    }

    fun moveItemDown(id: String): Int {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && index < _scheduleItems.size - 1) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index + 1, item)
            return index + 1
        }
        return index
    }

    fun moveItemToTop(id: String): Int {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index > 0) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(0, item)
            return 0
        }
        return index
    }

    fun moveItemToBottom(id: String): Int {
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && index < _scheduleItems.size - 1) {
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(item)
            return _scheduleItems.size - 1
        }
        return index
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

