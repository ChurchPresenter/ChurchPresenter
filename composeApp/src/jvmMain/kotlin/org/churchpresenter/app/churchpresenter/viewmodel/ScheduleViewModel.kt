package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import org.churchpresenter.app.churchpresenter.dialogs.filechooser.FileChooser
import org.churchpresenter.app.churchpresenter.models.ScheduleItem
import org.churchpresenter.app.churchpresenter.presenter.Presenting
import org.churchpresenter.app.churchpresenter.server.ScheduleItemDto
import org.churchpresenter.app.churchpresenter.utils.CrashReporter
import java.io.File
import java.security.SecureRandom
import java.util.Base64
import java.util.Calendar
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

class ScheduleViewModel(
    private val onScheduleChanged: ((List<ScheduleItem>) -> Unit)? = null
) {
    private val _scheduleItems: SnapshotStateList<ScheduleItem> = mutableStateListOf()
    val scheduleItems: List<ScheduleItem> get() = _scheduleItems

    // ── Instance Link — remote schedule mirroring ────────────────────────────
    // True whenever this instance is following another instance's schedule via Instance Link.
    // While true, local mutation methods below are no-ops — the schedule is driven entirely by
    // applyRemoteSchedule(), called from main.kt whenever InstanceLinkViewModel.remoteSchedule updates.
    private val _isFollowingRemote = mutableStateOf(false)
    val isFollowingRemote: Boolean get() = _isFollowingRemote.value

    /** Replaces the schedule with [dtos] mapped from the primary's `schedule_updated` broadcast. */
    fun applyRemoteSchedule(dtos: List<ScheduleItemDto>) {
        _isFollowingRemote.value = true
        _scheduleItems.clear()
        _scheduleItems.addAll(dtos.mapNotNull { it.toScheduleItem() })
        notifyChanged()
    }

    /** Called on Instance Link disconnect — hands local editing back to the operator. */
    fun stopFollowingRemote() {
        _isFollowingRemote.value = false
    }

    // ── Auto-save ─────────────────────────────────────────────────────────────

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val autoSaveFile = File(System.getProperty("user.home"), ".churchpresenter/autosave_schedule.tmp")
    @Volatile private var isDirty = false

    init {
        scope.launch {
            while (true) {
                delay(60_000)
                if (isDirty && _scheduleItems.isNotEmpty()) {
                    try {
                        autoSaveFile.parentFile?.mkdirs()
                        val scheduleFile = ScheduleFileV2(items = _scheduleItems.toList(), notes = _notes.toMap())
                        val serialized = json.encodeToString(ScheduleFileV2.serializer(), scheduleFile)
                        autoSaveFile.writeText(encrypt(serialized))
                        isDirty = false
                    } catch (_: Exception) {}
                }
            }
        }
    }

    /** Returns true if there is an autosave from today that is less than 4 hours old. */
    fun autoSaveAvailable(): Boolean {
        if (!autoSaveFile.exists() || autoSaveFile.length() == 0L) return false
        val lastModified = autoSaveFile.lastModified()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        cal.timeInMillis = lastModified
        val savedDay = cal.get(Calendar.DAY_OF_YEAR)
        val savedYear = cal.get(Calendar.YEAR)
        cal.timeInMillis = now
        val isSameDay = savedDay == cal.get(Calendar.DAY_OF_YEAR) && savedYear == cal.get(Calendar.YEAR)
        val isWithin4Hours = (now - lastModified) < 4 * 60 * 60 * 1000L
        return isSameDay && isWithin4Hours
    }

    /** Epoch millis of last autosave write, or 0 if no autosave. */
    fun autoSaveSavedAt(): Long = if (autoSaveFile.exists()) autoSaveFile.lastModified() else 0L

    /** Loads the autosave into the current schedule. Returns true on success. */
    fun restoreAutoSave(): Boolean {
        if (_isFollowingRemote.value || !autoSaveFile.exists()) return false
        return try {
            val raw = autoSaveFile.readText()
            val jsonText = try { decrypt(raw) } catch (_: Exception) { raw }
            val (items, notes) = try {
                val schedFile = json.decodeFromString(ScheduleFileV2.serializer(), jsonText)
                Pair(schedFile.items, schedFile.notes)
            } catch (_: Exception) {
                Pair(json.decodeFromString(ListSerializer(ScheduleItem.serializer()), jsonText), emptyMap())
            }
            _scheduleItems.clear()
            _scheduleItems.addAll(items)
            _notes.clear()
            _notes.putAll(notes)
            currentFilePath = null
            undoStack.clear()
            redoStack.clear()
            _canUndo.value = false
            _canRedo.value = false
            clearAutoSave()
            notifyChanged()
            true
        } catch (_: Exception) { false }
    }

    fun clearAutoSave() {
        try { autoSaveFile.delete() } catch (_: Exception) {}
    }

    fun dispose() {
        scope.cancel()
    }

    private fun notifyChanged() {
        isDirty = true
        onScheduleChanged?.invoke(_scheduleItems.toList())
    }

    private val _selectedItemId = mutableStateOf<String?>(null)
    val selectedItemId get() = _selectedItemId.value

    private val json = Json { prettyPrint = true; encodeDefaults = true }
    private var currentFilePath: String? = null

    // ── Undo / Redo ───────────────────────────────────────────────────────────

    private data class ScheduleSnapshot(
        val items: List<ScheduleItem>,
        val notes: Map<String, String>
    )

    private val undoStack = ArrayDeque<ScheduleSnapshot>()
    private val redoStack = ArrayDeque<ScheduleSnapshot>()

    private val _canUndo = mutableStateOf(false)
    private val _canRedo = mutableStateOf(false)
    val canUndo: Boolean get() = _canUndo.value
    val canRedo: Boolean get() = _canRedo.value

    private fun pushUndoSnapshot() {
        undoStack.addLast(ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap()))
        if (undoStack.size > 50) undoStack.removeFirst()
        redoStack.clear()
        _canUndo.value = true
        _canRedo.value = false
    }

    fun undo() {
        if (_isFollowingRemote.value || undoStack.isEmpty()) return
        redoStack.addLast(ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap()))
        val snapshot = undoStack.removeLast()
        _scheduleItems.clear()
        _scheduleItems.addAll(snapshot.items)
        _notes.clear()
        _notes.putAll(snapshot.notes)
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = true
        notifyChanged()
    }

    fun redo() {
        if (_isFollowingRemote.value || redoStack.isEmpty()) return
        undoStack.addLast(ScheduleSnapshot(_scheduleItems.toList(), _notes.toMap()))
        val snapshot = redoStack.removeLast()
        _scheduleItems.clear()
        _scheduleItems.addAll(snapshot.items)
        _notes.clear()
        _notes.putAll(snapshot.notes)
        _canUndo.value = true
        _canRedo.value = redoStack.isNotEmpty()
        notifyChanged()
    }

    // ── Notes ─────────────────────────────────────────────────────────────────

    private val _notes = mutableStateMapOf<String, String>()

    fun getNote(itemId: String): String = _notes[itemId] ?: ""

    fun setNote(itemId: String, note: String) {
        if (note.isBlank()) _notes.remove(itemId) else _notes[itemId] = note
    }

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
    suspend fun saveSchedule(
        dialogTitle: String = "Save Schedule As",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        val existing = currentFilePath
        if (existing != null) {
            val file = File(existing)
            val scheduleFile = ScheduleFileV2(items = _scheduleItems.toList(), notes = _notes.toMap())
            val serialized = json.encodeToString(ScheduleFileV2.serializer(), scheduleFile)
            file.writeText(encrypt(serialized))
            clearAutoSave()
            CrashReporter.breadcrumb("Schedule saved (${file.name})", category = "schedule")
        } else {
            saveScheduleAs(dialogTitle, fileFilterDescription)
        }
    }

    /** Always opens a save-file dialog and serializes the schedule to a .cps file. */
    suspend fun saveScheduleAs(
        dialogTitle: String = "Save Schedule As",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        val file = FileChooser.platformInstance.save(
            location = null,
            suggestedName = "schedule.cps",
            filters = listOf(FileNameExtensionFilter(fileFilterDescription, "cps")),
            title = dialogTitle
        )
        if (file != null) {
            val scheduleFile = ScheduleFileV2(items = _scheduleItems.toList(), notes = _notes.toMap())
            val serialized = json.encodeToString(ScheduleFileV2.serializer(), scheduleFile)
            file.writeText(encrypt(serialized))
            currentFilePath = file.absolutePathString()
            clearAutoSave()
            CrashReporter.breadcrumb("Schedule saved as (${file.fileName})", category = "schedule")
        }
    }

    /** Opens an open-file dialog and loads a schedule from a .cps file. */
    suspend fun loadSchedule(
        dialogTitle: String = "Open Schedule",
        fileFilterDescription: String = "Church Presenter Schedule (*.cps)"
    ) {
        if (_isFollowingRemote.value) return
        val file = FileChooser.platformInstance.chooseSingle(
            path = null,
            filters = listOf(FileNameExtensionFilter(fileFilterDescription, "cps")),
            title = dialogTitle,
            selectDirectory = false
        )
        if (file != null) {
            if (file.exists()) {
                try {
                    val raw = file.readText()
                    val jsonText = try { decrypt(raw) } catch (_: Exception) { raw }
                    // Try new format (v2 with notes), fall back to legacy plain array
                    val (items, notes) = try {
                        val schedFile = json.decodeFromString(ScheduleFileV2.serializer(), jsonText)
                        Pair(schedFile.items, schedFile.notes)
                    } catch (_: Exception) {
                        Pair(
                            json.decodeFromString(ListSerializer(ScheduleItem.serializer()), jsonText),
                            emptyMap()
                        )
                    }
                    _scheduleItems.clear()
                    _scheduleItems.addAll(items)
                    _notes.clear()
                    _notes.putAll(notes)
                    currentFilePath = file.absolutePathString()
                    undoStack.clear()
                    redoStack.clear()
                    _canUndo.value = false
                    _canRedo.value = false
                    clearAutoSave()
                    notifyChanged()
                    CrashReporter.breadcrumb("Schedule opened (${file.fileName}, ${items.size} items)", category = "schedule")
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    /** Clears the schedule and forgets the current file path. */
    fun newSchedule() {
        if (_isFollowingRemote.value) return
        _scheduleItems.clear()
        _notes.clear()
        currentFilePath = null
        undoStack.clear()
        redoStack.clear()
        _canUndo.value = false
        _canRedo.value = false
        clearAutoSave()
        notifyChanged()
    }

    // ── Existing methods ──────────────────────────────────────────────────────

    fun addSong(songNumber: Int, title: String, songbook: String, songId: String = "") {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.SongItem(id = UUID.randomUUID().toString(), songNumber = songNumber, title = title, songbook = songbook, songId = songId))
        notifyChanged()
    }

    fun addBibleVerse(bookName: String, chapter: Int, verseNumber: Int, verseText: String, verseRange: String = "") {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.BibleVerseItem(
            id = UUID.randomUUID().toString(),
            bookName = bookName,
            chapter = chapter,
            verseNumber = verseNumber,
            verseText = verseText,
            verseRange = verseRange
        ))
        notifyChanged()
    }

    fun addLabel(text: String, textColor: String, backgroundColor: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.LabelItem(id = UUID.randomUUID().toString(), text = text, textColor = textColor, backgroundColor = backgroundColor))
        notifyChanged()
    }

    fun addPicture(folderPath: String, folderName: String, imageCount: Int) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.PictureItem(id = UUID.randomUUID().toString(), folderPath = folderPath, folderName = folderName, imageCount = imageCount))
        notifyChanged()
    }

    fun addPresentation(filePath: String, fileName: String, slideCount: Int, fileType: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.PresentationItem(id = UUID.randomUUID().toString(), filePath = filePath, fileName = fileName, slideCount = slideCount, fileType = fileType))
        notifyChanged()
    }

    fun addMedia(mediaUrl: String, mediaTitle: String, mediaType: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.MediaItem(id = UUID.randomUUID().toString(), mediaUrl = mediaUrl, mediaTitle = mediaTitle, mediaType = mediaType))
        notifyChanged()
    }

    fun addLowerThird(presetId: String, presetLabel: String, pauseAtFrame: Boolean, pauseDurationMs: Long) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.LowerThirdItem(id = UUID.randomUUID().toString(), presetId = presetId, presetLabel = presetLabel, pauseAtFrame = pauseAtFrame, pauseDurationMs = pauseDurationMs))
        notifyChanged()
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
        horizontalAlignment: String = "center",
        position: String = "center",
        animationType: String = "SLIDE_FROM_BOTTOM",
        animationDuration: Int = 500,
        isTimer: Boolean = false,
        timerHours: Int = 0,
        timerMinutes: Int = 0,
        timerSeconds: Int = 0,
        timerTextColor: String = "#FFFFFF",
        timerExpiredText: String = "",
        timerMode: String = "duration",
        targetHour: Int = 0,
        targetMinute: Int = 0,
        targetSecond: Int = 0
    ) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
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
                horizontalAlignment = horizontalAlignment,
                position = position,
                animationType = animationType,
                animationDuration = animationDuration,
                isTimer = isTimer,
                timerHours = timerHours,
                timerMinutes = timerMinutes,
                timerSeconds = timerSeconds,
                timerTextColor = timerTextColor,
                timerExpiredText = timerExpiredText,
                timerMode = timerMode,
                targetHour = targetHour,
                targetMinute = targetMinute,
                targetSecond = targetSecond
            )
        )
        notifyChanged()
    }

    fun addWebsite(url: String, title: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.WebsiteItem(id = UUID.randomUUID().toString(), url = url, title = title.ifBlank { url }))
        notifyChanged()
    }

    fun addScene(sceneId: String, sceneName: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.SceneItem(id = UUID.randomUUID().toString(), sceneId = sceneId, sceneName = sceneName))
        notifyChanged()
    }

    fun addDictionary(number: String, word: String, transliteration: String, definition: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.add(ScheduleItem.DictionaryItem(id = UUID.randomUUID().toString(), number = number, word = word, transliteration = transliteration, definition = definition))
        notifyChanged()
    }

    fun updateWebsiteTitle(url: String, title: String) {
        if (_isFollowingRemote.value || title.isBlank()) return
        val index = _scheduleItems.indexOfFirst { it is ScheduleItem.WebsiteItem && it.url == url }
        if (index >= 0) {
            val existing = _scheduleItems[index] as ScheduleItem.WebsiteItem
            // Only update if the current title is still the URL (i.e. no real title was set yet)
            if (existing.title == existing.url || existing.title.isBlank()) {
                pushUndoSnapshot()
                _scheduleItems[index] = existing.copy(title = title)
                notifyChanged()
            }
        }
    }

    fun updateLabel(id: String, text: String, textColor: String, backgroundColor: String) {
        if (_isFollowingRemote.value) return
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && _scheduleItems[index] is ScheduleItem.LabelItem) {
            pushUndoSnapshot()
            _scheduleItems[index] = ScheduleItem.LabelItem(id = id, text = text, textColor = textColor, backgroundColor = backgroundColor)
            notifyChanged()
        }
    }

    fun removeItem(id: String) {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.removeAll { it.id == id }
        _notes.remove(id)
        notifyChanged()
    }

    fun moveItemUp(id: String): Int {
        if (_isFollowingRemote.value) return -1
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index > 0) {
            pushUndoSnapshot()
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index - 1, item)
            notifyChanged()
            return index - 1
        }
        return index
    }

    fun moveItemDown(id: String): Int {
        if (_isFollowingRemote.value) return -1
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && index < _scheduleItems.size - 1) {
            pushUndoSnapshot()
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(index + 1, item)
            notifyChanged()
            return index + 1
        }
        return index
    }

    fun moveItemToTop(id: String): Int {
        if (_isFollowingRemote.value) return -1
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index > 0) {
            pushUndoSnapshot()
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(0, item)
            notifyChanged()
            return 0
        }
        return index
    }

    fun moveItemToBottom(id: String): Int {
        if (_isFollowingRemote.value) return -1
        val index = _scheduleItems.indexOfFirst { it.id == id }
        if (index >= 0 && index < _scheduleItems.size - 1) {
            pushUndoSnapshot()
            val item = _scheduleItems.removeAt(index)
            _scheduleItems.add(item)
            notifyChanged()
            return _scheduleItems.size - 1
        }
        return index
    }

    fun moveItem(from: Int, to: Int) {
        if (_isFollowingRemote.value) return
        if (from < 0 || to < 0 || from >= _scheduleItems.size || to >= _scheduleItems.size || from == to) return
        pushUndoSnapshot()
        val item = _scheduleItems.removeAt(from)
        _scheduleItems.add(to, item)
        notifyChanged()
    }

    fun clearSchedule() {
        if (_isFollowingRemote.value) return
        pushUndoSnapshot()
        _scheduleItems.clear()
        _notes.clear()
        notifyChanged()
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
        onPresentMedia: ((ScheduleItem.MediaItem) -> Unit)? = null,
        onPresentAnnouncement: ((ScheduleItem.AnnouncementItem) -> Unit)? = null,
        onPresentLowerThird: ((ScheduleItem.LowerThirdItem) -> Unit)? = null,
        onPresentWebsite: ((ScheduleItem.WebsiteItem) -> Unit)? = null,
        onPresentScene: ((ScheduleItem.SceneItem) -> Unit)? = null,
        onPresentDictionary: ((ScheduleItem.DictionaryItem) -> Unit)? = null
    ) {
        when (item) {
            is ScheduleItem.SongItem -> onPresentSong?.invoke(item) ?: onPresenting(Presenting.LYRICS)
            is ScheduleItem.BibleVerseItem -> onPresentBible?.invoke(item) ?: onPresenting(Presenting.BIBLE)
            is ScheduleItem.LabelItem -> { /* not presentable */ }
            is ScheduleItem.PictureItem -> onPresentPictures?.invoke(item) ?: onPresenting(Presenting.PICTURES)
            is ScheduleItem.PresentationItem -> onPresentPresentation?.invoke(item) ?: onPresenting(Presenting.PRESENTATION)
            is ScheduleItem.MediaItem -> onPresentMedia?.invoke(item) ?: onPresenting(Presenting.MEDIA)
            is ScheduleItem.LowerThirdItem -> onPresentLowerThird?.invoke(item)
            is ScheduleItem.AnnouncementItem -> onPresentAnnouncement?.invoke(item) ?: onPresenting(Presenting.ANNOUNCEMENTS)
            is ScheduleItem.WebsiteItem -> onPresentWebsite?.invoke(item) ?: onPresenting(Presenting.WEBSITE)
            is ScheduleItem.SceneItem -> onPresentScene?.invoke(item) ?: onPresenting(Presenting.CANVAS)
            is ScheduleItem.DictionaryItem -> onPresentDictionary?.invoke(item) ?: onPresenting(Presenting.ANNOUNCEMENTS)
        }
    }

    /**
     * Inverse of [org.churchpresenter.app.churchpresenter.server.CompanionServer.updateSchedule]'s
     * ScheduleItem → ScheduleItemDto mapping. Some fields the DTO doesn't carry (extra announcement
     * formatting/timer options, lower-third pause settings, scene id, dictionary detail beyond the
     * word) fall back to defaults — a known limitation of mirroring through the flat companion DTO
     * shape rather than the full sealed [ScheduleItem].
     */
    private fun ScheduleItemDto.toScheduleItem(): ScheduleItem? = when (type) {
        "song" -> ScheduleItem.SongItem(id = id, songNumber = songNumber ?: 0, title = title ?: "", songbook = songbook ?: "")
        "bible" -> ScheduleItem.BibleVerseItem(
            id = id, bookName = bookName ?: "", chapter = chapter ?: 0, verseNumber = verseNumber ?: 0,
            verseText = text ?: "", verseRange = verseRange ?: ""
        )
        "label" -> ScheduleItem.LabelItem(id = id, text = text ?: "", textColor = textColor ?: "#FFFFFF", backgroundColor = backgroundColor ?: "#2196F3")
        "picture" -> ScheduleItem.PictureItem(id = id, folderPath = folderPath ?: "", folderName = folderName ?: "", imageCount = imageCount ?: 0)
        "presentation" -> ScheduleItem.PresentationItem(
            id = id, filePath = filePath ?: "", fileName = fileName ?: "", slideCount = slideCount ?: 0, fileType = fileType ?: ""
        )
        "media" -> ScheduleItem.MediaItem(id = id, mediaUrl = mediaUrl ?: "", mediaTitle = mediaTitle ?: "", mediaType = mediaType ?: "")
        "lower_third" -> ScheduleItem.LowerThirdItem(
            id = id, presetId = presetId ?: "", presetLabel = presetLabel ?: "", pauseAtFrame = false, pauseDurationMs = 2000L
        )
        "announcement" -> ScheduleItem.AnnouncementItem(id = id, text = text ?: "", textColor = textColor ?: "#FFFFFF", backgroundColor = backgroundColor ?: "#000000")
        "website" -> ScheduleItem.WebsiteItem(id = id, url = url ?: "", title = title ?: url ?: "")
        "scene" -> ScheduleItem.SceneItem(id = id, sceneId = "", sceneName = displayText)
        "dictionary" -> ScheduleItem.DictionaryItem(id = id, number = "", word = displayText, transliteration = "", definition = "")
        else -> null
    }
}

@Serializable
private data class ScheduleFileV2(
    val version: Int = 2,
    val items: List<ScheduleItem>,
    val notes: Map<String, String> = emptyMap()
)
