package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import java.io.File
import java.nio.charset.StandardCharsets

class BibleSettingsViewModel {

    // ── State ────────────────────────────────────────────────────────

    private val _storageDirectory = mutableStateOf("")
    val storageDirectory: String get() = _storageDirectory.value

    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: Int get() = _refreshTrigger.value

    private val _selectedFile = mutableStateOf<String?>(null)
    val selectedFile: String? get() = _selectedFile.value

    // ── Derived ──────────────────────────────────────────────────────

    fun filesInDirectory(): List<String> =
        fileManager.getBibleFilesInDirectory(_storageDirectory.value)

    fun fileDisplayNames(files: List<String>): Map<String, String> {
        val dir = _storageDirectory.value
        if (dir.isEmpty()) return emptyMap()
        return files.associateWith { fileName ->
            extractBibleTitle(File(dir, fileName).absolutePath)
        }
    }

    // ── Actions ──────────────────────────────────────────────────────

    fun setDirectory(path: String) {
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

    private fun extractBibleTitle(filePath: String): String {
        return try {
            File(filePath).bufferedReader(StandardCharsets.UTF_8).use { reader ->
                var title: String? = null
                repeat(10) {
                    val line = reader.readLine() ?: return@use (title ?: File(filePath).nameWithoutExtension)
                    if (line.startsWith("##Title:")) {
                        val t = line.substring(8).trim()
                        title = t
                        return@use t
                    }
                }
                title ?: File(filePath).nameWithoutExtension
            }
        } catch (_: Exception) {
            File(filePath).nameWithoutExtension
        }
    }
}

