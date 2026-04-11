package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import java.io.File

/** Quick check whether a JSON file looks like a Lottie animation (has "v" and "layers" keys). */
fun isLottieFile(file: File): Boolean {
    return try {
        val text = file.readText()
        text.contains("\"v\"") && text.contains("\"layers\"")
    } catch (_: Exception) { false }
}

class LowerThirdSettingsViewModel {

    // ── State ────────────────────────────────────────────────────────

    private val _lottieFolder = mutableStateOf("")
    val lottieFolder: String get() = _lottieFolder.value

    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: Int get() = _refreshTrigger.value

    private val _selectedFile = mutableStateOf<String?>(null)
    val selectedFile: String? get() = _selectedFile.value

    // ── Derived state ────────────────────────────────────────────────

    fun filesInDirectory(): List<String> {
        val folder = _lottieFolder.value
        if (folder.isEmpty()) return emptyList()
        return File(folder)
            .takeIf { it.exists() && it.isDirectory }
            ?.listFiles { f -> f.extension.lowercase() == "json" && isLottieFile(f) }
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun previewJsonContent(): String {
        val fname = _selectedFile.value ?: return ""
        val folder = _lottieFolder.value
        if (folder.isEmpty()) return ""
        val f = File(folder, fname)
        return if (f.exists()) f.readText() else ""
    }

    fun importSourcePath(): String {
        val fname = _selectedFile.value ?: return ""
        val folder = _lottieFolder.value
        if (folder.isEmpty()) return ""
        return File(folder, fname).absolutePath
    }

    // ── Actions ──────────────────────────────────────────────────────

    fun setFolder(path: String) {
        _lottieFolder.value = path
        _selectedFile.value = null
        _refreshTrigger.value++
    }

    fun selectFile(name: String) {
        _selectedFile.value = name
    }

    fun importFile(sourcePath: String) {
        val folder = _lottieFolder.value
        if (folder.isEmpty()) return
        val src = File(sourcePath)
        val target = File(folder, src.name)
        val folderCanonical = File(folder).canonicalPath
        if (!target.canonicalPath.startsWith(folderCanonical + File.separator) &&
            target.canonicalPath != folderCanonical) {
            return
        }
        src.copyTo(target, overwrite = true)
        _selectedFile.value = src.name
        _refreshTrigger.value++
    }

    fun removeSelectedFile() {
        val fname = _selectedFile.value ?: return
        val folder = _lottieFolder.value
        if (folder.isEmpty()) return
        File(folder, fname).delete()
        _selectedFile.value = null
        _refreshTrigger.value++
    }

    fun onFileSavedFromGenerator() {
        _refreshTrigger.value++
    }
}

