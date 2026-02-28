package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.utils.createFileChooser
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

class ScheduleViewModel {
    private val _scheduleItems: SnapshotStateList<ScheduleItem> = mutableStateListOf()
    val scheduleItems: List<ScheduleItem> get() = _scheduleItems

    private val _selectedItemId = mutableStateOf<String?>(null)
    val selectedItemId get() = _selectedItemId.value

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private var currentFilePath: String? = null

    // ── Encryption ────────────────────────────────────────────────────────────

    private companion object {
        private const val PASS = "ChurchPresenter-Schedule-Key-2024"
        private const val ALGO = "AES/CBC/PKCS5Padding"
        private const val KEY_ALGO = "PBKDF2WithHmacSHA256"
        private const val ITERATIONS = 65536
        private const val KEY_LEN = 256
        private const val SALT = "CPScheduleSalt01" // 16-byte fixed salt

        private fun deriveKey(): SecretKeySpec {
            val factory = SecretKeyFactory.getInstance(KEY_ALGO)
            val spec = PBEKeySpec(PASS.toCharArray(), SALT.toByteArray(Charsets.UTF_8), ITERATIONS, KEY_LEN)
            val secret = factory.generateSecret(spec)
            return SecretKeySpec(secret.encoded, "AES")
        }

        fun encrypt(plainText: String): String {
            val key = deriveKey()
            val iv = ByteArray(16).also { SecureRandom().nextBytes(it) }
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.ENCRYPT_MODE, key, IvParameterSpec(iv))
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            // Prepend IV to cipher bytes, then Base64-encode the whole thing
            val combined = iv + encrypted
            return Base64.getEncoder().encodeToString(combined)
        }

        fun decrypt(cipherText: String): String {
            val key = deriveKey()
            val combined = Base64.getDecoder().decode(cipherText)
            val iv = combined.copyOfRange(0, 16)
            val encrypted = combined.copyOfRange(16, combined.size)
            val cipher = Cipher.getInstance(ALGO)
            cipher.init(Cipher.DECRYPT_MODE, key, IvParameterSpec(iv))
            return String(cipher.doFinal(encrypted), Charsets.UTF_8)
        }
    }

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
            file.writeText(encrypt(serialized))
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
            file.writeText(encrypt(serialized))
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
                val raw = file.readText()
                // Support both old plain-JSON files and new encrypted files gracefully
                val jsonText = try { decrypt(raw) } catch (_: Exception) { raw }
                val items = json.decodeFromString(ListSerializer(ScheduleItem.serializer()), jsonText)
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

    fun addLowerThird(presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) {
        val id = UUID.randomUUID().toString()
        val item = ScheduleItem.LowerThirdItem(
            id = id,
            presetId = presetId,
            presetLabel = presetLabel,
            pauseAtFrame = pauseAtFrame,
            pauseDurationMs = pauseDurationMs
        )
        _scheduleItems.add(item)
    }

    fun addAnnouncement(
        text: String,
        textColor: String = "#FFFFFF",
        backgroundColor: String = "#000000",
        fontSize: Int = 48,
        fontType: String = "Arial",
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        shadow: Boolean = false,
        position: String = "center",
        animationType: String = "SLIDE_FROM_BOTTOM",
        animationDuration: Int = 500
    ) {
        val id = UUID.randomUUID().toString()
        _scheduleItems.add(
            ScheduleItem.AnnouncementItem(
                id = id,
                text = text,
                textColor = textColor,
                backgroundColor = backgroundColor,
                fontSize = fontSize,
                fontType = fontType,
                bold = bold,
                italic = italic,
                underline = underline,
                shadow = shadow,
                position = position,
                animationType = animationType,
                animationDuration = animationDuration
            )
        )
    }

    fun addWebsite(url: String, title: String) {
        val id = UUID.randomUUID().toString()
        _scheduleItems.add(ScheduleItem.WebsiteItem(id = id, url = url, title = title.ifBlank { url }))
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
        onPresenting: (Presenting) -> Unit,
        onPresentSong: ((ScheduleItem.SongItem) -> Unit)? = null,
        onPresentBible: ((ScheduleItem.BibleVerseItem) -> Unit)? = null,
        onPresentPresentation: ((ScheduleItem.PresentationItem) -> Unit)? = null,
        onPresentPictures: ((ScheduleItem.PictureItem) -> Unit)? = null,
        onPresentMedia: ((ScheduleItem.MediaItem) -> Unit)? = null
    ) {
        when (item) {
            is ScheduleItem.SongItem -> onPresentSong?.invoke(item) ?: onPresenting(Presenting.LYRICS)
            is ScheduleItem.BibleVerseItem -> onPresentBible?.invoke(item) ?: onPresenting(Presenting.BIBLE)
            is ScheduleItem.LabelItem -> { /* not presentable */ }
            is ScheduleItem.PictureItem -> onPresentPictures?.invoke(item) ?: onPresenting(Presenting.PICTURES)
            is ScheduleItem.PresentationItem -> onPresentPresentation?.invoke(item) ?: onPresenting(Presenting.PRESENTATION)
            is ScheduleItem.MediaItem -> onPresentMedia?.invoke(item) ?: onPresenting(Presenting.MEDIA)
            is ScheduleItem.LowerThirdItem -> { /* lower third shown via LowerThirdTab */ }
            is ScheduleItem.AnnouncementItem -> onPresenting(Presenting.ANNOUNCEMENTS)
            is ScheduleItem.WebsiteItem -> onPresenting(Presenting.WEBSITE)
        }
    }
}

