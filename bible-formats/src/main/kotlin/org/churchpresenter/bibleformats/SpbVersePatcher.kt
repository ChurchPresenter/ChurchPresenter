package org.churchpresenter.bibleformats

import org.churchpresenter.bible.checkSpbRows
import java.io.File

object SpbVersePatcher {

    /** A verse row is `id, book, chapter, verse, text` -- five tab-separated columns. */
    private const val VERSE_COLUMNS = 5
    private const val COLUMN_BOOK = 1
    private const val COLUMN_CHAPTER = 2
    private const val COLUMN_VERSE = 3
    private const val COLUMN_TEXT = 4
    private const val BACKUP_SUFFIX = ".bak"

    /** A verse id with one digit too many on the end, always a zero: `B047C010V0050`. */
    private val STRAY_ZERO_ID = Regex("""B(\d{3})C(\d{3})V(\d{3})0""")
    private const val MAX_VERSE_ID = 999
    private const val VERSE_ID_WIDTH = 3

    /**
     * Applies all known corrections to an SPB file in-place:
     *   0. Repairs verse IDs with a stray trailing zero (`V0050` for verse 5), see [repairStrayZeroIds].
     *   1. Merges consecutive verse lines that share the same ID (split superscriptions).
     *   2. Fixes wrong verse IDs listed in VersePatches.ID_CORRECTIONS.
     *   3. Fixes truncated verse texts listed in VersePatches.PATCHES.
     *   4. Inserts verses missing entirely, listed in VersePatches.MISSING_VERSES.
     *
     * When anything changes, the file as it was is first copied beside it as `<name>.spb.bak`. An
     * existing `.bak` is kept, so running this twice never overwrites the original.
     *
     * Returns the total number of changes made.
     */
    fun applyPatches(spbFile: File): Int {
        val rawLines = spbFile.readText(Charsets.UTF_8).split("\n")

        // Split into header lines (up to and including "-----") and verse lines.
        val separatorIndex = rawLines.indexOfFirst { it.trimEnd() == "-----" }
        val headerLines = if (separatorIndex >= 0) rawLines.subList(0, separatorIndex + 1) else rawLines
        val verseLines = if (separatorIndex >= 0) rawLines.subList(separatorIndex + 1, rawLines.size) else emptyList()

        var patchCount = 0
        val repaired = repairStrayZeroIds(verseLines) { patchCount++ }
        val deduped = mergeSplitVerses(repaired) { patchCount++ }
        val idFixed = correctVerseIds(deduped) { patchCount++ }
        val textFixed = correctVerseTexts(idFixed) { patchCount++ }.toMutableList()
        insertMissingVerses(textFixed) { patchCount++ }

        if (patchCount > 0) {
            val backup = File(spbFile.path + BACKUP_SUFFIX)
            if (!backup.exists()) spbFile.copyTo(backup)
            spbFile.writeText((headerLines + textFixed).joinToString("\n"), Charsets.UTF_8)
        }
        return patchCount
    }

    /**
     * Pass 0: a verse id written with a stray zero on the end — `B047C010V0050`, verse 5 — becomes
     * the three-digit id it was meant to be.
     *
     * The app cannot read such a row at all, so the verse is simply missing. The repair is the
     * obvious one, with one wrinkle: in the file this was written for, the row *before* it,
     * a second half of verse 4, had been given the id `V005` that the corrected row now needs.
     * Two rows cannot own one id, so when the id is already taken the older row is only moved if it
     * is plainly a continuation — the row before it shows the same display verse — and takes that
     * row's id, which the merge pass then joins. Anything else is left alone, and the row stays
     * as it was: a repair that has to guess is not made.
     */
    private fun repairStrayZeroIds(lines: List<String>, patched: () -> Unit): List<String> {
        val out = lines.toMutableList()
        val rowByVerseId = HashMap<String, Int>()
        for (i in out.indices) {
            val parts = out[i].split("\t", limit = VERSE_COLUMNS)
            val isRow = parts.size >= VERSE_COLUMNS
            val fixed = if (isRow) repairedId(parts) else null
            val owner = fixed?.let { rowByVerseId[it] }
            when {
                !isRow -> Unit
                fixed == null -> rowByVerseId[parts[0]] = i
                owner == null || freeVerseId(out, owner, parts.display, patched) -> {
                    out[i] = fixed + out[i].removePrefix(parts[0])
                    rowByVerseId[fixed] = i
                    patched()
                }
            }
        }
        return out
    }

