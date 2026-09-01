package org.churchpresenter.app.churchpresenter.dialogs

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * What the wizard's last step reports: what is actually on disk once the earlier steps are done.
 *
 * The old final step said "you're all set" and nothing else, which is the one claim a setup wizard
 * cannot make on faith — a user who mistyped a folder path was congratulated exactly as loudly as
 * one who had a library. These are counted from the two directories the wizard has just walked the
 * user through choosing, so the step either confirms the setup or shows a zero.
 */
internal data class SetupSummary(
    val bibleTranslations: Int,
    val songBooks: Int,
    val songs: Int,
)

/** The extension an installed Bible translation carries. */
private const val BIBLE_EXTENSION = "spb"

/** The extension every song in a library carries; mirrors `SONG_EXTENSION` in `:core-models`. */
private const val SONG_EXTENSION = "song"

/** How deep the song scan walks before giving up, so a folder chosen by mistake cannot hang it. */
private const val SONG_SCAN_DEPTH = 4

/**
 * Counts the installed translations and the song library, off the composition thread.
 *
 * Deliberately tolerant: either directory may be blank (never chosen), missing (typed by hand and
 * wrong) or unreadable, and each of those is a zero rather than a thrown exception — the summary is
 * a reassurance, and a wizard that crashes on its own last step is worse than one that says nothing.
 */
internal suspend fun loadSetupSummary(bibleDirectory: String, songsDirectory: String): SetupSummary =
    withContext(Dispatchers.IO) {
        SetupSummary(
            bibleTranslations = countBibleTranslations(bibleDirectory),
            songBooks = countSongBooks(songsDirectory),
            songs = countSongs(songsDirectory),
        )
    }

/** Installed translations: the `.spb` files sitting directly in the Bible folder. */
internal fun countBibleTranslations(directory: String): Int =
    readableDirectory(directory)
        ?.listFiles { file -> file.isFile && file.extension.equals(BIBLE_EXTENSION, ignoreCase = true) }
        ?.size
        ?: 0

/**
 * Song books: the immediate subfolders of the library that contain at least one song.
 *
 * A library is conventionally one folder per book, but a user may equally point the app at a flat
 * folder of `.song` files. That case is one book, not none, which is why a flat folder holding songs
 * counts as 1 rather than falling through to the subfolder tally.
 */
internal fun countSongBooks(directory: String): Int {
    val root = readableDirectory(directory) ?: return 0
    val subfolderBooks = root.listFiles { file -> file.isDirectory }
        ?.count { folder -> songFilesIn(folder, SONG_SCAN_DEPTH - 1) > 0 }
        ?: 0
    val loose = root.listFiles { file -> file.isFile && file.extension.equals(SONG_EXTENSION, true) }?.size ?: 0
    return subfolderBooks + if (loose > 0) 1 else 0
}

/** Every song in the library, however deeply it is filed. */
internal fun countSongs(directory: String): Int =
    readableDirectory(directory)?.let { songFilesIn(it, SONG_SCAN_DEPTH) } ?: 0

private fun songFilesIn(directory: File, depth: Int): Int {
    if (depth <= 0) return 0
    val entries = directory.listFiles() ?: return 0
    var total = 0
    for (entry in entries) {
        total += when {
            entry.isFile && entry.extension.equals(SONG_EXTENSION, ignoreCase = true) -> 1
            entry.isDirectory -> songFilesIn(entry, depth - 1)
            else -> 0
        }
    }
    return total
}

/** The folder, or null when it was never chosen, does not exist, or cannot be read. */
private fun readableDirectory(path: String): File? {
    if (path.isBlank()) return null
    val file = File(path)
    return if (file.isDirectory && file.canRead()) file else null
}
