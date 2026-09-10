package org.churchpresenter.app.churchpresenter.data

import androidx.compose.runtime.mutableStateListOf
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import org.churchpresenter.core.models.io.writeTextAtomically

/**
 * Recently-opened presentation files, and the pinned subset that survives "clear recents".
 *
 * [file], [pinnedFile] and [load] are `internal var`/`internal fun` so a test can point them at a
 * temp directory before calling [add]/[togglePin]/[clear], exactly as `RecentPictureFolders` and
 * `RecentMediaFiles` already allow. Use `RecentFilesSwap` from the test source set rather than
 * assigning them by hand — it restores both paths and both lists in one place, so a test cannot
 * leave the object pointing somewhere the next test does not expect.
 *
 * This is not about protecting the developer's own files: the Gradle test config already points
 * `user.home` at `build/test-home` for the whole JVM. It is about each test getting its own file
 * instead of sharing one across the class.
 */
object RecentPresentationFiles {
    private const val MAX = 10
    internal var file = File(System.getProperty("user.home"), ".churchpresenter/recent_presentation_files.json")
    internal var pinnedFile = File(System.getProperty("user.home"), ".churchpresenter/pinned_presentation_files.json")
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

    internal fun load() {
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
            file.writeTextAtomically(json.encodeToString(files.toList()))
        } catch (_: Exception) {}
    }

    private fun savePinned() {
        try {
            pinnedFile.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            pinnedFile.writeTextAtomically(json.encodeToString(pinned.toList()))
        } catch (_: Exception) {}
    }
}