    /**
     * The id a row with a stray zero was meant to have, or null when its id is fine.
     *
     * Dropping the zero is not always right: `B004C021V0140` is displayed as verse 15, and `V014` is
     * the real verse 14. When the row's display book and chapter are its id's own, the display verse
     * is what the id should say; otherwise (a module whose numbering differs from its codes) the
     * id minus its zero is the best there is.
     */
    internal fun repairedId(parts: List<String>): String? {
        val m = STRAY_ZERO_ID.matchEntire(parts[0]) ?: return null
        val (book, chapter, verse) = m.destructured
        val displayVerse = parts[COLUMN_VERSE].toIntOrNull()
        val sameNumbering = parts[COLUMN_BOOK].toIntOrNull() == book.toInt() &&
            parts[COLUMN_CHAPTER].toIntOrNull() == chapter.toInt()
        val fromDisplay = displayVerse?.takeIf { sameNumbering && it in 1..MAX_VERSE_ID }
        val verseDigits = fromDisplay?.toString()?.padStart(VERSE_ID_WIDTH, '0') ?: verse
        return "B${book}C${chapter}V$verseDigits"
    }

    /**
     * Makes the id of row [owner] available. True when it already was (the same verse written
     * twice) or now is; false when the row is not a continuation, so nothing was changed.
     */
    private fun freeVerseId(
        lines: MutableList<String>,
        owner: Int,
        wanted: List<String>,
        patched: () -> Unit,
    ): Boolean {
        val ownerParts = lines[owner].split("\t", limit = VERSE_COLUMNS)
        if (ownerParts.display == wanted) return true
        val before = lines.getOrNull(lines.subList(0, owner).indexOfLast { it.isNotBlank() })
        val continuedFrom = before?.split("\t", limit = VERSE_COLUMNS)?.takeIf {
            it.size >= VERSE_COLUMNS && it.display == ownerParts.display
        }
        if (continuedFrom != null) {
            lines[owner] = continuedFrom[0] + lines[owner].removePrefix(ownerParts[0])
            patched()
        }
        return continuedFrom != null
    }

    /** The display book, chapter and verse columns of a row's parts. */
    private val List<String>.display: List<String> get() = subList(COLUMN_BOOK, COLUMN_TEXT)

    /**
     * Pass 1: merges consecutive rows that share a verse ID.
     *
     * A superscription split off from its verse arrives as two rows with the same ID, and the app
     * shows one row per verse — so the second is appended to the first rather than shown alone.
     */
    private fun mergeSplitVerses(verseLines: List<String>, patched: () -> Unit): List<String> {
        val deduped = mutableListOf<String>()
        for (line in verseLines) {
            val id = line.substringBefore('\t')
            val previous = deduped.lastOrNull { it.isNotBlank() }
            val parts = line.split("\t", limit = VERSE_COLUMNS)
            // One verse split over two rows repeats its displayed reference too. Two rows that only
            // share a code are two verses -- a two-verse psalm title is coded verse 0 twice -- and stay.
            val mergeable = line.isNotBlank() && id.startsWith("B") && parts.size >= VERSE_COLUMNS &&
                previous?.split("\t", limit = VERSE_COLUMNS)?.take(COLUMN_TEXT) == parts.take(COLUMN_TEXT)
            if (!mergeable) {
                deduped.add(line)
                continue
            }
            val previousIndex = deduped.indexOfLast { it.isNotBlank() }
            val previousParts = deduped[previousIndex].split("\t", limit = VERSE_COLUMNS)
            deduped[previousIndex] = previousParts.take(COLUMN_TEXT).joinToString("\t") +
                "\t${previousParts[COLUMN_TEXT].trimEnd()} ${parts[COLUMN_TEXT]}"
            patched()
        }
        return deduped
    }

