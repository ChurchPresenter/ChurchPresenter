package org.churchpresenter.app.churchpresenter.viewmodel

import androidx.compose.runtime.mutableStateOf
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.copyTo
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText

class LowerThirdSettingsViewModel {

    // ── State ────────────────────────────────────────────────────────

    private val _lottieFolder = mutableStateOf<Path?>(null)
    val lottieFolder: Path? get() = _lottieFolder.value

    private val _refreshTrigger = mutableStateOf(0)
    val refreshTrigger: Int get() = _refreshTrigger.value

    private val _selectedFile = mutableStateOf<String?>(null)
    val selectedFile: String? get() = _selectedFile.value

    // ── Derived state ────────────────────────────────────────────────

    fun filesInDirectory(): List<String> {
        return lottieFolder
            ?.takeIf { it.exists() && it.isDirectory() }
            ?.listDirectoryEntries("*.json")
            ?.map { it.name }
            ?.sorted()
            ?: emptyList()
    }

    fun previewJsonContent(): String {
        val fname = _selectedFile.value ?: return ""
        val folder = _lottieFolder.value ?: return ""
        val f = folder.resolve(fname)
        return if (f.exists()) f.readText() else ""
    }

    fun importSourcePath(): String {
        val fname = _selectedFile.value ?: return ""
        val folder = _lottieFolder.value ?: return ""
        return folder.resolve(fname).absolutePathString()
    }

    // ── Actions ──────────────────────────────────────────────────────

    fun setFolder(path: Path) {
        _lottieFolder.value = path
        _selectedFile.value = null
        _refreshTrigger.value++
    }

    fun selectFile(name: String) {
        _selectedFile.value = name
    }

    fun importFile(sourcePath: Path) {
        val folder = _lottieFolder.value ?: return
        sourcePath.copyTo(folder.resolve(sourcePath.name), overwrite = true)
        _selectedFile.value = sourcePath.name
        _refreshTrigger.value++
    }

    fun removeSelectedFile() {
        val fname = _selectedFile.value ?: return
        val folder = _lottieFolder.value ?: return
        folder.resolve(fname).deleteIfExists()
        _selectedFile.value = null
        _refreshTrigger.value++
    }

    fun onFileSavedFromGenerator() {
        _refreshTrigger.value++
    }
}

