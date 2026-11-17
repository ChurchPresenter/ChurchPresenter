package org.churchpresenter.bible

/**
 * One verse row whose id is not `B` + three digits + `C` + three digits + `V` + three digits.
 *
 * [Bible] cannot read such a row — its verse pattern needs exactly three digits — so the verse is
 * missing from the module rather than shown wrongly. `B047C010V0050` for 2 Corinthians 10:5 is the
 * shape this was written for: a stray zero on the end of the verse.
 *
 * @property lineNumber the row's line in the file, counting from 1.
 * @property verseId the id as the file spells it.
 * @property book the module's own book number, as the row's display columns give it.
 * @property chapter the display chapter.
 * @property verse the display verse, which is what the operator would look for.
 */
data class SpbRowIssue(
    val lineNumber: Int,
    val verseId: String,
    val book: Int,
    val chapter: Int,
    val verse: Int,
)

/**
 * What a scan of one module's verse rows found.
 *
 * @property total how many rows have a malformed id.
 * @property examples the first [MAX_EXAMPLES] of them, enough to list for the operator.
 */
data class SpbRowReport(val total: Int, val examples: List<SpbRowIssue>) {
    val isClean: Boolean get() = total == 0

    companion object {
        val CLEAN = SpbRowReport(0, emptyList())
    }
}

/** A verse row: the id, then the display book, chapter and verse. The text after it is not read. */
private val SPB_ROW_REGEX = Regex("""^B(\d+)C(\d+)V(\d+)\s+(\d+)\s+(\d+)\s+(\d+)(?:\s|$)""")

private const val ID_DIGITS = 3
private const val MAX_EXAMPLES = 50
private const val GROUP_ID_BOOK = 1
private const val GROUP_ID_VERSE = 3
private const val GROUP_BOOK = 4
private const val GROUP_CHAPTER = 5
private const val GROUP_VERSE = 6

/**
 * Looks at a module's lines one at a time, and keeps the count and the first few malformed ids.
 *
 * Only a row with an id that has the right shape of *everything but its digit counts* is
 * considered: the older format's bare code lines and its wrapped text carry no display columns and
 * are none of this check's business.
 */
internal class SpbRowChecker {
    private var lineNumber = 0
    private var total = 0
    private val examples = mutableListOf<SpbRowIssue>()

    fun check(line: String) {
        lineNumber++
        val m = SPB_ROW_REGEX.find(line) ?: return
        val idGroups = (GROUP_ID_BOOK..GROUP_ID_VERSE).map { m.groupValues[it] }
        if (idGroups.all { it.length == ID_DIGITS }) return
        total++
        if (examples.size < MAX_EXAMPLES) {
            examples += SpbRowIssue(
                lineNumber = lineNumber,
                verseId = line.substringBefore(' ').substringBefore('\t'),
                book = m.groupValues[GROUP_BOOK].toIntOrNull() ?: 0,
                chapter = m.groupValues[GROUP_CHAPTER].toIntOrNull() ?: 0,
                verse = m.groupValues[GROUP_VERSE].toIntOrNull() ?: 0,
            )
        }
    }

    fun report(): SpbRowReport = if (total == 0) SpbRowReport.CLEAN else SpbRowReport(total, examples.toList())
}

/** The malformed verse ids in [lines], for a caller that has the file's text and not a loaded [Bible]. */
fun checkSpbRows(lines: Sequence<String>): SpbRowReport {
    val checker = SpbRowChecker()
    lines.forEach(checker::check)
    return checker.report()
}
