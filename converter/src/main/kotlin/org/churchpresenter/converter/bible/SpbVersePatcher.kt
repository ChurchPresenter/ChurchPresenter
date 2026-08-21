package org.churchpresenter.converter.bible

import java.io.File

object SpbVersePatcher {

    /** A verse row is `id, book, chapter, verse, text` -- five tab-separated columns. */
    private const val VERSE_COLUMNS = 5
    private const val COLUMN_BOOK = 1
    private const val COLUMN_CHAPTER = 2
    private const val COLUMN_VERSE = 3
    private const val COLUMN_TEXT = 4
    /**
     * Applies all known corrections to an SPB file in-place:
     *   1. Merges consecutive verse lines that share the same ID (split superscriptions).
     *   2. Fixes wrong verse IDs listed in VersePatches.ID_CORRECTIONS.
     *   3. Fixes truncated verse texts listed in VersePatches.PATCHES.
     *   4. Inserts verses missing entirely, listed in VersePatches.MISSING_VERSES.
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
        val deduped = mergeSplitVerses(verseLines) { patchCount++ }
        val idFixed = correctVerseIds(deduped) { patchCount++ }
        val textFixed = correctVerseTexts(idFixed) { patchCount++ }.toMutableList()
        insertMissingVerses(textFixed) { patchCount++ }

        if (patchCount > 0) {
            spbFile.writeText((headerLines + textFixed).joinToString("\n"), Charsets.UTF_8)
        }
        return patchCount
    }

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
            val previousId = deduped.lastOrNull { it.isNotBlank() }?.substringBefore('\t')
            val parts = line.split("\t", limit = VERSE_COLUMNS)
            val mergeable = line.isNotBlank() && id.startsWith("B") &&
                previousId == id && parts.size >= VERSE_COLUMNS
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
