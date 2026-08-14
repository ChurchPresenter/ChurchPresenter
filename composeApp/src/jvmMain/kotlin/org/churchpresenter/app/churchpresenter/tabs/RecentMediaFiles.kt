package org.churchpresenter.app.churchpresenter.tabs

import kotlinx.serialization.json.Json

/**
 * Recent media files, mirroring `RecentPictureFolders` in `PicturesTab.kt`.
 *
 * `internal` rather than private so the bar it feeds can be driven from a test by seeding [paths]
 * and [pinned]. [file], [pinnedFile] and [load] are `internal var`/`internal fun` for the same
 * reason: a test points them at a temp dir before calling [add]/[togglePin]/[clear]/[load], so the
 * real read/write logic runs without ever touching the developer's own recent/pinned JSON files
 * under `~/.churchpresenter`. Nothing else is widened.
 */
internal object RecentMediaFiles {
    private const val MAX = 10
    internal var file = java.io.File(System.getProperty("user.home"), ".churchpresenter/recent_media_files.json")
    internal var pinnedFile = java.io.File(System.getProperty("user.home"), ".churchpresenter/pinned_media_files.json")
    val paths = androidx.compose.runtime.mutableStateListOf<String>()
    val pinned = androidx.compose.runtime.mutableStateListOf<String>()

    init { load() }

    fun add(path: String) {
        paths.remove(path)
        paths.add(0, path)
        while (paths.size > MAX) paths.removeLast()
        save()
    }

    fun togglePin(path: String) {
        if (path in pinned) pinned.remove(path)
        else { pinned.remove(path); pinned.add(0, path) }
        savePinned()
    }

    fun clear() {
        val keep = paths.filter { it in pinned }
        paths.clear(); paths.addAll(keep); save()
    }

    internal fun load() {
        try {
            if (file.exists()) {
                val json = Json {
                    ignoreUnknownKeys = true
                }; val list = json.decodeFromString<List<String>>(
                    file.readText()
                ); paths.clear(); paths.addAll(list.take(MAX))
            }
        } catch (_: Exception) {}
        try {
            if (pinnedFile.exists()) {
                val json = Json {
                    ignoreUnknownKeys = true
                }; val list = json.decodeFromString<List<String>>(
                    pinnedFile.readText()
                ); pinned.clear(); pinned.addAll(list)
            }
        } catch (_: Exception) {}
    }

    private fun save() {
        try {
            file.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            file.writeText(json.encodeToString(paths.toList())) } catch (_: Exception) {
        }
    }

    private fun savePinned() {
        try {
            pinnedFile.parentFile?.mkdirs()
            val json = Json { encodeDefaults = true }
            pinnedFile.writeText(json.encodeToString(pinned.toList())) } catch (_: Exception) {
        }
    }
}
