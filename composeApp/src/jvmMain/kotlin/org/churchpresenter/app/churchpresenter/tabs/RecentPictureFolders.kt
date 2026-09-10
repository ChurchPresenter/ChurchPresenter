package org.churchpresenter.app.churchpresenter.tabs

import kotlinx.serialization.json.Json
import java.io.File
import org.churchpresenter.core.models.io.writeTextAtomically

/**
 * Recent picture folders, mirroring `data/RecentPresentationFiles` for the Pictures tab.
 *
 * `internal` rather than private so the bar it feeds can be driven from a test by seeding [folders]
 * and [pinned] — they are the state the bar renders from, and the sibling object this copies is
 * public for the same reason. [file], [pinnedFile] and [load] are `internal var`/`internal fun` for
 * the same reason: a test points them at a temp dir before calling [add]/[togglePin]/[clear]/[load],
 * so the real read/write logic runs without ever touching the developer's own recent/pinned JSON
 * files under `~/.churchpresenter`. Nothing else is widened.
 */
internal object RecentPictureFolders {
    private const val MAX = 10
    internal var file = File(System.getProperty("user.home"), ".churchpresenter/recent_picture_folders.json")
    internal var pinnedFile = File(System.getProperty("user.home"), ".churchpresenter/pinned_picture_folders.json")
    val folders = androidx.compose.runtime.mutableStateListOf<String>()
    val pinned = androidx.compose.runtime.mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        folders.remove(path)
        folders.add(0, path)
        while (folders.size > MAX) folders.removeLast()
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
        val keep = folders.filter { it in pinned }
        folders.clear()
        folders.addAll(keep)
        save()
    }

    internal fun load() {
        try {
            if (file.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                val list = json.decodeFromString<List<String>>(file.readText())
                folders.clear()
                folders.addAll(list.take(MAX))
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
            file.writeTextAtomically(json.encodeToString(folders.toList()))
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
