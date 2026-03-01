package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.useLines

class BibleSettingsViewModel {

    // ── State ────────────────────────────────────────────────────────

    private val _storageDirectory = mutableStateOf(Path(""))
    val storageDirectory: Path get() = _storageDirectory.value

    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: Int get() = _refreshTrigger.value

    private val _selectedFile = mutableStateOf<String?>(null)
    val selectedFile: String? get() = _selectedFile.value

    // ── Derived ──────────────────────────────────────────────────────

    fun filesInDirectory(): List<String> =
        fileManager.getBibleFilesInDirectory(_storageDirectory.value).map { it.name }

    fun fileDisplayNames(files: List<String>): Map<String, String> {
        val dir = _storageDirectory.value
        if (dir.name.isEmpty()) return emptyMap()
        return files.associateWith { fileName ->
            extractBibleTitle(dir.resolve(fileName))
        }
    }

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

    private fun extractBibleTitle(file: Path): String {
        return try {
            file.useLines { lines ->
                lines.find { it.startsWith("##Title:") }?.removePrefix("##Title:")?.trim() ?: file.nameWithoutExtension
            }
        } catch (_: Exception) {
            file.nameWithoutExtension
        }
    }
}

