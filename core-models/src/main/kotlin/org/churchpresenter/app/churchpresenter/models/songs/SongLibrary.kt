package org.churchpresenter.app.churchpresenter.models.songs

import java.io.File

/** What happened when pending changes were written: how many went, and what did not. */
data class SaveOutcome(val saved: Int, val errors: List<String>)

/**
 * The songs under one library folder, and the writes that put changes back.
 *
 * Songs are `.song` files in folders and the folder *is* the songbook, so a song moved to another
 * songbook is a file moved to another folder and a renumbered or renamed song is a file renamed.
 * Both are done here rather than in whatever screen asked for them: both can fail, and the screen
 * has to be able to say which song could not be written.
 *
 * Reading and writing the files themselves is [SongFileParser]'s, so the app and any screen that
 * edits a library agree on the format down to the byte.
 */
class SongLibrary(private val root: File, private val parser: SongFileParser = SongFileParser()) {

    /** Every song under the root, in the order a person reads them: by songbook, then by number. */
    fun load(): List<SongItem> =
        parser.loadSongsFromDirectory(root.absolutePath)
            .map { it.song }
            .sortedWith(compareBy({ it.songbook.lowercase() }, { it.sortKey }, { it.title.lowercase() }))

    /** Every songbook the library holds, including the parents of a nested one. */
    fun songbooks(songs: List<SongItem> = load()): List<String> =
        songs.map { it.songbook }
            .filter { it.isNotBlank() }
            .flatMap { book -> book.split('/').runningReduce { parent, part -> "$parent/$part" } }
            .distinct()
            .sortedBy { it.lowercase() }

    /** The folder [name] names, created empty so a songbook can exist before it holds anything. */
    fun createSongbook(name: String): Boolean {
        val folder = File(root, name.trim().trim('/'))
        if (!folder.toPath().normalize().startsWith(root.toPath().normalize())) return false
        return folder.isDirectory || folder.mkdirs()
    }

    /**
     * Writes every song in [edited] that differs from what [original] holds for it.
     *
     * A song goes to where its number, title and songbook now say it belongs. When that is not
     * where it already is, the new file is written *before* the old one is removed — so a failure
     * halfway through leaves the song on disk twice rather than not at all.
     */
    fun save(original: Map<String, SongItem>, edited: List<SongItem>): SaveOutcome {
        var saved = 0
        val errors = mutableListOf<String>()
        for (song in edited) {
            if (original[song.sourceFile] == song) continue
            runCatching { writeOne(song) }
                .onSuccess { saved++ }
                .onFailure { errors.add("${song.title}: ${it.message ?: it::class.simpleName}") }
        }
        return SaveOutcome(saved, errors)
    }

    /** Deletes the files of [songs], reporting the ones that would not go. */
    fun delete(songs: List<SongItem>): SaveOutcome {
        var deleted = 0
        val errors = mutableListOf<String>()
        for (song in songs) {
            val file = File(song.sourceFile)
            if (!file.exists() || file.delete()) deleted++ else errors.add(song.title)
        }
        return SaveOutcome(deleted, errors)
    }

    /**
     * Writes [song] to a file of its own, and answers with the song as it now sits on disk.
     *
     * This is what Duplicate and New Song produce, and it is written straight away rather than held
     * as a pending change: until it has a file it has no identity, and the file is what every edit
     * afterwards is keyed on.
     */
    fun writeNew(song: SongItem): SongItem {
        val target = freeName(File(root, SongFileName.relativePath(song)))
        val written = song.copy(sourceFile = target.absolutePath)
        target.parentFile?.mkdirs()
        parser.writeSongFile(written, target.absolutePath)
        return written
    }

    private fun writeOne(song: SongItem) {
        val target = File(root, SongFileName.relativePath(song))
        val previous = File(song.sourceFile)
        val moved = target.absolutePath != previous.absolutePath
        val destination = if (moved) freeName(target) else target
        destination.parentFile?.mkdirs()
        parser.writeSongFile(song.copy(sourceFile = destination.absolutePath), destination.absolutePath)
        if (moved && previous.exists()) previous.delete()
    }

    /** [target], or the first `Name (2).song` beside it that nothing else has taken. */
    private fun freeName(target: File): File {
        if (!target.exists()) return target
        val stem = target.nameWithoutExtension
        var counter = 2
        while (true) {
            val candidate = File(target.parentFile, "$stem ($counter).$SONG_EXTENSION")
            if (!candidate.exists()) return candidate
            counter++
        }
    }
}

/** Numbers sort as numbers — 2 before 10 — and a song without one sorts after every song with one. */
private val SongItem.sortKey: Long
    get() = number.toLongOrNull() ?: Long.MAX_VALUE
