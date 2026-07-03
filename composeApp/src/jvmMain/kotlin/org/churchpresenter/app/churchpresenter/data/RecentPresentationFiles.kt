package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

object RecentPresentationFiles {
    private const val MAX = 10
    private val file = File(System.getProperty("user.home"), ".churchpresenter/recent_presentation_files.json")
    private val pinnedFile = File(System.getProperty("user.home"), ".churchpresenter/pinned_presentation_files.json")
    val files = mutableStateListOf<String>()
    val pinned = mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        files.remove(path)
        files.add(0, path)
        while (files.size > MAX) files.removeLast()
        save()
    }

    fun togglePin(path: String) {
        if (path in pinned) {
            pinned.remove(path)
        } else {
            pinned.remove(path)
            pinned.add(0, path)
        }
        savePinned()
    }

    fun clear() {
        val keep = files.filter { it in pinned }
        files.clear()
        files.addAll(keep)
        save()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                files.clear()
                files.addAll(list.take(MAX))
            }
        } catch (_: Exception) {}
        try {
            if (pinnedFile.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(pinnedFile.readText())
                pinned.clear()
                pinned.addAll(list)
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            file.writeText(json.encodeToString(files.toList()))
        } catch (_: Exception) {}
    }

    private fun savePinned() {
        try {
            pinnedFile.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            pinnedFile.writeText(json.encodeToString(pinned.toList()))
        } catch (_: Exception) {}
    }
}
