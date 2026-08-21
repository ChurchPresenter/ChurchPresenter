package org.churchpresenter.app.churchpresenter.models.songs

/**
 * What a song's file is called, and where it sits.
 *
 * Two of a song's fields are not inside its file: the number is the numeric prefix of the file name
 * and the songbook is the folder holding it. So editing either is a move, and both the app and any
 * screen that edits a library have to agree on the name that comes out — which is what this is.
 */
object SongFileName {

    /** `0001 - Amazing Grace.song`, or just the title when the song has no number. */
    fun of(song: SongItem): String = of(song.number, song.title)

    fun of(number: String, title: String): String {
        val safeTitle = sanitize(title).ifBlank { "Untitled" }
        val prefix = if (number.isBlank()) "" else "${sanitize(number)} - "
        return "$prefix$safeTitle.$SONG_EXTENSION"
    }

    /** Where the song belongs relative to the library root, songbook folders included. */
    fun relativePath(song: SongItem): String =
        if (song.songbook.isBlank()) of(song) else "${song.songbook}/${of(song)}"

    /**
     * [name] with the characters a file name cannot hold replaced by a space.
     *
     * A title may be anything a person can type — `AC/DC`, `Who? Me!`, a colon — and one of those
     * in a path is either an error or, on Windows, a file somewhere nobody meant. The title itself
     * keeps its punctuation; only the name on disk loses it.
     */
    fun sanitize(name: String): String =
        name.map { if (it in FORBIDDEN || it.isISOControl()) ' ' else it }
            .joinToString("")
            .replace(WHITESPACE, " ")
            .trim()
            .trimEnd('.')

    private val FORBIDDEN = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
    private val WHITESPACE = Regex("\\s+")
}
