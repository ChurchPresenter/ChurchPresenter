package core.models.songs

/** Which column the grid is ordered by. */
enum class SortColumn { NUMBER, TITLE, SECONDARY_TITLE, SONGBOOK, AUTHOR, COMPOSER, TUNE, CCLI }

/** How the grid is filtered and ordered: what the search box, the book filter and a header say. */
data class GridView(
    val query: String = "",
    /** null shows every songbook; "" shows the songs filed directly in the library root. */
    val songbook: String? = null,
    val sortBy: SortColumn = SortColumn.SONGBOOK,
    val ascending: Boolean = true,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || songbook != null
}

/**
 * Turning the whole library into the rows on screen.
 *
 * Kept apart from the grid itself so what a person searched for and what they see can be checked
 * without a display: a search that quietly drops a song, or a sort that puts song 10 before song 2,
 * is invisible in a screenshot and obvious in a test.
 */
object SongGrid {

    /** The songs [view] asks for, in the order it asks for them. */
    fun rows(songs: List<SongItem>, view: GridView): List<SongItem> =
        sort(songs.filter { it.matches(view.query) && it.inSongbook(view.songbook) }, view)

    /**
     * Whether the song answers to [query], which is matched against the fields a person searches by.
     *
     * Every word has to match something, but not the same something: "newton grace" finds Amazing
     * Grace by John Newton, which is how a person who half-remembers a song looks for it.
     */
    private fun SongItem.matches(query: String): Boolean {
        val words = query.trim().lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        val haystack = listOf(number, title, secondaryTitle, author, composer, tune, ccliNumber, songbook)
            .joinToString(" ") { it.lowercase() }
        return words.all { it in haystack }
    }

    /** A songbook filter takes the book and everything filed under it, as the app's own list does. */
    private fun SongItem.inSongbook(selected: String?): Boolean = when (selected) {
        null -> true
        "" -> songbook.isBlank()
        else -> songbook == selected || songbook.startsWith("$selected/")
    }

    private fun sort(songs: List<SongItem>, view: GridView): List<SongItem> {
        val ordered = when (view.sortBy) {
            // Numbers sort as numbers: by string, song 10 comes before song 2.
            SortColumn.NUMBER -> songs.sortedWith(
                compareBy({ it.number.toLongOrNull() ?: Long.MAX_VALUE }, { it.number })
            )
            SortColumn.TITLE -> songs.byText { it.title }
            SortColumn.SECONDARY_TITLE -> songs.byText { it.secondaryTitle }
            SortColumn.AUTHOR -> songs.byText { it.author }
            SortColumn.COMPOSER -> songs.byText { it.composer }
            SortColumn.TUNE -> songs.byText { it.tune }
            SortColumn.CCLI -> songs.byText { it.ccliNumber }
            // Within a songbook the number is the order the book itself is in.
            SortColumn.SONGBOOK -> songs.sortedWith(
                compareBy(
                    { it.songbook.lowercase() },
                    { it.number.toLongOrNull() ?: Long.MAX_VALUE },
                    { it.title.lowercase() },
                )
            )
        }
        return if (view.ascending) ordered else ordered.reversed()
    }

    /** How many songs each songbook holds, for the counts beside the filter's entries. */
    fun countsBySongbook(songs: List<SongItem>): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        for (song in songs) {
            counts[song.songbook] = (counts[song.songbook] ?: 0) + 1
            // A song in "Kids/AM" is also one of the songs in "Kids".
            var parent = song.songbook.substringBeforeLast('/', "")
            while (parent.isNotBlank()) {
                counts[parent] = (counts[parent] ?: 0) + 1
                parent = parent.substringBeforeLast('/', "")
            }
        }
        return counts
    }

    /**
     * By [field], case ignored, with the songs that have nothing in it last.
     *
     * Blanks sort last rather than first for the same reason the number column does it: sorting by
     * composer to find the ones that need filling in puts them at one end either way, and having
     * the songs that *have* one at the top is what the sort was asked for.
     */
    private fun List<SongItem>.byText(field: (SongItem) -> String): List<SongItem> =
        sortedWith(compareBy({ field(it).isBlank() }, { field(it).lowercase() }))

    private val WHITESPACE = Regex("\\s+")
}
