package org.churchpresenter.converter.library

import java.io.File

/**
 * Setting a song's alias -- what the output shows where its number goes, "Ch 25" -- across a whole
 * folder at once, for a song book whose numbers need a prefix to tell it from another book.
 *
 * The `alias:` line is edited in the file's text rather than by parsing and rewriting the song, so a
 * bulk pass over someone's library changes that one line and nothing else in any file.
 */
object SongAliases {

    /** What [pattern] puts in place of the song's number. */
    const val NUMBER_PLACEHOLDER = "{n}"

    private const val HEADER_FENCE = "---"
    private const val ALIAS_KEY = "alias"
    private val leadingNumber = Regex("""^(\d+)\s*-\s*""")

    /**
     * One `.song` file and the alias the pass would give it: [newAlias] is null when the file name
     * carries no number to build one from, and the file is left alone.
     */
    data class Entry(val file: File, val number: String, val oldAlias: String, val newAlias: String?) {
        val changes: Boolean get() = newAlias != null && newAlias != oldAlias
    }

    /**
     * The alias [pattern] gives song [number]: `{n}` replaced by the number, or -- when the pattern
     * has no `{n}` -- the pattern followed by a space and the number, so typing just "Ch" works.
     * A blank pattern clears the alias.
     */
    fun aliasFor(pattern: String, number: String): String {
        val trimmed = pattern.trim()
        return when {
            trimmed.isEmpty() -> ""
            NUMBER_PLACEHOLDER in trimmed -> trimmed.replace(NUMBER_PLACEHOLDER, number)
            else -> "$trimmed $number"
        }
    }

    /** The number in a `0025 - Title.song` name without its leading zeros, or "" when there is none. */
    fun numberOf(file: File): String =
        leadingNumber.find(file.nameWithoutExtension)?.groupValues?.get(1)?.trimStart('0')?.ifEmpty { "0" }.orEmpty()

    /** Every `.song` file under [directory], in path order, with what [pattern] would make its alias. */
    fun preview(directory: File, pattern: String, read: (File) -> String): List<Entry> =
        directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("song", ignoreCase = true) }
            .sortedBy { it.absolutePath }
            .map { file ->
                val number = numberOf(file)
                Entry(
                    file = file,
                    number = number,
                    oldAlias = aliasIn(read(file)),
                    newAlias = number.takeIf { it.isNotEmpty() }?.let { aliasFor(pattern, it) },
                )
            }
            .toList()

    /** The `alias:` value in a song's header, or "" when it has none. */
    fun aliasIn(content: String): String {
        val lines = content.lines()
        val (start, end) = headerBounds(lines) ?: return ""
        return lines.subList(start + 1, end).firstNotNullOfOrNull(::aliasValueOf).orEmpty()
    }

    /**
     * [content] with its `alias:` line set to [alias], or removed when [alias] is blank. A file with
     * no header gets one holding only the alias; every other line, and the file's line endings, stay
     * exactly as they were.
     */
    fun withAlias(content: String, alias: String): String {
        val newline = if ("\r\n" in content) "\r\n" else "\n"
        val lines = content.split(newline).toMutableList()
        val value = alias.trim()
        val bounds = headerBounds(lines)
        if (bounds == null) {
            if (value.isEmpty()) return content
            return listOf(HEADER_FENCE, "$ALIAS_KEY: $value", HEADER_FENCE, "").joinToString(newline) +
                newline + content
        }
        val (start, end) = bounds
        val kept = lines.subList(start + 1, end).filter { aliasValueOf(it) == null }
        val header = if (value.isEmpty()) kept else kept + "$ALIAS_KEY: $value"
        val rebuilt = lines.subList(0, start + 1) + header + lines.subList(end, lines.size)
        return rebuilt.joinToString(newline)
    }

    /** The opening and closing `---` of the header, found the way the app's song parser finds them. */
    private fun headerBounds(lines: List<String>): Pair<Int, Int>? {
        val start = lines.indexOfFirst { it.trim() == HEADER_FENCE }.takeIf { it >= 0 } ?: return null
        val close = lines.subList(start + 1, lines.size).indexOfFirst { it.trim() == HEADER_FENCE }
            .takeIf { it >= 0 } ?: return null
        return start to start + 1 + close
    }

    private fun aliasValueOf(line: String): String? {
        val colon = line.indexOf(':')
        if (colon <= 0 || !line.substring(0, colon).trim().equals(ALIAS_KEY, ignoreCase = true)) return null
        return line.substring(colon + 1).trim()
    }
}
