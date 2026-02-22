package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.models.LyricSection
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.models.SelectedVerse
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import java.io.File
import java.util.UUID
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class ScheduleViewModel {
    private val _scheduleItems: SnapshotStateList<ScheduleItem> = mutableStateListOf()
    val scheduleItems: List<ScheduleItem> get() = _scheduleItems

    private val _selectedItemId = mutableStateOf<String?>(null)
    val selectedItemId get() = _selectedItemId.value

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private var currentFilePath: String? = null

    // ── File I/O ──────────────────────────────────────────────────────────────

    /** Saves to the current file if known, otherwise prompts like Save As. */
    fun saveSchedule(
        dialogTitle: String = "Save Schedule As",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        val existing = currentFilePath
        if (existing != null) {
            val file = File(existing)
            val serialized = json.encodeToString(ListSerializer(ScheduleItem.serializer()), _scheduleItems.toList())
            file.writeText(serialized)
        } else {
            saveScheduleAs(dialogTitle, fileFilterDescription)
        }
    }

    /** Always opens a save-file dialog and serializes the schedule to a .cps file. */
    fun saveScheduleAs(
        dialogTitle: String = "Save Schedule As",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        val chooser = createFileChooser {
            this.dialogTitle = dialogTitle
            fileFilter = FileNameExtensionFilter(fileFilterDescription, "cps")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            var file = chooser.selectedFile
            if (!file.name.endsWith(".cps", ignoreCase = true)) {
                file = file.resolveSibling("${file.name}.cps")
            }
            val serialized = json.encodeToString(ListSerializer(ScheduleItem.serializer()), _scheduleItems.toList())
            file.writeText(serialized)
            currentFilePath = file.absolutePath
        }
    }

    /** Opens an open-file dialog and loads a schedule from a .cps file. */
    fun loadSchedule(
        dialogTitle: String = "Open Schedule",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        val chooser = createFileChooser {
            this.dialogTitle = dialogTitle
            fileFilter = FileNameExtensionFilter(fileFilterDescription, "cps")
            isAcceptAllFileFilterUsed = false
        }
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
            val file = chooser.selectedFile
            if (file.exists()) {
                val items = json.decodeFromString(ListSerializer(ScheduleItem.serializer()), file.readText())
                _scheduleItems.clear()
                _scheduleItems.addAll(items)
                currentFilePath = file.absolutePath
            }
        }
    }

    /** Clears the schedule and forgets the current file path. */
    fun newSchedule() {
        _scheduleItems.clear()
        currentFilePath = null
    }

    // ── Existing methods ──────────────────────────────────────────────────────

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

    fun addMedia(mediaUrl: String, mediaTitle: String, mediaType: String) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.MediaItem(
            id = id,
            mediaUrl = mediaUrl,
            mediaTitle = mediaTitle,
            mediaType = mediaType
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

    fun selectItem(id: String) {
        _selectedItemId.value = if (_selectedItemId.value == id) null else id
    }

    fun clearSelection() {
        _selectedItemId.value = null
    }

    /**
     * Presents a schedule item by coordinating all relevant ViewModels.
     * Triggers the appropriate callbacks for tab switching and presenting mode.
     */
    fun presentItem(
        item: ScheduleItem,
        songsViewModel: SongsViewModel,
        bibleViewModel: BibleViewModel,
        picturesViewModel: PicturesViewModel?,
        presentationViewModel: PresentationViewModel?,
        mediaViewModel: MediaViewModel?,
        presenterManager: PresenterManager?,
        onSongItemSelected: (LyricSection) -> Unit,
        onVerseSelected: (List<SelectedVerse>) -> Unit,
        onPresenting: (Presenting) -> Unit
    ) {
        when (item) {
            is ScheduleItem.SongItem -> {
                songsViewModel.selectSongByDetails(
                    songNumber = item.songNumber,
                    title = item.title,
                    songbook = item.songbook
                )
                songsViewModel.getSelectedLyricSection()?.let { onSongItemSelected(it) }
                onPresenting(Presenting.LYRICS)
            }
            is ScheduleItem.BibleVerseItem -> {
                bibleViewModel.selectVerseByDetails(
                    bookName = item.bookName,
                    chapter = item.chapter,
                    verseNumber = item.verseNumber
                )
                onVerseSelected(bibleViewModel.getSelectedVerses())
                onPresenting(Presenting.BIBLE)
            }
            is ScheduleItem.LabelItem -> {
                // Labels are not presentable
            }
            is ScheduleItem.PictureItem -> {
                if (picturesViewModel != null && presenterManager != null) {
                    val folder = File(item.folderPath)
                    if (folder.exists() && folder.isDirectory) {
                        picturesViewModel.selectFolder(folder)
                        val firstImage = picturesViewModel.getCurrentImageFile()
                        if (firstImage != null) {
                            presenterManager.setSelectedImagePath(firstImage.absolutePath)
                            presenterManager.setPresentingMode(Presenting.PICTURES)
                            presenterManager.setShowPresenterWindow(true)
                        }
                    }
                }
            }
            is ScheduleItem.PresentationItem -> {
                if (presentationViewModel != null && presenterManager != null) {
                    val file = File(item.filePath)
                    if (file.exists()) {
                        presentationViewModel.loadPresentationByPath(item.filePath)
                        presenterManager.setPresentingMode(Presenting.PRESENTATION)
                        presenterManager.setShowPresenterWindow(true)
                    }
                }
            }
            is ScheduleItem.MediaItem -> {
                if (mediaViewModel != null && presenterManager != null) {
                    mediaViewModel.loadMediaFromSchedule(
                        url = item.mediaUrl,
                        title = item.mediaTitle,
                        type = item.mediaType
                    )
                    presenterManager.setPresentingMode(Presenting.MEDIA)
                    presenterManager.setShowPresenterWindow(true)
                }
            }
        }
    }
}

