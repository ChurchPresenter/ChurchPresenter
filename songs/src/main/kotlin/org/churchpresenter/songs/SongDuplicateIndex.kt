package org.churchpresenter.songs

import org.churchpresenter.core.models.songs.SongItem

/**
 * The song library by number, title and song book, built once per list so that the song editor's
 * duplicate warning looks up a key on each keystroke instead of comparing every song in the library.
 *
 * Titles and song books are matched ignoring case, exactly as [String.equals] with `ignoreCase`
 * does: the key folds each character the same way, and the few songs under a key are then checked
 * with that very comparison.
 */
internal class SongDuplicateIndex(songs: List<SongItem>) {

    private val byKey: Map<Key, List<SongItem>> = songs.groupBy { Key.of(it.number, it.title, it.songbook) }

    /**
     * Whether a song other than the one at [sourceFile] already has this [number], [title] and
     * [songbook] -- a blank song book being the top of the songs folder, which is a place like any
     * other. A blank title is never a duplicate.
     */
    fun hasDuplicate(number: String, title: String, songbook: String, sourceFile: String): Boolean {
        if (title.isBlank()) return false
        return byKey[Key.of(number, title, songbook)].orEmpty().any {
            it.number == number &&
                it.title.equals(title, ignoreCase = true) &&
                it.songbook.equals(songbook, ignoreCase = true) &&
                it.sourceFile != sourceFile
        }
    }

    private data class Key(val number: String, val title: String, val songbook: String) {
        companion object {
            fun of(number: String, title: String, songbook: String) = Key(number, fold(title), fold(songbook))

            /** Each character upper- then lower-cased: equal for any two strings `equals(ignoreCase)` matches. */
            private fun fold(text: String): String = buildString(text.length) {
                text.codePoints().forEach { appendCodePoint(Character.toLowerCase(Character.toUpperCase(it))) }
            }
        }
    }
}
