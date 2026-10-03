package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongFileParser
import org.churchpresenter.core.models.songs.SongBackground
import org.churchpresenter.core.models.songs.SongItem
import java.io.File

private const val SONG_NUMBER_DIGITS = 4

internal fun SongsViewModel.buildSongFileName(number: String, title: String): String {
    return if (number.isNotBlank()) {
        "${number.padStart(SONG_NUMBER_DIGITS, '0')} - $title.song"
    } else {
        "$title.song"
    }
}

/**
 * Moves the .song file when the songbook, title or number changed, and returns the song with
 * its new path — or null when nothing had to move.
 */
internal fun SongsViewModel.moveSongFile(oldSong: SongItem, newSong: SongItem, storageDir: String): SongItem? {
    val oldFile = File(oldSong.sourceFile)
    if (!oldFile.exists()) return null
    val songbookChanged = oldSong.songbook != newSong.songbook
    val titleChanged = oldSong.title != newSong.title
    val numberChanged = oldSong.number != newSong.number
    if (!songbookChanged && !titleChanged && !numberChanged) return null

    val targetDir = if (songbookChanged) File(storageDir, newSong.songbook) else oldFile.parentFile
    if (!targetDir.exists()) targetDir.mkdirs()
    val newFileName = if (titleChanged || numberChanged) {
        buildSongFileName(newSong.number, newSong.title)
    } else {
        oldFile.name
    }

    val newFile = File(targetDir, newFileName)
    oldFile.copyTo(newFile, overwrite = true)
    if (oldFile.absolutePath != newFile.absolutePath) oldFile.delete()
    if (songbookChanged) deleteIfEmpty(oldFile.parentFile)
    return newSong.copy(sourceFile = newFile.absolutePath)
}

internal fun SongsViewModel.deleteIfEmpty(dir: File?) {
    if (dir != null && dir.isDirectory && dir.listFiles()?.isEmpty() == true) dir.delete()
}

fun SongsViewModel.updateSong(
    oldSong: SongItem,
    newSong: SongItem
): Boolean {
    if (remoteModeActive) return false
    try {
        val storageDir = appSettings.songSettings.storageDirectory
        var songToSave = newSong

        // Handle .song file moves/renames when songbook, title, or number change
        if (oldSong.sourceFile.isNotEmpty() && storageDir.isNotEmpty()) {
            songToSave = moveSongFile(oldSong, newSong, storageDir) ?: songToSave
        }

        // Update in memory
        songsDataState.value.updateSong(oldSong, songToSave)

        // Save to file (pass BOTH old and new song)
        val saved = songsDataState.value.saveSongToFile(oldSong, songToSave, storageDir)

        if (saved) {
            // Remember which song to re-select after async reload
            pendingSelectSourceFile = songToSave.sourceFile
            // Reload songs to reflect changes
            loadSongs()
            // Re-apply current filters to update the filtered list
            applyFilters()
            return true
        }

        return false
    } catch (_: Exception) {
        return false
    }
}

/**
 * Gives every song in [songbook] the same pair of backgrounds, rewriting each `.song` file.
 * Returns how many were written — 0 when nothing matched or the library is remote.
 *
 * A song whose file has gone missing is skipped rather than recreated: the library on disk is
 * the record, and this must not resurrect a song someone deleted outside the app.
 */
fun SongsViewModel.applyBackgroundToSongbook(
    songbook: String,
    background: SongBackground,
    lowerThirdBackground: SongBackground,
): Int {
    if (remoteModeActive || songbook.isBlank()) return 0
    val parser = SongFileParser()
    var written = 0
    allSongItemsState.value
        .filter { it.songbook == songbook && it.sourceFile.isNotBlank() }
        .forEach { song ->
            if (!File(song.sourceFile).exists()) return@forEach
            try {
                parser.writeSongFile(
                    song.copy(background = background, lowerThirdBackground = lowerThirdBackground),
                    song.sourceFile,
                )
                written++
            } catch (_: Exception) {
                // One unwritable file must not abandon the rest of the book.
            }
        }
    if (written > 0) loadSongs()
    return written
}

fun SongsViewModel.createSong(song: SongItem): Boolean {
    if (remoteModeActive) return false
    try {
        val storageDir = appSettings.songSettings.storageDirectory
        if (storageDir.isEmpty() || song.songbook.isBlank()) return false

        val targetDir = File(storageDir, song.songbook)
        if (!targetDir.exists()) targetDir.mkdirs()

        val fileName = if (song.number.isNotBlank()) {
            "${song.number.padStart(SONG_NUMBER_DIGITS, '0')} - ${song.title}.song"
        } else {
            "${song.title}.song"
        }
        val filePath = File(targetDir, fileName).absolutePath

        val parser = SongFileParser()
        parser.writeSongFile(song.copy(sourceFile = filePath), filePath)

        loadSongs()
        return true
    } catch (_: Exception) {
        return false
    }
}

fun SongsViewModel.deleteSong(song: SongItem): Boolean {
    if (remoteModeActive) return false
    return try {
        if (song.sourceFile.isNotEmpty()) {
            val file = File(song.sourceFile)
            if (file.exists()) file.delete()
            val dir = file.parentFile
            if (dir != null && dir.isDirectory && dir.listFiles()?.isEmpty() == true) {
                dir.delete()
            }
        }
        loadSongs()
        true
    } catch (_: Exception) {
        false
    }
}

/**
 * Adds the currently selected song to the schedule.
 * Returns true if successfully added, false otherwise.
 */
fun SongsViewModel.addCurrentSongToSchedule(
    onAdd: (songNumber: Int, title: String, songbook: String, songId: String) -> Unit
): Boolean {
    val items = filteredSongItemsState.value
    val idx = selectedSongIndexState.value
    if (idx < 0 || idx >= items.size) return false
    val song = items[idx]
    onAdd(
        song.number.toIntOrNull() ?: 0,
        song.title,
        song.songbook,
        song.songId
    )
    return true
}
