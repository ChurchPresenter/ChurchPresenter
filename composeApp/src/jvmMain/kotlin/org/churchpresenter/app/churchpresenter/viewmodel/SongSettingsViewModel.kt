package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import java.nio.file.Path
import kotlin.io.path.name

class SongSettingsViewModel {

    // ── State ────────────────────────────────────────────────────────

    private val _storageDirectory = mutableStateOf<Path?>(null)
    val storageDirectory: Path? get() = _storageDirectory.value

    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: Int get() = _refreshTrigger.value

    private val _selectedFile = mutableStateOf<String?>(null)
    val selectedFile: String? get() = _selectedFile.value

    // ── Derived ──────────────────────────────────────────────────────

    fun filesInDirectory(): List<String> =
        fileManager.getSongFilesInDirectory(_storageDirectory.value ?: return emptyList()).map { it.name }

    // ── Actions ──────────────────────────────────────────────────────

    fun setDirectory(path: Path) {
        _storageDirectory.value = path
        _selectedFile.value = null
        _refreshTrigger.value++
    }

    fun selectFile(name: String?) {
        _selectedFile.value = name
    }

    fun refresh() {
        _refreshTrigger.value++
    }

    // ── Internal helpers ─────────────────────────────────────────────

    val fileManager = FileManager()
}

