package org.churchpresenter.core.models.songs

import java.io.File

/** Which of a song's fields a grid can edit in place, and how each is read and written. */
enum class SongField {
    NUMBER,
    TITLE,
    SECONDARY_TITLE,
    SONGBOOK,
    AUTHOR,
    COMPOSER,
    TUNE,
    CCLI,
    ;

    /** True when changing this field renames or moves the song's file. */
    val movesFile: Boolean get() = this == NUMBER || this == TITLE || this == SONGBOOK

    fun of(song: SongItem): String = when (this) {
        NUMBER -> song.number
        TITLE -> song.title
        SECONDARY_TITLE -> song.secondaryTitle
        SONGBOOK -> song.songbook
        AUTHOR -> song.author
        COMPOSER -> song.composer
        TUNE -> song.tune
        CCLI -> song.ccliNumber
    }

    fun set(song: SongItem, value: String): SongItem = when (this) {
        NUMBER -> song.copy(number = value.trim())
        TITLE -> song.copy(title = value.trim())
        SECONDARY_TITLE -> song.withTranslation(0) { it.copy(title = value.trim()) }
        SONGBOOK -> song.copy(songbook = value.trim().trim('/'))
        AUTHOR -> song.copy(author = value.trim())
        COMPOSER -> song.copy(composer = value.trim())
        TUNE -> song.copy(tune = value.trim())
        CCLI -> song.copy(ccliNumber = value.trim())
    }
}

/**
 * The edits made in a grid but not yet written.
 *
 * Nothing here touches the disk. The grid holds what a person has typed until they press Save, so a
 * mistyped number or a wrong songbook costs a Revert rather than a file moved somewhere they then
 * have to find — and so a hundred small edits are one save rather than a hundred.
 */
class SongEdits(loaded: List<SongItem>) {

    /** What each song looked like when it was read, keyed by the file that identifies it. */
    private val original: MutableMap<String, SongItem> =
        loaded.associateBy { it.sourceFile }.toMutableMap()

    private val current: MutableMap<String, SongItem> =
        loaded.associateBy { it.sourceFile }.toMutableMap()

    /** Every song, edits included, in the order they were loaded. */
    val songs: List<SongItem> get() = order.mapNotNull { current[it] }

    private val order: MutableList<String> = loaded.map { it.sourceFile }.toMutableList()

    /** The songs whose fields differ from the file they came from. */
    val changed: List<SongItem> get() = songs.filter { it != original[it.sourceFile] }

    val isDirty: Boolean get() = changed.isNotEmpty()

    fun snapshot(): Map<String, SongItem> = original.toMap()

    /** [field] of the song in [file] set to [value], as one edit. */
    fun edit(sourceFile: String, field: SongField, value: String) {
        val song = current[sourceFile] ?: return
        current[sourceFile] = field.set(song, value)
    }

    /** The lyrics of the song in [sourceFile], which a grid does not show but an editor changes. */
    fun editLyrics(sourceFile: String, lyrics: List<String>, secondaryLyrics: List<String>) {
        val song = current[sourceFile] ?: return
        current[sourceFile] = song.copy(lyrics = lyrics).withTranslation(0) { it.copy(lyrics = secondaryLyrics) }
    }

    /**
     * [fields] applied to every song in [files], as a batch edit.
     *
     * A field named here is overwritten on all of them, including with a blank — clearing the
     * composer on a selection is a thing people do deliberately, and refusing it would mean editing
     * each song by hand to do it.
     */
    fun editAll(sourceFiles: Collection<String>, fields: Map<SongField, String>) {
        for (file in sourceFiles) {
            for ((field, value) in fields) edit(file, field, value)
        }
    }

    /** Puts a song that was written outside the grid — a duplicate, a new song — into the list. */
    fun add(song: SongItem) {
        original[song.sourceFile] = song
        current[song.sourceFile] = song
        order.add(song.sourceFile)
    }

    /** Forgets the songs in [files]: their files are gone. */
    fun remove(sourceFiles: Collection<String>) {
        for (file in sourceFiles) {
            original.remove(file)
            current.remove(file)
            order.remove(file)
        }
    }

    /** Throws every pending edit away, leaving what was read from disk. */
    fun revert() {
        current.clear()
        current.putAll(original)
    }

    /** Marks what is on screen as what is on disk, which is what a successful save means. */
    fun markSaved() {
        original.clear()
        original.putAll(current)
    }

    /**
     * A copy of [song] under a title that says it is one.
     *
     * The number is dropped rather than shared: two songs in one book answering to number 42 is a
     * worse starting point than a song with no number, which the grid shows as blank to fill in.
     */
    fun copyOf(song: SongItem, titleSuffix: String): SongItem =
        song.copy(number = "", title = "${song.title} $titleSuffix")

    /** A song with nothing in it, to be filled in through the grid, filed under [songbook]. */
    fun blank(root: File, songbook: String, title: String): SongItem {
        val blank = SongItem(number = "", title = title, songbook = songbook)
        return blank.copy(sourceFile = File(root, SongFileName.relativePath(blank)).absolutePath)
    }
}
