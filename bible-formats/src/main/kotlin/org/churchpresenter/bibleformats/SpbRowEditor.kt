package org.churchpresenter.bibleformats

import java.io.File

/**
 * One verse row of an `.spb` as the Bible verse fixer shows it.
 *
 * @property lineNumber the row's line in the file, counting from 1; edits are keyed by it.
 * @property id the id as the file spells it.
 */
data class SpbRowView(
    val lineNumber: Int,
    val id: String,
    val book: String,
    val chapter: String,
    val verse: String,
    val text: String,
)

/** What a person changed on one row. A null field is left as the file has it. */
data class SpbRowEdit(val id: String? = null, val text: String? = null)

/**
 * Everything a person changed in the editor, keyed by line number in the file as it was read.
 *
 * @property edits rows whose id or text changed.
 * @property deleted rows to remove.
 * @property inserted new rows, by the line they follow; 0 puts them before the first verse row.
 *   Their [SpbRowView.lineNumber] is not used.
 */
data class SpbChanges(
    val edits: Map<Int, SpbRowEdit> = emptyMap(),
    val deleted: Set<Int> = emptySet(),
    val inserted: Map<Int, List<SpbRowView>> = emptyMap(),
) {
    val isEmpty: Boolean get() = edits.isEmpty() && deleted.isEmpty() && inserted.isEmpty()
}

/**
 * Reads every verse row of an `.spb` and rewrites it with the changes a person chose.
 *
 * The Fix Verses pass in [SpbVersePatcher] repairs what it can prove; this is for the rest, where a
 * person reads the verses around a damaged one and decides. It changes, removes or adds verse rows
 * only — the header, the book list and any other line stay as they were.
 */
object SpbRowEditor {
    private const val COLUMNS = 5
    private const val COLUMN_BOOK = 1
    private const val COLUMN_CHAPTER = 2
    private const val COLUMN_VERSE = 3
    private const val COLUMN_TEXT = 4
    private val ANY_ID = Regex("""B(\d+)C(\d+)V(\d+)""")
    private val BOOK_LINE = Regex("""(\d+)\s+(.+?)\s+(\d+)""")
    private val VALID_ID = Regex("""B\d{3}C\d{3}V\d{3}""")

    /** True for an id the app can read: `B`, `C` and `V` each followed by exactly three digits. */
    fun isValidId(id: String): Boolean = VALID_ID.matches(id)

    /** Every verse row of [spbFile], in file order; header and wrapped lines are not rows. */
    fun readRows(spbFile: File): List<SpbRowView> =
        spbFile.readLines(Charsets.UTF_8).mapIndexedNotNull { index, line -> parse(index + 1, line) }

    /** The book names the file's own book list gives, by the book number its rows display. */
    fun readBookNames(spbFile: File): Map<Int, String> = spbFile.readLines(Charsets.UTF_8)
        .mapNotNull { BOOK_LINE.matchEntire(it) }
        .associate { it.groupValues[1].toInt() to it.groupValues[2] }

    /** The id the automatic repair would give [row], or null when it would be a guess. */
    fun suggestedId(row: SpbRowView): String? =
        SpbVersePatcher.repairedId(listOf(row.id, row.book, row.chapter, row.verse, row.text))

    /**
     * Writes [changes] into [spbFile], keeping a `.bak` copy of the original beside it the first
     * time. Returns how many rows changed, were removed or were added. An id that is not [isValidId],
     * or a line that is not a verse row, is skipped; tabs and line breaks in a text become spaces,
     * since either would split the row.
     */
    fun applyChanges(spbFile: File, changes: SpbChanges): Int {
        val lines = spbFile.readText(Charsets.UTF_8).split("\n")
        val firstVerse = lines.indexOfFirst { parse(0, it) != null }
        val lineEnd = if (firstVerse >= 0 && lines[firstVerse].endsWith("\r")) "\r" else ""
        val out = ArrayList<String>(lines.size)
        var count = 0
        fun addInserted(after: Int) = changes.inserted[after].orEmpty().forEach { row ->
            if (isValidId(row.id)) {
                out += listOf(row.id, row.book, row.chapter, row.verse, flatten(row.text) + lineEnd).joinToString("\t")
                count++
            }
        }
        lines.forEachIndexed { index, line ->
            val lineNumber = index + 1
            if (index == firstVerse) addInserted(0)
            val isVerse = parse(lineNumber, line) != null
            val rewritten = if (isVerse) changes.edits[lineNumber]?.let { rewrite(line, it) } else null
            when {
                isVerse && lineNumber in changes.deleted -> count++
                rewritten != null && rewritten != line -> { out += rewritten; count++ }
                else -> out += line
            }
            addInserted(lineNumber)
        }
        if (count > 0) {
            val backup = File(spbFile.path + ".bak")
            if (!backup.exists()) spbFile.copyTo(backup)
            spbFile.writeText(out.joinToString("\n"), Charsets.UTF_8)
        }
        return count
    }

    private fun flatten(text: String): String = text.replace(Regex("""[\t\r\n]"""), " ")

    private fun rewrite(line: String, edit: SpbRowEdit): String? {
        val parts = line.split("\t", limit = COLUMNS).toMutableList()
        if (parts.size < COLUMNS || !ANY_ID.matches(parts[0])) return null
        edit.id?.let { id -> if (isValidId(id)) parts[0] = id else return null }
        edit.text?.let { text ->
            val lineEnd = if (parts[COLUMN_TEXT].endsWith("\r")) "\r" else ""
            parts[COLUMN_TEXT] = flatten(text) + lineEnd
        }
        return parts.joinToString("\t")
    }

    private fun parse(lineNumber: Int, line: String): SpbRowView? {
        val parts = line.split("\t", limit = COLUMNS)
        if (parts.size < COLUMNS || !ANY_ID.matches(parts[0])) return null
        return SpbRowView(
            lineNumber = lineNumber,
            id = parts[0],
            book = parts[COLUMN_BOOK],
            chapter = parts[COLUMN_CHAPTER],
            verse = parts[COLUMN_VERSE],
            text = parts[COLUMN_TEXT].trimEnd('\r'),
        )
    }
}