    /** Pass 2: rewrites the verse IDs known to be corrupted, leaving the rest of the row alone. */
    private fun correctVerseIds(lines: List<String>, patched: () -> Unit): List<String> = lines.map { line ->
        if (line.isBlank()) return@map line
        val id = line.substringBefore('\t')
        val corrected = VersePatches.ID_CORRECTIONS[id] ?: return@map line
        patched()
        corrected + line.removePrefix(id)
    }

    /** Pass 3: replaces the verse texts known to be truncated or mis-worded. */
    private fun correctVerseTexts(lines: List<String>, patched: () -> Unit): List<String> = lines.map { line ->
        val parts = line.split("\t", limit = VERSE_COLUMNS)
        if (line.isBlank() || parts.size < VERSE_COLUMNS) return@map line
        val bookNum = parts[COLUMN_BOOK].toIntOrNull() ?: return@map line
        val chapNum = parts[COLUMN_CHAPTER].toIntOrNull() ?: return@map line
        val versNum = parts[COLUMN_VERSE].toIntOrNull() ?: return@map line
        val patch = VersePatches.PATCHES[Triple(bookNum, chapNum, versNum)] ?: return@map line

        val currentText = parts[COLUMN_TEXT].trimEnd('\r')
        if (!patch.applies(currentText)) return@map line
        patched()
        parts.take(COLUMN_TEXT).joinToString("\t") + "\t${patch.correctedText}"
    }

    /**
     * Whether this patch is the one for [currentText].
     *
     * A patch that names the wording it replaces only applies to that wording; one that completes a
     * truncated verse only applies to text the corrected version starts with. Everything else is
     * another translation of the same verse, and completing it from this one would be a
     * mistranslation.
     */
    private fun VersePatch.applies(currentText: String): Boolean = when {
        matchText != null -> currentText == matchText
        minimumPrefixLength > 0 && currentText.length < minimumPrefixLength -> false
        currentText == correctedText -> false
        else -> correctedText.startsWith(currentText)
    }

    /** Pass 4: inserts the verses a file leaves out entirely, after the verse they follow. */
    private fun insertMissingVerses(lines: MutableList<String>, patched: () -> Unit) {
        for (missing in VersePatches.MISSING_VERSES) {
            val alreadyPresent = lines.any { it.substringBefore('\t') == missing.verseId }
            val insertAfter = lines.indexOfLast { line -> line.follows(missing) }
            if (alreadyPresent || insertAfter < 0) continue
            lines.add(
                insertAfter + 1,
                listOf(
                    missing.verseId, missing.bookNum, missing.displayChap, missing.displayVers, missing.verseText,
                ).joinToString("\t"),
            )
            patched()
        }
    }

    /** Whether this row is the verse [missing] is inserted after. */
    private fun String.follows(missing: MissingVersePatch): Boolean {
        val parts = split("\t", limit = VERSE_COLUMNS)
        return isNotBlank() && parts.size >= COLUMN_TEXT &&
            parts[COLUMN_BOOK].toIntOrNull() == missing.bookNum &&
            parts[COLUMN_CHAPTER].toIntOrNull() == missing.displayChap &&
            parts[COLUMN_VERSE].toIntOrNull() == missing.insertAfterDisplayVers
    }
}

/**
 * How many verse rows of [spbFile] have an id the app cannot read: none in a sound file, and after
 * [SpbVersePatcher.applyPatches] the ones it could not repair without guessing.
 */
fun malformedSpbIdCount(spbFile: File): Int = spbFile.useLines(Charsets.UTF_8) { checkSpbRows(it).total }
