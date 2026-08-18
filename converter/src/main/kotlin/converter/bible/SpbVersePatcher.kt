package converter.bible

import java.io.File

object SpbVersePatcher {
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
        val originalText = spbFile.readText(Charsets.UTF_8)
        val rawLines = originalText.split("\n")
        var patchCount = 0

        // Split into header lines (up to and including "-----") and verse lines.
        val separatorIndex = rawLines.indexOfFirst { it.trimEnd() == "-----" }
        val headerLines = if (separatorIndex >= 0) rawLines.subList(0, separatorIndex + 1) else rawLines
        val verseLines = if (separatorIndex >= 0) rawLines.subList(separatorIndex + 1, rawLines.size) else emptyList()

        // --- Pass 1: merge consecutive duplicate verse IDs ---
        val deduped = mutableListOf<String>()
        for (line in verseLines) {
            if (line.isBlank()) { deduped.add(line); continue }
            val id = line.substringBefore('\t')
            if (!id.startsWith("B")) { deduped.add(line); continue }
            val prevId = deduped.lastOrNull { it.isNotBlank() }?.substringBefore('\t')
            if (prevId == id) {
                val parts = line.split("\t", limit = 5)
                if (parts.size < 5) { deduped.add(line); continue }
                val prevIdx = deduped.indexOfLast { it.isNotBlank() }
                val prevParts = deduped[prevIdx].split("\t", limit = 5)
                deduped[prevIdx] = "${prevParts[0]}\t${prevParts[1]}\t${prevParts[2]}\t${prevParts[3]}\t${prevParts[4].trimEnd()} ${parts[4]}"
                patchCount++
            } else {
                deduped.add(line)
            }
        }

        // --- Pass 2: fix wrong verse IDs ---
        val idFixed = deduped.map { line ->
            if (line.isBlank()) return@map line
            val id = line.substringBefore('\t')
            val corrected = VersePatches.ID_CORRECTIONS[id] ?: return@map line
            patchCount++
            corrected + line.removePrefix(id)
        }.toMutableList()

        // --- Pass 3: fix truncated verse texts ---
        val textFixed = idFixed.map { line ->
            if (line.isBlank()) return@map line
            val parts = line.split("\t", limit = 5)
            if (parts.size < 5) return@map line
            val bookNum = parts[1].toIntOrNull() ?: return@map line
            val chapNum = parts[2].toIntOrNull() ?: return@map line
            val versNum = parts[3].toIntOrNull() ?: return@map line
            val currentText = parts[4].trimEnd('\r')
            val patch = VersePatches.PATCHES[Triple(bookNum, chapNum, versNum)] ?: return@map line
            if (patch.matchText != null) {
                if (currentText != patch.matchText) return@map line
                patchCount++
                return@map "${parts[0]}\t${parts[1]}\t${parts[2]}\t${parts[3]}\t${patch.correctedText}"
            }
            if (patch.minimumPrefixLength > 0 && currentText.length < patch.minimumPrefixLength) return@map line
            if (currentText == patch.correctedText) return@map line
            if (!patch.correctedText.startsWith(currentText)) return@map line
            patchCount++
            "${parts[0]}\t${parts[1]}\t${parts[2]}\t${parts[3]}\t${patch.correctedText}"
        }.toMutableList()

        // --- Pass 4: insert missing verses ---
        for (missing in VersePatches.MISSING_VERSES) {
            val insertAfterIdx = textFixed.indexOfLast { line ->
                if (line.isBlank()) return@indexOfLast false
                val parts = line.split("\t", limit = 5)
                if (parts.size < 4) return@indexOfLast false
                parts[1].toIntOrNull() == missing.bookNum &&
                parts[2].toIntOrNull() == missing.displayChap &&
                parts[3].toIntOrNull() == missing.insertAfterDisplayVers
            }
            if (insertAfterIdx < 0) continue
            // Check it's not already present
            val alreadyPresent = textFixed.any { line ->
                line.substringBefore('\t') == missing.verseId
            }
            if (alreadyPresent) continue
            val newLine = "${missing.verseId}\t${missing.bookNum}\t${missing.displayChap}\t${missing.displayVers}\t${missing.verseText}"
            textFixed.add(insertAfterIdx + 1, newLine)
            patchCount++
        }

        if (patchCount > 0) {
            val result = (headerLines + textFixed).joinToString("\n")
            spbFile.writeText(result, Charsets.UTF_8)
        }
        return patchCount
    }
}
